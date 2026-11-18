package io.github.perruzz.stacco.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import io.github.perruzz.stacco.domain.AdjustmentKind
import io.github.perruzz.stacco.domain.BankAdjustment
import io.github.perruzz.stacco.domain.BankCalculator
import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.Engine
import io.github.perruzz.stacco.domain.Intervention
import io.github.perruzz.stacco.domain.OnCall
import io.github.perruzz.stacco.domain.Schedule
import io.github.perruzz.stacco.domain.ScheduleHistory
import io.github.perruzz.stacco.domain.ShiftTemplate
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.StampSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.max

val StaccoJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = false
}

object StaccoDataSerializer : Serializer<StaccoData> {
    override val defaultValue: StaccoData = StaccoData()

    override suspend fun readFrom(input: InputStream): StaccoData = try {
        StaccoJson.decodeFromString(StaccoData.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unable to read Stacco data", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unable to read Stacco data", e)
    }

    override suspend fun writeTo(t: StaccoData, output: OutputStream) {
        output.write(StaccoJson.encodeToString(StaccoData.serializer(), t).encodeToByteArray())
    }
}

/** Single source of truth. Every write is an atomic transformation of the whole state. */
class StaccoRepository(private val store: DataStore<StaccoData>) {

    val data: Flow<StaccoData> = store.data

    suspend fun current(): StaccoData = store.data.first()

    suspend fun update(transform: (StaccoData) -> StaccoData): StaccoData = store.updateData(transform)

    suspend fun updateDay(date: LocalDate, transform: (DayRecord) -> DayRecord) {
        update { it.withDay(date, transform) }
    }

    /** Sets (or clears) a stamp; a time earlier than the entry belongs to the next day. */
    suspend fun setStamp(date: LocalDate, slot: StampSlot, minutes: Int?) {
        updateDay(date) { day -> day.with(slot, minutes?.let { Engine.normalizeStamp(day, slot, it) }) }
    }

    /** Result of a stamp: what was recorded, where, and the day as it was before. */
    data class Stamp(val date: LocalDate, val slot: StampSlot, val minutes: Int, val previous: DayRecord)

    /**
     * Stamps the next free slot of the current working day (yesterday during a night shift).
     * [minutes] lets the caller record a slightly different time (the dial, a company-app session);
     * it is relative to the working day and defaults to "now".
     */
    suspend fun stampAt(now: LocalDateTime, forceSlot: StampSlot? = null, minutes: Int? = null): Stamp? {
        var stamp: Stamp? = null
        update { data ->
            val active = Engine.activeDay(data, now)
            val calendar = WorkCalendar.from(data)
            val record = data.days[active.date.toString()] ?: DayRecord.EMPTY
            val slot = forceSlot ?: record.nextSlot(calendar.expectsBreak(active.date)) ?: return@update data
            val value = Engine.normalizeStamp(record, slot, minutes ?: active.minutes)
            stamp = Stamp(active.date, slot, value, record)
            data.withDay(active.date) { day ->
                val base = if (day.type != null && day.type != DayType.OFFICE) day.copy(type = DayType.OFFICE) else day
                base.with(slot, value)
            }
        }
        return stamp
    }

    suspend fun stampNow(now: LocalDateTime, forceSlot: StampSlot? = null): StampSlot? = stampAt(now, forceSlot)?.slot

    suspend fun setShift(date: LocalDate, shiftId: String?) {
        updateDay(date) { it.copy(shiftId = shiftId) }
    }

    suspend fun saveShiftTemplate(template: ShiftTemplate) {
        update { data ->
            val list = data.work.shifts
            val updated = if (list.any { it.id == template.id }) list.map { if (it.id == template.id) template else it } else list + template
            data.copy(work = data.work.copy(shifts = updated))
        }
    }

    suspend fun deleteShiftTemplate(id: String) {
        update { data ->
            data.copy(
                work = data.work.copy(shifts = data.work.shifts.filterNot { it.id == id }),
                days = data.days.mapValues { (_, day) -> if (day.shiftId == id) day.copy(shiftId = null) else day }
                    .filterValues { !it.isEmpty },
            )
        }
    }

    suspend fun setDayType(date: LocalDate, type: DayType?) {
        updateDay(date) { it.copy(type = type) }
    }

    /** Writes [type] on every day of [dates]; returns those days as they were, for undo. */
    suspend fun setDayTypes(dates: Collection<LocalDate>, type: DayType?): Map<LocalDate, DayRecord?> {
        var previous: Map<LocalDate, DayRecord?> = emptyMap()
        update { data ->
            previous = dates.associateWith { data.days[it.toString()] }
            dates.fold(data) { acc, date -> acc.withDay(date) { it.copy(type = type) } }
        }
        return previous
    }

    /** Marks (or unmarks) days as on call, days off included; returns them as they were, for undo. */
    suspend fun setOnCallDays(dates: Collection<LocalDate>, on: Boolean): Map<LocalDate, DayRecord?> {
        var previous: Map<LocalDate, DayRecord?> = emptyMap()
        update { data ->
            previous = dates.associateWith { data.days[it.toString()] }
            dates.fold(data) { acc, date -> acc.withDay(date) { it.copy(onCall = on) } }
        }
        return previous
    }

    /** A started or stopped intervention timer: its day, the intervention, and the day as it was before. */
    data class InterventionChange(val date: LocalDate, val intervention: Intervention, val previous: DayRecord)

