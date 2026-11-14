package io.github.perruzz.stacco.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A slow, breathing aurora of soft green light behind the content.
 * Animated values are only read in the draw phase, so it never recomposes.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val colors = StaccoTheme.colors
    val base = MaterialTheme.colorScheme.background
    val transition = rememberInfiniteTransition(label = "aurora")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing)),
        label = "aurora-t",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        val w = size.width
        val h = size.height
        val a = t * 2f * PI.toFloat()
        blob(colors.auroraA.copy(alpha = 0.42f * intensity), w * (0.18f + 0.14f * cos(a)), h * (0.10f + 0.05f * sin(2 * a)), w * 0.95f)
        blob(colors.auroraB.copy(alpha = 0.30f * intensity), w * (0.88f + 0.10f * sin(a)), h * (0.26f + 0.07f * cos(a)), w * 0.80f)
        blob(colors.auroraC.copy(alpha = 0.26f * intensity), w * (0.50f + 0.22f * cos(a + 1.3f)), h * (0.02f + 0.05f * sin(a + 0.7f)), w * 0.70f)
        blob(colors.auroraA.copy(alpha = 0.16f * intensity), w * (0.30f + 0.20f * sin(a + 2.1f)), h * (0.92f + 0.04f * cos(a)), w * 0.90f)
    }
}

private fun DrawScope.blob(color: Color, x: Float, y: Float, radius: Float) {
    val center = Offset(x, y)
    drawCircle(
        brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}
