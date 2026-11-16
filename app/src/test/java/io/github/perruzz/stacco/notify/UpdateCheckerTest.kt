package io.github.perruzz.stacco.notify

import org.junit.Assert.assertFalse
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
}
