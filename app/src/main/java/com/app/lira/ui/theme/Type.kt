package com.app.lira.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.app.lira.R

/** Inter carries the interface. */
val InterFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_bold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/**
 * Numbers font: Space Grotesk (variable, SIL OFL 1.1 — res/font/space_grotesk.ttf) carries every
 * amount. Weights are set through the wght axis.
 */
@OptIn(ExperimentalTextApi::class)
private fun spaceGrotesk(weight: Int) = Font(
    R.font.space_grotesk,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val NumberFont: FontFamily = FontFamily(
    spaceGrotesk(400),
    spaceGrotesk(500),
    spaceGrotesk(600),
    spaceGrotesk(700)
)

private const val TNUM = "tnum"

/** Lira type scale (Tokens board). Amounts: numbers font, tabular figures. */
object LiraType {
    private fun amount(size: Int, line: Double, tracking: Double) = TextStyle(
        fontFamily = NumberFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = size.sp,
        lineHeight = (size * line).sp,
        letterSpacing = tracking.em,
        fontFeatureSettings = TNUM,
        color = LiraColors.TextPrimary
    )

    val AmountHero = amount(88, 1.0, -0.035)
    val AmountXl = amount(48, 1.05, -0.03)
    val AmountL = amount(40, 1.1, -0.02)
    val AmountM = amount(28, 1.15, -0.01)
    val AmountS = amount(22, 1.2, -0.01)
    val AmountXs = amount(16, 1.3, 0.0)
    val Keypad = TextStyle(
        fontFamily = NumberFont, fontWeight = FontWeight.Medium, fontSize = 30.sp,
        lineHeight = 30.sp, fontFeatureSettings = TNUM, color = LiraColors.TextPrimary
    )

    private fun ui(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0, color: androidx.compose.ui.graphics.Color = LiraColors.TextPrimary) =
        TextStyle(
            fontFamily = InterFont, fontWeight = weight, fontSize = size.sp,
            lineHeight = line.sp, letterSpacing = tracking.em, color = color
        )

    val TitleL = ui(28, 34, FontWeight.SemiBold, -0.01)
    val TitleM = ui(20, 26, FontWeight.SemiBold, -0.01)
    val BodyL = ui(16, 24, FontWeight.Normal)
    val BodyM = ui(15, 22, FontWeight.Normal)
    val BodyS = ui(13, 18, FontWeight.Normal, color = LiraColors.TextSecondary)
    val Label = ui(13, 16, FontWeight.Medium)
    val Caption = ui(11, 14, FontWeight.SemiBold, 0.08, LiraColors.TextTertiary) // render uppercase

    /** Quick-add amount steps down instead of wrapping: ≤5 chars 88, ≤7 72, ≤9 58, else 46. */
    fun heroSizeFor(chars: Int) = when {
        chars <= 5 -> 88
        chars <= 7 -> 72
        chars <= 9 -> 58
        chars <= 12 -> 46
        else -> 38
    }
}
