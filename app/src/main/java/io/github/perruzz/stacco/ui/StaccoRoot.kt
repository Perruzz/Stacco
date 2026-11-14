package io.github.perruzz.stacco.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import io.github.perruzz.stacco.domain.StaccoData
import io.github.perruzz.stacco.domain.ThemeMode
import io.github.perruzz.stacco.ui.onboarding.OnboardingScreen
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/**
 * Switches theme with a circular reveal that grows from the tapped button:
 * the old UI is frozen in a bitmap and "wiped away" to uncover the new one.
 */
@Stable
class ThemeSwitcher internal constructor(
    private val scope: CoroutineScope,
    private val layer: GraphicsLayer,
    private val apply: (ThemeMode) -> Unit,
) {
    var snapshot by mutableStateOf<ImageBitmap?>(null)
        private set
    var center by mutableStateOf(Offset.Unspecified)
        private set
    val progress = Animatable(0f)

    fun switch(mode: ThemeMode, origin: Offset = Offset.Unspecified) {
        if (snapshot != null) return
        scope.launch {
            val bitmap = runCatching { layer.toImageBitmap() }.getOrNull()
            if (bitmap == null) {
                apply(mode)
                return@launch
            }
            center = origin
            snapshot = bitmap
            progress.snapTo(0f)
            apply(mode)
            // Let the new theme compose before starting the wipe.
            withFrameNanos { }
            withFrameNanos { }
            progress.animateTo(1f, tween(760, easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)))
            snapshot = null
        }
    }
}

val LocalThemeSwitcher = staticCompositionLocalOf<ThemeSwitcher?> { null }

@Composable
fun StaccoRoot(data: StaccoData, viewModel: MainViewModel, systemDark: Boolean) {
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var themeOverride by remember { mutableStateOf<ThemeMode?>(null) }
    val mode = themeOverride ?: data.appearance.theme
    LaunchedEffect(data.appearance.theme) {
        if (themeOverride == data.appearance.theme) themeOverride = null
    }
    val switcher = remember {
        ThemeSwitcher(scope, layer) { newMode ->
            themeOverride = newMode
            viewModel.updateData { it.copy(appearance = it.appearance.copy(theme = newMode)) }
        }
    }
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    CompositionLocalProvider(LocalThemeSwitcher provides switcher) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                    val snap = switcher.snapshot
                    if (snap != null) {
                        val c = if (switcher.center.isSpecified) switcher.center else Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = hypot(max(c.x, size.width - c.x), max(c.y, size.height - c.y))
                        val radius = maxRadius * switcher.progress.value
                        val hole = Path().apply { addOval(Rect(c, radius)) }
                        clipPath(hole, ClipOp.Difference) { drawImage(snap) }
                    }
                },
        ) {
            StaccoTheme(darkTheme = dark, dynamicColor = data.appearance.dynamicColor) {
                AnimatedContent(
                    targetState = data.onboardingDone,
                    transitionSpec = {
                        (fadeIn(tween(700, delayMillis = 150)) + scaleIn(tween(900), initialScale = 1.06f)) togetherWith
                            (fadeOut(tween(350)) + scaleOut(tween(500), targetScale = 0.94f))
                    },
                    label = "root",
                ) { done ->
                    if (done) MainShell(viewModel) else OnboardingScreen(viewModel)
                }
            }
        }
    }
}