    /**
     * Starts an intervention timer now, on the on-call day it belongs to (yesterday's in the early
     * hours after an on-call night), which is marked as on call. Nothing happens if one is running.
     */
    suspend fun startIntervention(now: LocalDateTime, id: Long = System.currentTimeMillis()): InterventionChange? {
        var change: InterventionChange? = null
        update { data ->
            if (OnCall.running(data, now.toLocalDate()) != null) return@update data
            val (date, minutes) = OnCall.dutyDay(data, now)
            val intervention = Intervention(id = id, start = minutes, startSecond = now.second)
            val day = data.days[date.toString()] ?: DayRecord.EMPTY
            change = InterventionChange(date, intervention, day)
            data.withDay(date) { it.copy(onCall = true, interventions = it.interventions + intervention) }
        }
        return change
    }

    /** Stops the running timer at [now]. */
    suspend fun stopIntervention(now: LocalDateTime): InterventionChange? {
        var change: InterventionChange? = null
        update { data ->
            val (date, running) = OnCall.running(data, now.toLocalDate()) ?: return@update data
            val done = running.copy(end = max(running.start, OnCall.minutesOf(date, now)))
            change = InterventionChange(date, done, data.days[date.toString()] ?: DayRecord.EMPTY)
            data.withDay(date) { day -> day.copy(interventions = day.interventions.map { if (it.id == running.id) done else it }) }
        }
        return change
    }

    /** Adds or corrects an intervention typed in by hand: its day becomes an on-call day. */
    suspend fun saveIntervention(date: LocalDate, intervention: Intervention) {
        updateDay(date) { day ->
            val others = day.interventions.filterNot { it.id == intervention.id }
            day.copy(onCall = true, interventions = (others + intervention).sortedBy { it.start })
        }
    }

    suspend fun deleteIntervention(date: LocalDate, id: Long) {
        updateDay(date) { day -> day.copy(interventions = day.interventions.filterNot { it.id == id }) }
    }

    suspend fun restoreDays(previous: Map<LocalDate, DayRecord?>) {
        update { data -> previous.entries.fold(data) { acc, (date, record) -> acc.withDay(date) { record ?: DayRecord.EMPTY } } }
    }

    /**
     * A new contract (hours, weekdays, fixed or shifts) from [from] on; null rewrites every date.
     * Days before keep being counted with the rules they had.
     */
    suspend fun changeSchedule(next: Schedule, from: LocalDate?) {
        update { data ->
            // With nothing recorded before the change there is no past to protect.
            val effectiveFrom = from?.takeIf { day -> data.days.keys.any { it < day.toString() } }
            val history = ScheduleHistory.change(data.scheduleHistory, data.work.schedule, next, effectiveFrom)
            data.copy(work = data.work.withSchedule(next), scheduleHistory = history)
        }
    }

    /** Makes the vacation hours available today equal to [target] minutes. */
    suspend fun setVacationAvailable(target: Int, today: LocalDate) {
        update { data ->
            val key = today.year.toString()
            val adjustment = VacationCalculator.adjustmentFor(target, data, today)
            val adjustments = if (adjustment == 0) data.vacation.adjustments - key else data.vacation.adjustments + (key to adjustment)
            data.copy(vacation = data.vacation.copy(adjustments = adjustments))
        }
    }

    suspend fun clearDay(date: LocalDate) {
        update { it.copy(days = it.days - date.toString()) }
    }

    /** Makes the balance *right now* equal to [target] minutes. */
    suspend fun setBalance(target: Int, today: LocalDate, now: Long = System.currentTimeMillis()) {
        update { data ->
            val key = today.toString()
            val others = data.adjustments.filterNot { it.kind == AdjustmentKind.SET_BALANCE && it.date == key }
            val base = data.copy(adjustments = others)
            val anchor = BankCalculator.anchorFor(target, base, today)
            base.copy(adjustments = others + BankAdjustment(now, key, AdjustmentKind.SET_BALANCE, anchor))
        }
    }

    suspend fun addAdjustment(date: LocalDate, minutes: Int, note: String, now: Long = System.currentTimeMillis()) {
        update { it.copy(adjustments = it.adjustments + BankAdjustment(now, date.toString(), AdjustmentKind.MANUAL, minutes, note.trim())) }
    }

    suspend fun deleteAdjustment(id: Long) {
        update { data -> data.copy(adjustments = data.adjustments.filterNot { it.id == id }) }
    }

    /** Wipes everything: the app goes back to the welcome flow. */
    suspend fun reset() {
        update { StaccoData() }
    }

    suspend fun replaceAll(newData: StaccoData) {
        update { newData.copy(onboardingDone = true) }
    }

    companion object {
        fun create(context: Context, scope: CoroutineScope): StaccoRepository {
            val store = DataStoreFactory.create(
                serializer = StaccoDataSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { StaccoData() },
                scope = scope,
                produceFile = { context.dataStoreFile("stacco.json") },
            )
            return StaccoRepository(store)
        }
    }
}

fun StaccoData.day(date: LocalDate): DayRecord = days[date.toString()] ?: DayRecord.EMPTY

fun StaccoData.withDay(date: LocalDate, transform: (DayRecord) -> DayRecord): StaccoData {
    val key = date.toString()
    val updated = transform(days[key] ?: DayRecord.EMPTY)
    return copy(days = if (updated.isEmpty) days - key else days + (key to updated))
}

object Backup {
    private val pretty = Json(from = StaccoJson) { prettyPrint = true }

    fun encode(data: StaccoData): String = pretty.encodeToString(StaccoData.serializer(), data)

    /**
     * A Stacco backup, or null. Any JSON object would otherwise decode (unknown keys are ignored)
     * into empty defaults and an import would wipe every day: a real backup always carries
     * "schemaVersion" together with the work settings.
     */
    fun decode(text: String): StaccoData? = runCatching {
        val root = StaccoJson.parseToJsonElement(text).jsonObject
        if ("schemaVersion" !in root || "work" !in root) return null
        StaccoJson.decodeFromJsonElement(StaccoData.serializer(), root)
    }.getOrNull()
}
