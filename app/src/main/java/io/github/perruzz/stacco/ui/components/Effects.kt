package io.github.perruzz.stacco.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A badge whose shape keeps slowly morphing (star → hexagon → flower → circle …)
 * like a living pebble. Used for heroes and empty states.
 */
@Composable
fun MorphBadge(
    icon: ImageVector?,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    iconTint: Color = Color.White,
    periodMillis: Int = 2600,
) {
    val shapes = remember {
        listOf(
            RoundedPolygon.star(numVerticesPerRadius = 9, innerRadius = 0.84f, rounding = CornerRounding(0.32f)).normalized(),
            RoundedPolygon(numVertices = 6, rounding = CornerRounding(0.42f)).normalized(),
            RoundedPolygon.star(numVerticesPerRadius = 5, innerRadius = 0.72f, rounding = CornerRounding(0.4f)).normalized(),
            RoundedPolygon.circle(numVertices = 12).normalized(),
            RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.78f, rounding = CornerRounding(0.5f)).normalized(),
        )
    }
    val morphs = remember { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val transition = rememberInfiniteTransition(label = "morph")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = morphs.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(morphs.size * periodMillis, easing = LinearEasing)),
        label = "morph-phase",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(28_000, easing = LinearEasing)),
        label = "morph-rotation",
    )
    val androidPath = remember { android.graphics.Path() }
    val matrix = remember { android.graphics.Matrix() }

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val index = phase.toInt().coerceIn(0, morphs.lastIndex)
                val local = FastOutSlowInEasing.transform((phase - index).coerceIn(0f, 1f))
                androidPath.rewind()
                morphs[index].toPath(local, androidPath)
                matrix.setScale(this.size.width, this.size.height)
                androidPath.transform(matrix)
                rotate(rotation) {
                    drawPath(
                        path = androidPath.asComposePath(),
                        brush = Brush.linearGradient(colors, start = Offset.Zero, end = Offset(this.size.width, this.size.height)),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(size * 0.42f))
        }
    }
}

private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    val spin: Float,
    val color: Color,
    val w: Float,
    val h: Float,
    val wobble: Float,
)

/**
 * A short, joyful burst of confetti. Changing [trigger] fires a new burst.
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier, origin: Offset? = null) {
    if (trigger <= 0) return
    val palette = listOf(
        StaccoTheme.colors.gold,
        MaterialTheme.colorScheme.primary,
        StaccoTheme.colors.positive,
        MaterialTheme.colorScheme.tertiary,
        StaccoTheme.colors.auroraA,
        Color.White,
    )
    var particles by remember(trigger) { mutableStateOf<List<Particle>>(emptyList()) }
    var frame by remember(trigger) { mutableLongStateOf(0L) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(trigger, canvasSize) {
        if (canvasSize == Size.Zero) return@LaunchedEffect
        val random = Random(trigger)
        val start = origin ?: Offset(canvasSize.width / 2f, canvasSize.height * 0.32f)
        particles = List(120) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val speed = 600f + random.nextFloat() * 1300f
            Particle(
                x = start.x, y = start.y,
                vx = cos(angle) * speed, vy = sin(angle) * speed - 700f,
                rotation = random.nextFloat() * 360f,
                spin = (random.nextFloat() - 0.5f) * 900f,
                color = palette[random.nextInt(palette.size)],
                w = 10f + random.nextFloat() * 14f,
                h = 6f + random.nextFloat() * 8f,
                wobble = random.nextFloat() * 6f,
            )
        }
        var last = 0L
        val begin = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            particles.forEach { p ->
                p.vy += 1500f * dt
                p.vx *= (1f - 1.6f * dt)
                p.vy *= (1f - 0.9f * dt)
                p.x += (p.vx + sin(p.rotation / 30f) * p.wobble * 20f) * dt
                p.y += p.vy * dt
                p.rotation += p.spin * dt
            }
            frame = now
            if ((now - begin) / 1e9f > 3.2f) break
        }
        particles = emptyList()
    }

    Canvas(modifier.fillMaxSize().onSizeChanged { canvasSize = it.toSize() }) {
        if (frame < 0L) return@Canvas // reading the frame clock keeps the canvas redrawing
        particles.forEach { p ->
            if (p.y < size.height + 40f) {
                rotate(p.rotation, Offset(p.x, p.y)) {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(p.x - p.w / 2f, p.y - p.h / 2f),
                        size = Size(p.w, p.h * (0.4f + 0.6f * kotlin.math.abs(cos(p.rotation / 40f)))),
                    )
                }
            }
        }
    }
}
