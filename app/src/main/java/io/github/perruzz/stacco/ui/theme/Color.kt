package io.github.perruzz.stacco.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// A calm, sage/emerald palette: green is the colour of rest.

val LightColors = lightColorScheme(
    primary = Color(0xFF1F6E52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA9F0CE),
    onPrimaryContainer = Color(0xFF002116),
    inversePrimary = Color(0xFF8BD6B3),
    secondary = Color(0xFF4C6358),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFE9F8),
    onTertiaryContainer = Color(0xFF001F27),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FAF5),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FAF5),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404943),
    surfaceTint = Color(0xFF1F6E52),
    inverseSurface = Color(0xFF2C322E),
    inverseOnSurface = Color(0xFFEDF2EC),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF5FAF5),
    surfaceDim = Color(0xFFD6DBD6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F0),
    surfaceContainer = Color(0xFFE9EFEA),
    surfaceContainerHigh = Color(0xFFE3E9E4),
    surfaceContainerHighest = Color(0xFFDEE4DF),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD6B3),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFA9F0CE),
    inversePrimary = Color(0xFF1F6E52),
    secondary = Color(0xFFB3CCBF),
    onSecondary = Color(0xFF1E352B),
    secondaryContainer = Color(0xFF354B41),
    onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFA3CDDC),
    onTertiary = Color(0xFF033541),
    tertiaryContainer = Color(0xFF224C58),
    onTertiaryContainer = Color(0xFFBFE9F8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1411),
    onBackground = Color(0xFFDEE4DF),
    surface = Color(0xFF0E1411),
    onSurface = Color(0xFFDEE4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceTint = Color(0xFF8BD6B3),
    inverseSurface = Color(0xFFDEE4DF),
    inverseOnSurface = Color(0xFF2C322E),
    outline = Color(0xFF89938C),
    outlineVariant = Color(0xFF404943),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343B37),
    surfaceDim = Color(0xFF0E1411),
    surfaceContainerLowest = Color(0xFF090F0C),
    surfaceContainerLow = Color(0xFF161D19),
    surfaceContainer = Color(0xFF1A211D),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF2F3632),
)

/** Semantic colours that Material does not provide. */
@Immutable
data class StaccoColors(
    val positive: Color,
    val negative: Color,
    val office: Color,
    val officeContainer: Color,
    val smart: Color,
    val smartContainer: Color,
    val travel: Color,
    val travelContainer: Color,
    val vacation: Color,
    val vacationContainer: Color,
    val sick: Color,
    val sickContainer: Color,
    val holiday: Color,
    val holidayContainer: Color,
    /** On-call duty: its calendar badge, timer and recovery hours. */
    val onCall: Color,
    val onCallContainer: Color,
    val lunch: Color,
    val gold: Color,
    val auroraA: Color,
    val auroraB: Color,
    val auroraC: Color,
)

val LightStaccoColors = StaccoColors(
    positive = Color(0xFF1B8A58),
    negative = Color(0xFFC4413A),
    office = Color(0xFF2A8460),
    officeContainer = Color(0xFFCDEFDB),
    smart = Color(0xFF3A77A8),
    smartContainer = Color(0xFFD5E7F8),
    travel = Color(0xFF6F8B13),
    travelContainer = Color(0xFFE6F1C0),
    vacation = Color(0xFFB97A1E),
    vacationContainer = Color(0xFFF9E4C4),
    sick = Color(0xFFB4566A),
    sickContainer = Color(0xFFF7DCE2),
    holiday = Color(0xFF7562B0),
    holidayContainer = Color(0xFFE6E0F8),
    onCall = Color(0xFF0E7C8C),
    onCallContainer = Color(0xFFCDEEF2),
    lunch = Color(0xFFC8954C),
    gold = Color(0xFFE9B53F),
    auroraA = Color(0xFF7FD6AA),
    auroraB = Color(0xFF9BD8E8),
    auroraC = Color(0xFFD9F2C4),
)

val DarkStaccoColors = StaccoColors(
    positive = Color(0xFF7FD9A8),
    negative = Color(0xFFFF9A8F),
    office = Color(0xFF7FD3A6),
    officeContainer = Color(0xFF1C4A36),
    smart = Color(0xFF9CC7EE),
    smartContainer = Color(0xFF1D3B56),
    travel = Color(0xFFC6DD7A),
    travelContainer = Color(0xFF3B4A10),
    vacation = Color(0xFFF3C27E),
    vacationContainer = Color(0xFF543B12),
    sick = Color(0xFFF1A7B6),
    sickContainer = Color(0xFF55232F),
    holiday = Color(0xFFC5B8F3),
    holidayContainer = Color(0xFF393062),
    onCall = Color(0xFF7FD4E2),
    onCallContainer = Color(0xFF13434B),
    lunch = Color(0xFFE7C38C),
    gold = Color(0xFFF2C75C),
    auroraA = Color(0xFF1F7A55),
    auroraB = Color(0xFF1C5E70),
    auroraC = Color(0xFF3F6B2E),
)

val LocalStaccoColors = staticCompositionLocalOf { LightStaccoColors }

/** Colours a shift can take (light, dark). Index is stored in [io.github.perruzz.stacco.domain.ShiftTemplate.color]. */
val ShiftColorsLight = listOf(
    Color(0xFFC98A1C), // sunrise amber
    Color(0xFFD0603C), // afternoon coral
    Color(0xFF4A5AA8), // night indigo
    Color(0xFF2A8460), // green
    Color(0xFF13879A), // teal
    Color(0xFFB4566A), // rose
)
val ShiftColorsDark = listOf(
    Color(0xFFF3C46B),
    Color(0xFFFFA387),
    Color(0xFFAEB9FF),
    Color(0xFF7FD3A6),
    Color(0xFF7FD4E2),
    Color(0xFFF1A7B6),
)
