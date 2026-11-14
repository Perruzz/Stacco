package io.github.perruzz.stacco.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.perruzz.stacco.R

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Geometric, friendly display face: great for big clock digits. */
val Outfit = FontFamily(
    variable(R.font.outfit, 300),
    variable(R.font.outfit, 400),
    variable(R.font.outfit, 500),
    variable(R.font.outfit, 600),
    variable(R.font.outfit, 700),
)

/** Highly legible text face. */
val Manrope = FontFamily(
    variable(R.font.manrope, 400),
    variable(R.font.manrope, 500),
    variable(R.font.manrope, 600),
    variable(R.font.manrope, 700),
    variable(R.font.manrope, 800),
)

/** Tabular figures so that ticking clocks don't wobble. */
const val TABULAR = "tnum"

private fun display(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Outfit,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = TABULAR,
)

private fun text(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

val StaccoTypography = Typography(
    displayLarge = display(64, 68, 600, -0.03),
    displayMedium = display(48, 54, 600, -0.02),
    displaySmall = display(36, 42, 500, -0.01),
    headlineLarge = display(32, 38, 600, -0.01),
    headlineMedium = display(26, 32, 600),
    headlineSmall = display(22, 28, 500),
    titleLarge = display(20, 26, 600),
    titleMedium = text(16, 22, 700),
    titleSmall = text(14, 20, 700),
    bodyLarge = text(16, 24, 500),
    bodyMedium = text(14, 20, 500),
    bodySmall = text(12, 16, 500),
    labelLarge = text(14, 20, 700, 0.01),
    labelMedium = text(12, 16, 700, 0.02),
    labelSmall = text(11, 16, 700, 0.04),
)
