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
import io.github.perruzz.stacco.domain.ShiftTemplate
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.StampSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime

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

    fun decode(text: String): StaccoData? =
        runCatching { StaccoJson.decodeFromString(StaccoData.serializer(), text) }.getOrNull()
}
