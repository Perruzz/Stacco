package io.github.perruzz.stacco.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A small tick drawn on the ring track, e.g. the "bank exit" moment. */
data class RingMarker(val fraction: Float, val color: Color)

/**
 * The heart of the Today screen: a ring that fills up during the day.
 * While you work the arc gently *flows* like water; during the break it calms down.
 */
@Composable
fun DayRing(
    progress: Float,
    colors: List<Color>,
    trackColor: Color,
    modifier: Modifier = Modifier,
    thickness: Dp = 16.dp,
    flowing: Boolean = true,
    glowColor: Color = colors.last(),
    markers: List<RingMarker> = emptyList(),
    content: @Composable BoxScope.() -> Unit,
) {
    // Entrance: the ring sweeps in from zero the first time it is shown.
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 60f),
        label = "ring-progress",
    )
    val transition = rememberInfiniteTransition(label = "ring")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "ring-phase",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "ring-pulse",
    )
    val amplitudeTarget = if (flowing) 1f else 0f
    val amplitude by animateFloatAsState(amplitudeTarget, tween(900), label = "ring-amp")
    val path = remember { Path() }

    Box(
        modifier = modifier.drawBehind {
            val stroke = thickness.toPx()
            val radius = min(size.width, size.height) / 2f - stroke * 1.2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val p = animated * intro.value

            // Track
            drawCircle(trackColor, radius = radius, center = center, style = Stroke(width = stroke))

            // Markers on the track
            markers.forEach { marker ->
                if (marker.fraction in 0.01f..0.99f) {
                    val angle = (-90f + 360f * marker.fraction) * (PI / 180f).toFloat()
                    val inner = radius - stroke * 0.85f
                    val outer = radius + stroke * 0.85f
                    drawLine(
                        color = marker.color,
                        start = Offset(center.x + inner * cos(angle), center.y + inner * sin(angle)),
                        end = Offset(center.x + outer * cos(angle), center.y + outer * sin(angle)),
                        strokeWidth = stroke * 0.28f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            if (p <= 0.0005f) return@drawBehind
            // At the very beginning of the day a tiny wavy arc looks like a blot: show only the glowing tip.
            val drawArc = p > 0.02f

            // Progress arc (a softly undulating path)
            val sweep = 2f * PI.toFloat() * p
            val steps = (180 * p).toInt().coerceAtLeast(8)
            val waveAmp = stroke * 0.13f * amplitude
            val waves = 14f
            path.reset()
            for (i in 0..steps) {
                val u = i / steps.toFloat()
                val theta = sweep * u
                val envelope = min(1f, min(u * 6f, (1f - u) * 6f))
                val r = radius + waveAmp * envelope * sin(waves * theta - phase * 2f)
                val x = center.x + r * cos(theta)
                val y = center.y + r * sin(theta)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val brush = if (colors.size > 1) {
                Brush.sweepGradient(
                    colorStops = arrayOf(0f to colors.first(), (p.coerceAtLeast(0.05f)) to colors.last(), 1f to colors.last()),
                    center = center,
                )
            } else {
                Brush.linearGradient(listOf(colors.first(), colors.first()))
            }
            rotate(-90f, center) {
                if (drawArc) drawPath(path, brush, style = Stroke(width = stroke, cap = StrokeCap.Round))
                // Glowing tip
                val tip = Offset(center.x + radius * cos(sweep), center.y + radius * sin(sweep))
                val glow = stroke * (1.6f + 0.5f * sin(pulse * 2f * PI.toFloat()))
                drawCircle(
                    brush = Brush.radialGradient(listOf(glowColor.copy(alpha = 0.55f), glowColor.copy(alpha = 0f)), center = tip, radius = glow),
                    radius = glow,
                    center = tip,
                )
                drawCircle(Color.White.copy(alpha = 0.92f), radius = stroke * 0.26f, center = tip)
            }
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
