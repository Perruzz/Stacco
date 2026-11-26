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
import io.github.perruzz.stacco.domain.SchedulePeriod
import io.github.perruzz.stacco.domain.ScheduleHistory
import io.github.perruzz.stacco.domain.ShiftTemplate
import io.github.perruzz.stacco.domain.VacationCalculator
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.StampSlot
import io.github.perruzz.stacco.domain.SupportRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.File
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

/** The mark some editors put in front of a text file they save. */
private const val BOM = "\uFEFF"

/** For a damaged data file only: an unknown or missing value takes its default instead of failing. */
private val SalvageJson = Json(from = StaccoJson) { coerceInputValues = true }

/**
 * The app's data file. One that does not read as it is (a value this version does not know, a
 * damaged day…) is read part by part, keeping everything that still reads; its original bytes go
 * to [keepDamaged] first, so that nothing is thrown away for good. Only a file with nothing left
 * to read is reported as corrupt (and replaced by an empty one).
 */
class StaccoDataSerializer(private val keepDamaged: (ByteArray) -> Unit = {}) : Serializer<StaccoData> {
    override val defaultValue: StaccoData = StaccoData()

    override suspend fun readFrom(input: InputStream): StaccoData {
        val bytes = input.readBytes()
        val text = bytes.decodeToString().removePrefix(BOM)
        StoredData.read(text)?.let { return it }
        if (text.isNotBlank()) runCatching { keepDamaged(bytes) }
        return StoredData.salvage(text) ?: throw CorruptionException("Unable to read Stacco data")
    }

    override suspend fun writeTo(t: StaccoData, output: OutputStream) {
        output.write(StaccoJson.encodeToString(StaccoData.serializer(), t).encodeToByteArray())
    }
}

/** The stored JSON turned into data the app can count on (see [Sanitizer]). */
internal object StoredData {

    /** [text] decoded as it is, or null. */
    fun read(text: String): StaccoData? =
        unreadableAsNull { StaccoJson.decodeFromString(StaccoData.serializer(), text) }?.let(Sanitizer::clean)

    /** What still reads of [text]: every section, day, adjustment and contract period that decodes on its own. */
    fun salvage(text: String): StaccoData? = unreadableAsNull {
        val root = StaccoJson.parseToJsonElement(text).jsonObject
        val kept = buildMap {
            for ((key, value) in root) {
                val part = when {
                    key == "days" && value is JsonObject -> JsonObject(value.filterValues { decodes(DayRecord.serializer(), it) })
                    key == "adjustments" && value is JsonArray -> JsonArray(value.filter { decodes(BankAdjustment.serializer(), it) })
                    key == "scheduleHistory" && value is JsonArray -> JsonArray(value.filter { decodes(SchedulePeriod.serializer(), it) })
                    else -> value
                }
                if (decodes(StaccoData.serializer(), JsonObject(mapOf(key to part)))) put(key, part)
            }
        }
        Sanitizer.clean(SalvageJson.decodeFromJsonElement(StaccoData.serializer(), JsonObject(kept)))
    }

    private fun <T> decodes(serializer: KSerializer<T>, element: JsonElement): Boolean =
        unreadableAsNull { SalvageJson.decodeFromJsonElement(serializer, element) } != null

