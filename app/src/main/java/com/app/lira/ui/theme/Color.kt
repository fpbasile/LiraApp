package com.app.lira.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Lira design tokens (see design/reference/Tokens.dc.html).
 * Black-and-white at a glance; colour is the reward. Dark only.
 */
object LiraColors {
    // Base & surfaces — depth comes from surface steps, no shadows, no blur.
    val Base = Color(0xFF0B0B0C)
    val Surface1 = Color(0xFF141416)
    val Surface2 = Color(0xFF1C1C1F)
    val Surface3 = Color(0xFF26262A)
    val Surface4 = Color(0xFF323237)
    val Line = Color(0x14FFFFFF) // rgba(255,255,255,0.08)
    val Track = Color(0x1AFFFFFF) // ring / bar tracks, white 10%
    val Scrim = Color(0x9E000000) // 62% black behind sheets

    // Text — solid values so contrast is predictable.
    val TextPrimary = Color(0xFFF5F5F4)
    val TextSecondary = Color(0xFFA8A8AD)
    val TextTertiary = Color(0xFF85858B)
    val TextDisabled = Color(0xFF4E4E54)

    // Accent & signal
    val Lichen = Color(0xFF5D926D) // positive moments only
    val Cedar = Color(0xFF325E45) // corner glow only, never text
    val OverLimit = Color(0xFFFF5A5F) // only when a limit is actually crossed
    val OverLimitTint = Color(0x24FF5A5F) // 14%
}

/** Muted category palette — always paired with an icon and a label. */
object CategoryPalette {
    val Groceries = Color(0xFFE9C46A)
    val EatingOut = Color(0xFFCB7A5D)
    val Coffee = Color(0xFF937354)
    val Transport = Color(0xFF6DA3DA)
    val Shopping = Color(0xFFB09DE9)
    val HomeBills = Color(0xFF6AC9CE)
    val Health = Color(0xFFD48AAD)
    val Fun = Color(0xFFF0BFEA)
    val Stays = Color(0xFF7C88C3)
    val Other = Color(0xFF9E9EA4)

    val all = listOf(Groceries, EatingOut, Coffee, Transport, Shopping, HomeBills, Health, Fun, Stays, Other)
}

// ---- Legacy aliases, kept so older code keeps compiling while screens migrate. ----
val LiraBackground = LiraColors.Base
val BorderGray = LiraColors.Surface3
val TextWhite = LiraColors.TextPrimary
val LiraTextPrimary = LiraColors.TextPrimary
val LiraTextSecondary = LiraColors.TextSecondary
val LiraBorder = LiraColors.Surface2
val LiraProgressBarTrack = LiraColors.Track
val LiraProgressBarIndicator = LiraColors.Lichen
val LiraProgressTextBackground = LiraColors.Surface2
