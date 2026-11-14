package io.github.perruzz.stacco.ui.theme

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.perruzz.stacco.domain.DayType

val StaccoShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun StaccoTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val extra = if (darkTheme) DarkStaccoColors else LightStaccoColors
    CompositionLocalProvider(LocalStaccoColors provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = StaccoTypography,
            shapes = StaccoShapes,
        ) {
            // Text drawn outside of a Surface must follow the theme too (the default is black).
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}

object StaccoTheme {
    val colors: StaccoColors
        @Composable @ReadOnlyComposable get() = LocalStaccoColors.current
}

/** Whether the light or the dark palette is in use (read from the semantic colours). */
private val StaccoColors.isDark: Boolean get() = this === DarkStaccoColors

@Composable
@ReadOnlyComposable
fun shiftColor(index: Int): Color {
    val palette = if (StaccoTheme.colors.isDark) ShiftColorsDark else ShiftColorsLight
    return palette[Math.floorMod(index, palette.size)]
}

@Composable
@ReadOnlyComposable
fun shiftContainer(index: Int): Color = shiftColor(index).copy(alpha = if (StaccoTheme.colors.isDark) 0.24f else 0.18f)

/** Springs used across the app: everything moves like it has a little weight. */
object Motion {
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 320f)
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 420f)
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow * 2)
    fun <T> slow(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 90f)
}

@Composable
@ReadOnlyComposable
fun DayType?.tint(): Color {
    val c = StaccoTheme.colors
    return when (this) {
        DayType.OFFICE -> c.office
        DayType.SMART -> c.smart
        DayType.TRAVEL -> c.travel
        DayType.VACATION -> c.vacation
        DayType.SICK -> c.sick
        DayType.HOLIDAY -> c.holiday
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
@ReadOnlyComposable
fun DayType?.container(): Color {
    val c = StaccoTheme.colors
    return when (this) {
        DayType.OFFICE -> c.officeContainer
        DayType.SMART -> c.smartContainer
        DayType.TRAVEL -> c.travelContainer
        DayType.VACATION -> c.vacationContainer
        DayType.SICK -> c.sickContainer
        DayType.HOLIDAY -> c.holidayContainer
        null -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
}

@Composable
@ReadOnlyComposable
fun deltaColor(minutes: Int): Color = when {
    minutes > 0 -> StaccoTheme.colors.positive
    minutes < 0 -> StaccoTheme.colors.negative
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
