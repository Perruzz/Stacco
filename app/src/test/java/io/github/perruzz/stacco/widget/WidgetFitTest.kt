package io.github.perruzz.stacco.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The home screen widget at every size it can be given, with large fonts too. */
class WidgetFitTest {

    /** The height the layout takes, as [widgetFit] counts it. */
    private fun used(fit: WidgetFit, fontScale: Float, button: Boolean): Float {
        val small = 12f * fontScale * 1.4f
        return 2 * fit.padding.value + fit.bigSp * fontScale * 1.2f +
            (if (button) WIDGET_BUTTON_HEIGHT + fit.gap.value else 0f) +
            (if (fit.label) small else 0f) + (if (fit.bank) small else 0f) +
            (if (fit.header) 18f * maxOf(1f, fontScale) + 8f else 0f)
    }

    private fun check(width: Dp, height: Dp, fontScale: Float, big: String = "17:12", button: Boolean = true): WidgetFit {
        val fit = widgetFit(width, height, fontScale, big, if (button) "Clock out" else null)
        // The value stays on one line, inside the widget.
        assertTrue("$width × $height at $fontScale: value too wide", big.length * 0.6f * fit.bigSp * fontScale <= width.value - 2 * fit.padding.value + 0.5f || fit.bigSp == 14f)
        // Nothing goes below the bottom edge: the button keeps its whole touch area.
        assertTrue("$width × $height at $fontScale: ${used(fit, fontScale, button)}dp used", used(fit, fontScale, button) <= height.value + 0.5f || fit.bigSp == 14f)
        return fit
    }

    @Test
    fun `the usual two by two widget shows everything`() {
        val fit = check(180.dp, 200.dp, 1f)
        assertTrue(fit.header && fit.label && fit.bank)
        assertEquals(34f, fit.bigSp)
    }

    @Test
    fun `a short widget keeps the value and the button, then drops the rest`() {
        val fit = check(180.dp, 110.dp, 1f)
        assertFalse(fit.header)
        assertFalse(fit.bank)
        val short = check(180.dp, 100.dp, 1.3f)
        assertFalse(short.label || short.bank || short.header)
    }

    @Test
    fun `large fonts never push the button out of the widget`() {
        for (scale in listOf(1f, 1.15f, 1.3f, 1.5f, 2f)) {
            for (height in listOf(100, 120, 150, 180, 200, 260)) {
                for (width in listOf(120, 160, 200, 320)) check(width.dp, height.dp, scale)
            }
        }
    }

    @Test
    fun `a long value gets smaller instead of being cut`() {
        val fit = check(180.dp, 200.dp, 1f, big = "Smart working")
        assertTrue(fit.bigSp < 34f)
        // Without a button (a day away from the office) the value has more room.
        assertTrue(check(180.dp, 100.dp, 1.3f, button = false).bigSp > check(180.dp, 100.dp, 1.3f).bigSp)
    }

    @Test
    fun `a button too long for its width says just the stamp`() {
        assertTrue(widgetFit(180.dp, 200.dp, 1f, "15:20", "Start lunch break").longButton)
        // Large fonts, or a narrow widget: "Break start" rather than a cut "Start lunch".
        assertFalse(widgetFit(180.dp, 200.dp, 1.5f, "15:20", "Start lunch break").longButton)
        assertFalse(widgetFit(130.dp, 200.dp, 1f, "15:20", "Rientro dalla pausa").longButton)
        assertTrue(widgetFit(130.dp, 200.dp, 1f, "15:20", "Uscita").longButton)
        // The short name is made smaller rather than cut ("Break" alone says nothing).
        val narrow = widgetFit(135.dp, 160.dp, 1.5f, "15:20", "Start lunch break", "Break start")
        assertFalse(narrow.longButton)
        assertTrue(narrow.buttonSp < 14f)
        assertTrue("Break start".length * 0.52f * narrow.buttonSp * 1.5f + 32f <= 135f - 2 * narrow.padding.value + 0.5f)
        // Italian at 150% in a widget 132dp wide: "Inizio pausa", whole.
        val italian = widgetFit(132.dp, 160.dp, 1.5f, "15:20", "Vado in pausa", "Inizio pausa")
        assertTrue("Inizio pausa".length * 0.52f * italian.buttonSp * 1.5f + 32f <= 132f - 2 * italian.padding.value + 0.5f)
    }
}
