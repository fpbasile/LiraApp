package com.app.lira.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LiraColorScheme = darkColorScheme(
    primary = LiraColors.TextPrimary,
    onPrimary = LiraColors.Base,
    secondary = LiraColors.TextSecondary,
    onSecondary = LiraColors.Base,
    tertiary = LiraColors.Lichen,
    background = LiraColors.Base,
    onBackground = LiraColors.TextPrimary,
    surface = LiraColors.Surface1,
    onSurface = LiraColors.TextPrimary,
    surfaceVariant = LiraColors.Surface2,
    onSurfaceVariant = LiraColors.TextSecondary,
    surfaceContainer = LiraColors.Surface1,
    surfaceContainerLow = LiraColors.Surface1,
    surfaceContainerHigh = LiraColors.Surface3,
    surfaceContainerHighest = LiraColors.Surface3,
    outline = LiraColors.TextDisabled,
    outlineVariant = LiraColors.Line,
    error = LiraColors.OverLimit,
    scrim = LiraColors.Scrim
)

private val LiraTypography = Typography(
    bodyLarge = LiraType.BodyL,
    bodyMedium = LiraType.BodyM,
    bodySmall = LiraType.BodyS,
    labelLarge = LiraType.Label,
    titleLarge = LiraType.TitleM,
    headlineMedium = LiraType.TitleL
)

/** Motion tokens (Motion board). */
object LiraMotion {
    val Standard = FastOutSlowInEasing
    val Emphasized = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    val Settle = CubicBezierEasing(0.33f, 1f, 0.68f, 1f) // EaseOutCubic
    const val MICRO = 120
    const val STANDARD = 220
    const val UNDO_WINDOW_MS = 4000L
}

@Composable
fun LiraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LiraColorScheme, typography = LiraTypography, content = content)
}
