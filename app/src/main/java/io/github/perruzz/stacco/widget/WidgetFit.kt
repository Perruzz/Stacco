package io.github.perruzz.stacco.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What fits in a widget [width] × [height] with the fonts at [fontScale]: the big value always,
 * as large as the room allows and on one line; the stamp button with its full 48dp touch area;
 * then, as long as there is room, the line above the value, the hour bank and the heading.
 */
internal data class WidgetFit(
    val padding: Dp,
    val gap: Dp,
    val bigSp: Float,
    val label: Boolean,
    val bank: Boolean,
    val header: Boolean,
    /** The button says its whole action ("Start lunch break"), or just the stamp ("Break start"). */
    val longButton: Boolean = true,
    /** The size of the button's text: smaller rather than cut. */
    val buttonSp: Float = BUTTON_SP,
)

private const val BUTTON_SP = 14f

internal const val WIDGET_BUTTON_HEIGHT = 48

/** [button]: the whole text of the stamp button, null without one; [shortButton]: its short form. */
internal fun widgetFit(width: Dp, height: Dp, fontScale: Float, big: String, button: String?, shortButton: String? = button): WidgetFit {
    val tiny = height < 120.dp
    val compact = tiny || width < 150.dp || height < 160.dp
    val padding = when {
        tiny -> 8f
        compact -> 12f
        else -> 16f
    }
    val gap = if (tiny) 4f else 8f
    val buttonRoom = if (button != null) WIDGET_BUTTON_HEIGHT + gap else 0f
    // About 0.6 em a character and 1.2 em a line.
    val byWidth = (width.value - 2 * padding) / (maxOf(big.length, 4) * 0.6f * fontScale)
    val byHeight = (height.value - 2 * padding - buttonRoom) / (1.2f * fontScale)
    val largest = when {
        tiny -> 22f
        compact -> 28f
        else -> 34f
    }
    val bigSp = minOf(largest, byWidth, byHeight).coerceAtLeast(14f)
    var room = height.value - 2 * padding - buttonRoom - bigSp * fontScale * 1.2f
    fun fits(line: Float): Boolean = (room >= line).also { if (it) room -= line }
    val small = 12f * fontScale * 1.4f
    val label = fits(small)
    val bank = fits(small)
    val header = fits(18f * maxOf(1f, fontScale) + 8f)
    // A button's text takes up to about 0.52 em a character (Italian words are wider than
    // "Start lunch break", measured at 0.44 on a Pixel), inside the button's 16dp padding.
    val textRoom = width.value - 2 * padding - 32f
    fun sizeFor(text: String) = textRoom / (maxOf(text.length, 1) * 0.52f * fontScale)
    // The whole action, a little smaller if need be; otherwise the stamp's short name, as small as it takes.
    val longButton = button == null || sizeFor(button) >= 12f
    val buttonSp = when {
        button == null -> BUTTON_SP
        longButton -> minOf(BUTTON_SP, sizeFor(button))
        else -> minOf(BUTTON_SP, sizeFor(shortButton ?: button)).coerceAtLeast(8f)
    }
    return WidgetFit(padding.dp, gap.dp, bigSp, label, bank, header, longButton, buttonSp)
}
