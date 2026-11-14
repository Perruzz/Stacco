package io.github.perruzz.stacco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale
import kotlin.math.abs

/**
 * A 3D drum picker with a satisfying haptic tick for every value that passes the center.
 */
@Composable
fun WheelPicker(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    itemHeight: Dp = 54.dp,
    visibleItems: Int = 5,
    width: Dp = 88.dp,
    textStyle: TextStyle = MaterialTheme.typography.displaySmall,
    label: (Int) -> String = { String.format(Locale.ROOT, "%02d", it) },
) {
    val haptic = LocalHapticFeedback.current
    val total = if (loop) count * 1000 else count
    val base = if (loop) (total / 2) - ((total / 2) % count) else 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = base + value.coerceIn(0, count - 1))
    val fling = rememberSnapFlingBehavior(lazyListState = listState, snapPosition = SnapPosition.Center)
    val sidePadding = itemHeight * (visibleItems / 2)
    val currentValue by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)

    val centerIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
                ?: listState.firstVisibleItemIndex
        }
    }

    // Report every value that crosses the center, with a little tick.
    LaunchedEffect(listState) {
        snapshotFlow { centerIndex }
            .distinctUntilChanged()
            .collect { index ->
                val v = if (loop) index % count else index.coerceIn(0, count - 1)
                if (v != currentValue) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    onChange(v)
                }
            }
    }

    // Follow programmatic changes (e.g. the "Now" button).
    LaunchedEffect(value) {
        val currentIndex = centerIndex
        val currentV = if (loop) currentIndex % count else currentIndex
        if (currentV != value && !listState.isScrollInProgress) {
            val target = if (loop) {
                val diff = ((value - currentV) % count + count) % count
                val forward = currentIndex + diff
                val backward = currentIndex - (count - diff)
                if (diff <= count / 2) forward else backward
            } else {
                value.coerceIn(0, count - 1)
            }
            listState.animateScrollToItem(target)
        }
    }

    val selectedColor = MaterialTheme.colorScheme.onSurface
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .width(width)
            .height(itemHeight * visibleItems)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.32f to Color.Black,
                        0.68f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
        )
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = sidePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(count = total) { index ->
                val v = if (loop) index % count else index
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val info = listState.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                            if (item != null) {
                                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                val d = ((item.offset + item.size / 2f) - center) / item.size
                                val ad = abs(d).coerceAtMost(3f)
                                alpha = 1f - ad * 0.25f
                                scaleX = 1f - ad * 0.1f
                                scaleY = scaleX
                                rotationX = -d * 24f
                                cameraDistance = 10f * density
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(v),
                        style = textStyle,
                        color = if (index == centerIndex) selectedColor else idleColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