    /**
     * Null when the JSON does not decode (malformed, wrong types: all IllegalArgumentException).
     * Anything else, such as running out of memory, is no proof that the file is damaged: it goes
     * up as before, and the file stays as it is.
     */
    private inline fun <T> unreadableAsNull(decode: () -> T): T? = try {
        decode()
    } catch (e: IllegalArgumentException) {
        null
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

    /** When the last stamp from outside the app was recorded ([System.nanoTime], in milliseconds). */
    @Volatile
    private var lastQuickStamp: Long? = null

    /**
     * A stamp asked from outside the app: a notification button ([slot], for the working day
     * [date]), the widget or the Quick Settings tile (the next stamp). It is recorded only if it
     * still fits: the slot must be one the day can take now, the day must be the one the button
     * was shown for and not one away from the office. A button left from earlier (the exit
     * already stamped in the app, yesterday's reminder) does nothing instead of overwriting a
     * stamp, and a second tap right after a stamp is a double tap, not one more stamp.
     */
    suspend fun quickStamp(
        now: LocalDateTime,
        slot: StampSlot? = null,
        date: LocalDate? = null,
        elapsedMillis: Long = System.nanoTime() / 1_000_000,
    ): Stamp? {
        var stamp: Stamp? = null
        update { data ->
            val last = lastQuickStamp
            if (last != null && elapsedMillis - last in 0 until QUICK_STAMP_GAP_MS) return@update data
            val active = Engine.activeDay(data, now)
            if (date != null && active.date != date) return@update data
            val calendar = WorkCalendar.from(data)
            if (!Engine.quickStampsAllowed(calendar.effectiveType(active.date))) return@update data
            val record = data.days[active.date.toString()] ?: DayRecord.EMPTY
            val expectsBreak = calendar.expectsBreak(active.date)
            val chosen = slot ?: record.nextSlot(expectsBreak) ?: return@update data
            if (!record.accepts(chosen, expectsBreak)) return@update data
            val value = Engine.normalizeStamp(record, chosen, active.minutes)
            stamp = Stamp(active.date, chosen, value, record)
            lastQuickStamp = elapsedMillis
            data.withDay(active.date) { it.with(chosen, value) }
        }
        return stamp
    }

    /**
     * Takes a stamp back: the stamps and the day type return to [previous], while the rest of the
     * day stays as it is now (an intervention started or stopped meanwhile is kept).
     */
    suspend fun undoStamp(date: LocalDate, previous: DayRecord) {
        updateDay(date) { day ->
            day.copy(type = previous.type, entry = previous.entry, breakOut = previous.breakOut, breakIn = previous.breakIn, exit = previous.exit)
        }
    }

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
            val (stopped, done) = stopRunning(data, now) ?: return@update data
            change = done
            stopped
        }
        return change
    }

    /** [data] with the running timer stopped at [now], or null if none is running. */
    private fun stopRunning(data: StaccoData, now: LocalDateTime): Pair<StaccoData, InterventionChange>? {
        val (date, running) = OnCall.running(data, now.toLocalDate()) ?: return null
        val done = running.copy(end = max(running.start, OnCall.minutesOf(date, now)))
        val change = InterventionChange(date, done, data.days[date.toString()] ?: DayRecord.EMPTY)
        return data.withDay(date) { day -> day.copy(interventions = day.interventions.map { if (it.id == running.id) done else it }) } to change
    }

    /** Takes a stop back: intervention [id] of [date] runs again, unless another timer was started meanwhile. */
    suspend fun resumeIntervention(date: LocalDate, id: Long, now: LocalDateTime) {
        update { data ->
            if (OnCall.running(data, now.toLocalDate()) != null) return@update data
            data.withDay(date) { day -> day.copy(interventions = day.interventions.map { if (it.id == id) it.copy(end = null) else it }) }
        }
    }

    /**
     * Turns on-call duty on or off. Off, nothing of it shows any more, so a timer still running is
     * stopped right then instead of going on unseen; it is returned for the user to know.
     */
    suspend fun setOnCallEnabled(on: Boolean, now: LocalDateTime): InterventionChange? {
        var change: InterventionChange? = null
        update { data ->
            val base = if (on) data else stopRunning(data, now)?.let { (stopped, done) -> change = done; stopped } ?: data
            base.copy(onCall = base.onCall.copy(enabled = on))
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
    suspend fun changeSchedule(next: Schedule, from: LocalDate?, today: LocalDate = LocalDate.now()) {
        update { data ->
            // The days before the change keep their rules: those recorded, and with a change still
            // to come, today and the days until then. Otherwise there is no past to protect.
            val effectiveFrom = from?.takeIf { day -> day.isAfter(today) || data.days.keys.any { it < day.toString() } }
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

    /**
     * Saves what the setup gathered: its settings, the hour bank balance and, when given, the
     * vacation hours left [today] (as on the payslip; otherwise the whole year's are). Days and
     * adjustments already there stay (after a reset there are none).
     */
    suspend fun completeOnboarding(draft: StaccoData, balance: Int, vacationLeft: Int?, today: LocalDate) {
        update { draft.copy(onboardingDone = true, days = it.days, adjustments = it.adjustments) }
        setBalance(balance, today)
        if (vacationLeft != null) setVacationAvailable(vacationLeft, today)
    }

    /** Wipes everything: the app goes back to the welcome flow. */
    suspend fun reset() {
        update { StaccoData() }
    }

    /** Restores a backup; the coffee card keeps the choices already made on this phone. */
    suspend fun replaceAll(newData: StaccoData) {
        update { current -> newData.copy(onboardingDone = true, support = SupportRules.restored(current.support, newData.support)) }
    }

    companion object {
        /** Two stamps from outside the app closer than this are one double tap. */
        const val QUICK_STAMP_GAP_MS = 5_000L

        fun create(context: Context, scope: CoroutineScope): StaccoRepository {
            val file = context.dataStoreFile("stacco.json")
            // A copy of a data file that had to be repaired, kept beside it.
            val damaged = File(file.parentFile, "stacco-damaged.json")
            val store = DataStoreFactory.create(
                serializer = StaccoDataSerializer(keepDamaged = { damaged.writeBytes(it) }),
                corruptionHandler = ReplaceFileCorruptionHandler { StaccoData() },
                scope = scope,
                produceFile = { file },
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

    /** Ten years of days take about 2 MB; anything this large is not a Stacco backup. */
    const val MAX_BYTES = 16 * 1024 * 1024

    fun encode(data: StaccoData): String = pretty.encodeToString(StaccoData.serializer(), data)

    /** The text of a file picked for import, or null when it is far too large to be a backup. */
    fun read(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            bytes.write(buffer, 0, n)
            if (bytes.size() > MAX_BYTES) return null
        }
        return bytes.toByteArray().decodeToString()
    }

    /**
     * A Stacco backup, or null. Any JSON object would otherwise decode (unknown keys are ignored)
     * into empty defaults and an import would wipe every day: a real backup always carries
     * "schemaVersion" together with the work settings. Backups of every version since 1.0 read,
     * also once an editor has saved them again; impossible values are brought back in range.
     */
    fun decode(text: String): StaccoData? = runCatching {
        val root = StaccoJson.parseToJsonElement(text.removePrefix(BOM)).jsonObject
        if ("schemaVersion" !in root || "work" !in root) return null
        Sanitizer.clean(StaccoJson.decodeFromJsonElement(StaccoData.serializer(), root))
    }.getOrNull()
}
