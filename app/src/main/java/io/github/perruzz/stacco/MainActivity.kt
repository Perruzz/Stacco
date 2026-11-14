package io.github.perruzz.stacco

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.perruzz.stacco.domain.ThemeMode
import io.github.perruzz.stacco.ui.MainViewModel
import io.github.perruzz.stacco.ui.StaccoRoot

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        splash.setKeepOnScreenCondition { viewModel.data.value == null }
        splash.setOnExitAnimationListener { provider ->
            val view = provider.view
            val icon: View? = runCatching { provider.iconView }.getOrNull()
            val fade = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f).apply { duration = 420 }
            val set = AnimatorSet()
            if (icon != null) {
                val sx = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.35f)
                val sy = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.35f)
                listOf(sx, sy).forEach { it.duration = 420; it.interpolator = AnticipateInterpolator(1.2f) }
                set.playTogether(fade, sx, sy)
            } else {
                set.play(fade)
            }
            set.doOnEnd { provider.remove() }
            set.start()
        }

        // Only on a fresh start: a recreated activity (e.g. rotation) gets the same intent again.
        if (savedInstanceState == null) handleShortcut(intent)

        setContent {
            val data by viewModel.data.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (data?.appearance?.theme ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            data?.let { StaccoRoot(data = it, viewModel = viewModel, systemDark = systemDark) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
    }

    /** Launcher shortcut "Stamp now". */
    private fun handleShortcut(intent: Intent?) {
        if (intent?.action == ACTION_STAMP) {
            viewModel.stampNow()
            intent.action = null
        }
    }

    companion object {
        const val ACTION_STAMP = "io.github.perruzz.stacco.action.STAMP"
        const val EXTRA_TAB = "tab"
    }
}
