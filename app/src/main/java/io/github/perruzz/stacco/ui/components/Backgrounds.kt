package io.github.perruzz.stacco.ui.components

import androidx.compose.runtime.remember
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One full turn of the aurora. */
private const val AURORA_PERIOD_MS = 26_000L

/**
 * A slow, breathing aurora of soft green light behind the content. Its time is only read in the
 * draw phase, so it never recomposes; it moves 20 times a second (see [rememberAmbientPhase]).
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val colors = StaccoTheme.colors
    val base = MaterialTheme.colorScheme.background
    val phase = rememberAmbientPhase(AURORA_PERIOD_MS)
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        val w = size.width
        val h = size.height
        val a = phase.value * 2f * PI.toFloat()
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
