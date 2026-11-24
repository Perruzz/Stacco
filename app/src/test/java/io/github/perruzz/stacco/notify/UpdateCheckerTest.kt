package io.github.perruzz.stacco.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `later versions are newer`() {
        assertTrue(UpdateChecker.isNewer("1.2.0", "1.1.0"))
        assertTrue(UpdateChecker.isNewer("v1.2.0", "1.1.0"))
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.3"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("1.1.1", "1.1"))
    }

    @Test
    fun `same or older versions are not newer`() {
        assertFalse(UpdateChecker.isNewer("1.1.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("v1.1", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("1.0.9", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("0.9", "1.0"))
    }

    @Test
    fun `garbage and suffixes are handled`() {
        assertFalse(UpdateChecker.isNewer("latest", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("1.1.0-beta", "1.1.0"))
        assertTrue(UpdateChecker.isNewer("1.2.0", "1.1.0-debug"))
    }

    @Test
    fun `an answer far too large for a release is not read whole`() {
        val release = """{"tag_name":"v2.0.0","html_url":"https://github.com/Perruzz/Stacco/releases/tag/v2.0.0"}"""
        assertEquals(release, UpdateChecker.readAtMost(release.byteInputStream(), 1 shl 20))
        // A captive portal, a proxy gone wrong: megabytes of something else.
        assertNull(UpdateChecker.readAtMost(java.io.ByteArrayInputStream(ByteArray(3 shl 20)), 1 shl 20))
    }
}
