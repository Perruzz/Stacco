package io.github.perruzz.stacco.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The colors of the Today ring around 12 o'clock, where its sweep gradient starts and ends. */
class RingStopsTest {

    private val start = Color(1f, 0f, 0f)
    private val end = Color(0f, 0f, 1f)

    /** The start cap of a 16dp ring, 278px from its center on a 420dpi phone, in turns. */
    private val cap = 0.012f

    /** The gradient's color at [x] (a share of the turn from 12 o'clock), as Android interpolates it. */
    private fun colorAt(stops: Array<Pair<Float, Color>>, x: Float): Color {
        val i = stops.indexOfFirst { it.first >= x }
        if (i <= 0) return stops.first().second
        val (a, from) = stops[i - 1]
        val (b, to) = stops[i]
        val t = if (b > a) (x - a) / (b - a) else 1f
        fun mix(u: Float, v: Float) = u + (v - u) * t
        return Color(mix(from.red, to.red), mix(from.green, to.green), mix(from.blue, to.blue), mix(from.alpha, to.alpha))
    }

    private fun Color.distance(o: Color) = maxOf(abs(red - o.red), abs(green - o.green), abs(blue - o.blue), abs(alpha - o.alpha))

    private fun assertSame(expected: Color, actual: Color, what: String) =
        assertTrue("$what: $actual instead of $expected", expected.distance(actual) < 0.001f)

    @Test
    fun `around 12 o'clock the ring has the start color, at any progress`() {
        for (p in listOf(0.03f, 0.3f, 0.5f, 0.9f, 0.95f, 0.97f, 1f)) {
            val stops = ringStops(start, end, p, cap)
            assertEquals(0f, stops.first().first)
            assertEquals(1f, stops.last().first)
            assertTrue("stops in order at $p", stops.map { it.first }.zipWithNext().all { (a, b) -> b > a })
            // No jump where the gradient wraps around.
            assertSame(start, colorAt(stops, 0f), "12 o'clock at $p")
            assertSame(start, colorAt(stops, 1f), "a full turn at $p")
            // The round cap of the start, just before 12 o'clock: no half-moon of the head's color.
            assertSame(start, colorAt(stops, 1f - cap / 2), "start cap at $p")
        }
    }

    @Test
    fun `while the ring is open the arc keeps its colors`() {
        val stops = ringStops(start, end, 0.5f, cap)
        assertSame(start, colorAt(stops, 0f), "start")
        assertSame(Color(0.5f, 0f, 0.5f), colorAt(stops, 0.25f), "halfway")
        assertSame(end, colorAt(stops, 0.5f), "head")
        // The round cap of the head too.
        assertSame(end, colorAt(stops, 0.5f + cap), "head cap")
    }

    @Test
    fun `a closed ring has no seam anywhere`() {
        val stops = ringStops(start, end, 1f, cap)
        val samples = (0..3600).map { colorAt(stops, it / 3600f) }
        // Every tenth of a degree, the wrap from a full turn back to 12 o'clock included.
        val worst = (samples.zipWithNext() + (samples.last() to samples.first())).maxOf { (a, b) -> a.distance(b) }
        assertTrue("largest step between neighbours: $worst", worst < 0.02f)
        // It still reaches the end color, then goes back over a short stretch before 12 o'clock.
        assertSame(end, colorAt(stops, 1f - cap - RING_BLEND), "end color")
    }

    @Test
    fun `the head blends in only when it is about to close the ring`() {
        assertSame(end, colorAt(ringStops(start, end, 0.9f, cap), 0.9f), "head at 90%")
        val late = ringStops(start, end, 0.97f, cap)
        val head = colorAt(late, 0.97f)
        assertTrue("head at 97% partly back to the start color: $head", head.red > 0.2f && head.blue > 0.2f)
    }
}
