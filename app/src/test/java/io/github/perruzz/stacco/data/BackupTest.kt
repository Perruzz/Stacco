package io.github.perruzz.stacco.data

import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.StaccoData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BackupTest {

    @Test
    fun `a real backup round-trips`() {
        val data = StaccoData(onboardingDone = true, days = mapOf("2026-11-16" to DayRecord(entry = 480, exit = 1000)))
        val decoded = Backup.decode(Backup.encode(data))
        assertNotNull(decoded)
        assertEquals(data, decoded)
    }

    @Test
    fun `json that is not a stacco backup is refused`() {
        // These used to decode into empty defaults, and importing them wiped every day.
        assertNull(Backup.decode("{}"))
        assertNull(Backup.decode("""{"name":"something else","items":[1,2,3]}"""))
        assertNull(Backup.decode("""{"schemaVersion":2}"""))
        assertNull(Backup.decode("[1,2,3]"))
        assertNull(Backup.decode("not json at all"))
    }
}
