package io.github.perruzz.stacco.ui.components

import io.github.perruzz.stacco.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.ui.util.Fmt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Degrees of bezel rotation for one minute. */
private const val DEG_PER_MINUTE = 12f
private const val MIN_OFFSET = -180
private const val MAX_OFFSET = 60
private const val RETURN_AFTER_MS = 10_000

/**
 * The stamp button with a rotating bezel around it, like a diver's watch.
 *
 * The center is a clock showing the time that will be recorded: now, shifted by the bezel.
 * Each detent is one minute (with a haptic click). Left alone for 10 seconds the bezel springs
 * back and the center shows the current time again. Tapping the center records the stamp.
 */
@Composable
fun StampDial(
    actionLabel: String,
    icon: ImageVector,
    nowSeconds: Int,
    onStamp: (minutes: Int) -> Unit,
    modifier: Modifier = Modifier,
    nowLabel: String = "",
    accent: Color = MaterialTheme.colorScheme.primary,
    onAccent: Color = MaterialTheme.colorScheme.onPrimary,
    pastColor: Color = Color(0xFFE0A94A),
    size: Dp = 268.dp,
    describe: (Int) -> String = { Fmt.offset(it) },
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val countdown = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf(false) }
    // Only the user's own turns click: the bezel going back home by itself stays silent.
    var userTurning by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    var lastAngle by remember { mutableFloatStateOf(0f) }
    val currentStamp by rememberUpdatedState(onStamp)

    // Only a new minute recomposes the dial: the bezel itself turns in the draw phase.
    val offset by remember { derivedStateOf { (rotation.value / DEG_PER_MINUTE).roundToInt() } }
    val nowMinutes = nowSeconds / 60
    val stampMinutes = nowMinutes + offset

    // A click for every minute that passes under the pointer.
    LaunchedEffect(Unit) {
        snapshotFlow { (rotation.value / DEG_PER_MINUTE).roundToInt() }
            .drop(1)
            .collect { if (userTurning) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
    }

    /**
     * Turns the bezel back exactly to "now". It runs in [scope], not in the countdown effect below:
     * that effect restarts as soon as the offset reads 0 (less than half a detent away), and
     * running the animation there cancelled it a few degrees short of home.
     */
    fun returnHome(stiffness: Float = 140f) {
        returning = true
        userTurning = false
        scope.launch {
            try {
                // Damped enough not to overshoot past the previous minute.
                rotation.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = stiffness))
            } finally {
                returning = false
            }
        }
    }

    /** Snaps to the nearest detent once the finger lifts. */
    fun settle() {
        dragging = false
        lastTouch = System.nanoTime()
        val settled = ((rotation.value / DEG_PER_MINUTE).roundToInt().coerceIn(MIN_OFFSET, MAX_OFFSET)) * DEG_PER_MINUTE
        scope.launch { rotation.animateTo(settled, spring(dampingRatio = 0.6f, stiffness = 420f)) }
    }

    // Left alone, the bezel goes back to "now" after 10 seconds.
    LaunchedEffect(lastTouch, dragging, offset == 0) {
        countdown.snapTo(0f)
        if (dragging || offset == 0 || returning) return@LaunchedEffect
        countdown.animateTo(1f, tween(RETURN_AFTER_MS, easing = LinearEasing))
        countdown.snapTo(0f)
        returnHome()
    }

    fun nudge(minutes: Int) {
        returning = false
        userTurning = true
        lastTouch = System.nanoTime()
        val target = ((offset + minutes).coerceIn(MIN_OFFSET, MAX_OFFSET)) * DEG_PER_MINUTE
        scope.launch { rotation.animateTo(target, spring(dampingRatio = 0.7f, stiffness = 500f)) }
    }

    val dragGlow by animateFloatAsState(if (dragging) 1f else 0f, tween(300), label = "dial-glow")
    val shifted = offset != 0
    val arcColor by animateColorAsState(if (offset < 0) pastColor else accent, label = "dial-arc")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val trackDark = MaterialTheme.colorScheme.surfaceContainerHigh
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val pointer = MaterialTheme.colorScheme.onSurface

    // With TalkBack the dial is a slider: swiping up or down moves the time by a minute.
    val dialLabel = stringResource(R.string.a11y_dial)
    val adjustLabel = stringResource(R.string.a11y_dial_adjust)
    val state = listOf(Fmt.time(stampMinutes), if (offset == 0) nowLabel else describe(offset)).filter { it.isNotEmpty() }.joinToString(", ")
    Box(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = dialLabel
                stateDescription = state
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = offset.toFloat(),
                    range = MIN_OFFSET.toFloat()..MAX_OFFSET.toFloat(),
                    steps = MAX_OFFSET - MIN_OFFSET - 1,
                )
                setProgress(label = adjustLabel) { value ->
                    nudge(value.roundToInt() - offset)
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    // The bezel and a little around it; the corners of the square are not the dial.
                    val reach = minOf(this.size.width, this.size.height) / 2f + 4.dp.toPx()
                    fun angleOf(p: Offset) = (atan2(p.y - center.y, p.x - center.x) * 180f / PI.toFloat())
                    fun turnTo(position: Offset) {
                        val angle = angleOf(position)
                        var delta = angle - lastAngle
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        lastAngle = angle
                        val min = MIN_OFFSET * DEG_PER_MINUTE
                        val max = MAX_OFFSET * DEG_PER_MINUTE
                        val current = rotation.value
                        // Rubber band beyond the limits.
                        val resisted = if ((current <= min && delta < 0) || (current >= max && delta > 0)) delta * 0.25f else delta
                        scope.launch { rotation.snapTo(current + resisted) }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val fromCenter = down.position - center
                        val distance = fromCenter.getDistance()
                        if (distance > reach || distance == 0f) return@awaitEachGesture
                        // The bezel turns when the finger goes around it; a swipe across the dial
                        // (up or down over its top or bottom) is left to the page, which scrolls.
                        val radial = fromCenter / distance
                        val start = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                            val moved = change.position - down.position
                            val across = abs(moved.x * radial.x + moved.y * radial.y)
                            val around = abs(moved.y * radial.x - moved.x * radial.y)
                            if (around >= across) change.consume()
                        } ?: return@awaitEachGesture
                        dragging = true
                        returning = false
                        userTurning = true
                        lastAngle = angleOf(down.position)
                        scope.launch { rotation.stop() }
                        turnTo(start.position)
                        drag(start.id) { change ->
                            turnTo(change.position)
                            change.consume()
                        }
                        settle()
                    }
                },
        ) {
            val outer = this.size.minDimension / 2f - 6.dp.toPx()
            val bezelWidth = 30.dp.toPx()
            val inner = outer - bezelWidth
            val mid = (outer + inner) / 2f
            val c = center
            val angle = rotation.value + spin.value

            // Soft glow while turning
            if (dragGlow > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.28f * dragGlow), Color.Transparent),
                        center = c,
                        radius = outer + 18.dp.toPx(),
                    ),
                    radius = outer + 18.dp.toPx(),
                    center = c,
                )
            }

            // Bezel body with a metallic sheen that turns with it
            rotate(angle, c) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(track, trackDark, track, trackDark, track),
                        center = c,
                    ),
                    radius = mid,
                    center = c,
                    style = Stroke(width = bezelWidth),
                )
                // Knurled grip on the outer edge
                val knurls = 96
                for (i in 0 until knurls) {
                    val a = (i * 360f / knurls) * (PI.toFloat() / 180f)
                    val r1 = outer - 7.dp.toPx()
                    drawLine(
                        color = tick.copy(alpha = 0.30f),
                        start = Offset(c.x + r1 * cos(a), c.y + r1 * sin(a)),
                        end = Offset(c.x + outer * cos(a), c.y + outer * sin(a)),
                        strokeWidth = 1.2.dp.toPx(),
                    )
                }
                // Minute ticks, a long one every five minutes
                val ticks = (360f / DEG_PER_MINUTE).toInt()
                for (i in 0 until ticks) {
                    val a = (i * DEG_PER_MINUTE - 90f) * (PI.toFloat() / 180f)
                    val long = i % 5 == 0
                    val r0 = inner + 6.dp.toPx()
                    val r1 = r0 + if (long) 11.dp.toPx() else 6.dp.toPx()
                    drawLine(
                        color = tick.copy(alpha = if (long) 0.75f else 0.4f),
                        start = Offset(c.x + r0 * cos(a), c.y + r0 * sin(a)),
                        end = Offset(c.x + r1 * cos(a), c.y + r1 * sin(a)),
                        strokeWidth = (if (long) 2.4f else 1.4f).dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            // How far from "now": an arc from the zero mark to the pointer
            val sweep = rotation.value.coerceIn(-359f, 359f)
            if (abs(sweep) > 0.5f) {
                drawArc(
                    color = arcColor.copy(alpha = 0.85f),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(c.x - (inner + 3.dp.toPx()), c.y - (inner + 3.dp.toPx())),
                    size = Size((inner + 3.dp.toPx()) * 2, (inner + 3.dp.toPx()) * 2),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            // The "now" mark travelling with the bezel
            val zero = (-90f + angle) * (PI.toFloat() / 180f)
            drawCircle(accent, radius = 4.5.dp.toPx(), center = Offset(c.x + mid * cos(zero), c.y + mid * sin(zero)))

            // Fixed pointer at 12 o'clock
            drawPointer(c, outer, pointer)

            // Countdown before the bezel goes back home
            if (countdown.value > 0f) {
                drawArc(
                    color = accent.copy(alpha = 0.55f),
                    startAngle = -90f,
                    sweepAngle = 360f * (1f - countdown.value),
                    useCenter = false,
                    topLeft = Offset(c.x - (inner - 6.dp.toPx()), c.y - (inner - 6.dp.toPx())),
                    size = Size((inner - 6.dp.toPx()) * 2, (inner - 6.dp.toPx()) * 2),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // Ripple after a stamp
            if (burst.value > 0f && burst.value < 1f) {
                drawCircle(
                    color = accent.copy(alpha = 0.45f * (1f - burst.value)),
                    radius = inner * (0.6f + 0.9f * burst.value),
                    center = c,
                    style = Stroke(width = 6.dp.toPx() * (1f - burst.value)),
                )
            }
        }

        // The stamp button in the middle
        val interaction = remember { MutableInteractionSource() }
        val buttonSize = size - 30.dp * 2 - 32.dp
        Box(
            modifier = Modifier
                .size(buttonSize)
                .pressScale(interaction, 0.92f)
                .shadow(14.dp, CircleShape, ambientColor = accent, spotColor = accent)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(lerpLight(accent), accent, lerpDark(accent))))
                .bouncyClickable(haptic = false, pressed = 0.94f) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentStamp(stampMinutes)
                    scope.launch {
                        burst.snapTo(0f)
                        burst.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
                        burst.snapTo(0f)
                    }
                    scope.launch {
                        spin.animateTo(-360f, tween(900, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)))
                        spin.snapTo(0f)
                    }
                    returnHome(stiffness = 200f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null, tint = onAccent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(2.dp))
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = onAccent.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                RollingText(
                    Fmt.time(stampMinutes),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = onAccent,
                )
                AnimatedContent(
                    targetState = if (offset == 0) nowLabel else describe(offset),
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.8f)) },
                    label = "dial-chip",
                ) { label ->
                    if (label.isNotEmpty()) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (offset < 0) Color(0xFF2B1D07) else onAccent,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .background(if (offset < 0) pastColor.copy(alpha = 0.95f) else onAccent.copy(alpha = if (shifted) 0.28f else 0.18f), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        // Fine adjustment, also handy with TalkBack
        FilledTonalIconButton(
            onClick = { nudge(-1) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .graphicsLayer { alpha = 0.9f },
        ) { Icon(Icons.Rounded.Remove, contentDescription = describe(-1), modifier = Modifier.size(18.dp)) }
        FilledTonalIconButton(
            onClick = { nudge(1) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .graphicsLayer { alpha = 0.9f },
        ) { Icon(Icons.Rounded.Add, contentDescription = describe(1), modifier = Modifier.size(18.dp)) }
    }
}

private fun DrawScope.drawPointer(c: Offset, outer: Float, color: Color) {
    val tipY = c.y - outer + 2.dp.toPx()
    val w = 8.dp.toPx()
    val h = 10.dp.toPx()
    val path = Path().apply {
        moveTo(c.x, tipY + h)
        lineTo(c.x - w / 2f, tipY - 2.dp.toPx())
        lineTo(c.x + w / 2f, tipY - 2.dp.toPx())
        close()
    }
    drawPath(path, color)
}

private fun lerpLight(color: Color): Color = Color(
    red = color.red + (1f - color.red) * 0.16f,
    green = color.green + (1f - color.green) * 0.16f,
    blue = color.blue + (1f - color.blue) * 0.16f,
    alpha = 1f,
)

private fun lerpDark(color: Color): Color = Color(
    red = color.red * 0.72f,
    green = color.green * 0.72f,
    blue = color.blue * 0.72f,
    alpha = 1f,
)
