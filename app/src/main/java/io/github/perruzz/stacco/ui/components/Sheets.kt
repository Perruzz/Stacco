package io.github.perruzz.stacco.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Window insets for a ModalBottomSheet: none, its [SheetColumn] takes care of them.
 *
 * Material 3 pads a sheet for the status bar by as much as the sheet has moved into it, so a sheet
 * reaching the status bar changes height with its own position. Its resting point is computed from
 * its height and, once it moves, the settle animation starts over with the speed of the fling that
 * began it: a fast fling ending on the day sheet threw it against the status bar, where it grew,
 * moved its own resting point and was thrown again, bouncing up and down by itself.
 */
val SheetInsets = WindowInsets(0, 0, 0, 0)

/** Never taller than this share of the room the sheet has. */
private const val SHEET_MAX_FRACTION = 0.9f

/**
 * The scrolling body of a bottom sheet (with [SheetInsets]): whatever its content, the sheet keeps
 * the same size wherever it is.
 * - It is never taller than 90% of the room the sheet has and always leaves the status bar free;
 *   with the keyboard up that room shrinks, so the body scrolls above the keyboard.
 * - It pads itself for the navigation bar and the keyboard, outside the scroll.
 * - The end of an upward fling stays in the body: handed to the sheet, it made the whole sheet
 *   bounce up out of place. Pulling the sheet down from the top of the content works as before.
 */
@Composable
fun SheetColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val keepClear = WindowInsets.statusBars.getTop(density) + with(density) { 8.dp.roundToPx() }
    Column(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val room = constraints.maxHeight
                val max = if (constraints.hasBoundedHeight) {
                    min((room * SHEET_MAX_FRACTION).roundToInt(), room - keepClear).coerceAtLeast(constraints.minHeight)
                } else {
                    room
                }
                val placeable = measurable.measure(constraints.copy(maxHeight = max))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .nestedScroll(KeepUpwardFling)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** Keeps what is left of an upward fling, so it never reaches the sheet. */
private val KeepUpwardFling = object : NestedScrollConnection {
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (available.y < 0f) Velocity(0f, available.y) else Velocity.Zero
}
