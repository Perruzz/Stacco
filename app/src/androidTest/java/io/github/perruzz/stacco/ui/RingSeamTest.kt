package io.github.perruzz.stacco.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.perruzz.stacco.ui.components.DayRing
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The Today ring as drawn, around 12 o'clock where it starts: the round cap of the start has
 * the color of the start (no half-moon of the head's), and a closed ring has no seam.
 */
@RunWith(AndroidJUnit4::class)
class RingSeamTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val startColor = Color(0xFFE53935)
    private val endColor = Color(0xFF1E5AE8)

    private class Ring(val pixels: androidx.compose.ui.graphics.PixelMap, val cx: Float, val cy: Float, val radius: Float, val stroke: Float) {
        /** The color at [degrees] clockwise from 12 o'clock, [offset] pixels out from the middle of the stroke. */
        fun at(degrees: Double, offset: Float = 0f): Color {
            val a = degrees * PI / 180
            val r = radius + offset
            return pixels[(cx + r * sin(a)).toInt(), (cy - r * cos(a)).toInt()]
        }

        /** How far the round cap of the start reaches back before 12 o'clock, in degrees. */
        val capDegrees: Double get() = asin(stroke / 2.0 / radius) * 180 / PI
    }

    private fun draw(progress: Float, colors: List<Color>): Ring {
        rule.setContent {
            StaccoTheme(darkTheme = false) {
                // The size of the ring on Today: 735 x 656 px at 420 dpi, with its center on a pixel's.
                Box(Modifier.size(280.dp, 250.dp).testTag("ring")) {
                    DayRing(progress = progress, colors = colors, trackColor = Color.LightGray, flowing = false, modifier = Modifier.size(280.dp, 250.dp)) {}
                }
            }
        }
        rule.waitForIdle()
        val image = rule.onNodeWithTag("ring").captureToImage()
        val stroke = with(rule.density) { 16.dp.toPx() }
        val radius = min(image.width, image.height) / 2f - stroke * 1.2f
        return Ring(image.toPixelMap(), image.width / 2f, image.height / 2f, radius, stroke)
    }

    private fun Color.distance(o: Color) = maxOf(abs(red - o.red), abs(green - o.green), abs(blue - o.blue))

    @Test
    fun theStartCapHasTheColorOfTheStart() {
        val ring = draw(0.5f, listOf(startColor, endColor))
        val half = ring.capDegrees / 2
        val before = ring.at(-half)
        val after = ring.at(half)
        assertTrue("before 12 o'clock $before, after $after", before.distance(after) < 0.08f)
        assertTrue("the start color: $after", after.distance(startColor) < 0.08f)
    }

    @Test
    fun theStartCapHasTheOpacityOfTheStartDuringTheBreak() {
        val lunch = Color(0xFFC8954C)
        val ring = draw(0.5f, listOf(lunch.copy(alpha = 0.7f), lunch))
        val half = ring.capDegrees / 2
        assertTrue(ring.at(-half).distance(ring.at(half)) < 0.04f)
    }

    @Test
    fun noHairlineThroughTheCenter() {
        // The column of pixels on the vertical through the center, at 12 and at 6 o'clock, against its neighbours.
        val ring = draw(0.75f, listOf(startColor, endColor))
        for (degrees in listOf(0.0, 180.0)) {
            val a = degrees * PI / 180
            val y = (ring.cy - ring.radius * cos(a)).toInt()
            val x = ring.cx.toInt()
            val column = ring.pixels[x, y]
            for (side in listOf(-2, 2)) {
                val beside = ring.pixels[x + side, y]
                assertTrue("at $degrees°: $column, $side px aside $beside", column.distance(beside) < 0.06f)
            }
        }
    }

    @Test
    fun aClosedRingHasNoSeam() {
        val ring = draw(1f, listOf(startColor, endColor))
        // On the ring indeed: the start color near the start, the end color near the end.
        assertTrue("near the start ${ring.at(20.0)}", ring.at(20.0).red > 0.6f && ring.at(20.0).blue < 0.4f)
        assertTrue("near the end ${ring.at(320.0)}", ring.at(320.0).blue > 0.6f && ring.at(320.0).red < 0.4f)
        // Along the inner and the outer part of the stroke (the white dot of the head sits in the middle).
        for (offset in listOf(-0.35f * ring.stroke, 0.35f * ring.stroke)) {
            val samples = (0 until 720).map { ring.at(it / 2.0, offset) }
            val steps = (samples.zipWithNext() + (samples.last() to samples.first())).map { (a, b) -> a.distance(b) }
            val worst = steps.max()
            assertTrue("largest step between neighbours, ${steps.indexOf(worst) / 2.0}° (offset $offset): $worst", worst < 0.1f)
        }
    }
}
