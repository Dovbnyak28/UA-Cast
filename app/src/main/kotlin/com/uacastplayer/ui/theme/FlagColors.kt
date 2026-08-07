package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The national flag colours drawn by `ui/language/LanguageFlag`.
 *
 * These live under `ui/theme/` because `scripts/check-no-hardcoded-colors.sh` allows colour
 * literals only here - but they are **not** part of [UaPalette] and deliberately do not vary with
 * the theme. A flag is a fixed specification: Ukraine's blue is the same blue on Azure, Cinema and
 * Midnight, and a "theme-aware" flag would simply be the wrong flag. Everything else a screen draws
 * still has to come from `UaTheme.palette` - this is the one class of colour that is content rather
 * than styling.
 */
internal object FlagColors {
    // Ukraine
    val UaBlue = Color(0xFF0057B7)
    val UaYellow = Color(0xFFFFD700)

    // United Kingdom
    val GbBlue = Color(0xFF012169)
    val GbRed = Color(0xFFC8102E)
    val GbWhite = Color(0xFFFFFFFF)

    // Russia
    val RuWhite = Color(0xFFFFFFFF)
    val RuBlue = Color(0xFF0039A6)
    val RuRed = Color(0xFFD52B1E)

    // Spain
    val EsRed = Color(0xFFAA151B)
    val EsYellow = Color(0xFFF1BF00)
}
