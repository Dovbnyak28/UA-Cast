package com.uacastplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Design spec calls for the Inter family; no font files are bundled under res/font yet, so these
// tokens fall back to the platform default family until Inter is added.
private const val TabularNums = "tnum"

val LargeTitle = TextStyle(
    fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em,
    fontFeatureSettings = TabularNums,
)
val Title = TextStyle(
    fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em,
    fontFeatureSettings = TabularNums,
)
val CardTitle = TextStyle(
    fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em,
    fontFeatureSettings = TabularNums,
)
val BodyText = TextStyle(
    fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em,
    fontFeatureSettings = TabularNums,
)
val BodyRegular = TextStyle(
    fontSize = 13.5.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.em,
    fontFeatureSettings = TabularNums,
)
val Caption = TextStyle(
    fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.em,
    fontFeatureSettings = TabularNums,
)
val CaptionSemibold = TextStyle(
    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.em,
    fontFeatureSettings = TabularNums,
)
val Micro = TextStyle(
    fontSize = 11.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.em,
    fontFeatureSettings = TabularNums,
)
val SectionLabel = TextStyle(
    fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.16.em,
    fontFeatureSettings = TabularNums,
)
val ButtonLabel = TextStyle(
    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em,
    fontFeatureSettings = TabularNums,
)
val PillText = TextStyle(
    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.04.em,
    fontFeatureSettings = TabularNums,
)
val LiveText = TextStyle(
    fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.12.em,
    fontFeatureSettings = TabularNums,
)
val RingValue = TextStyle(
    fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em,
    fontFeatureSettings = TabularNums,
)
val TabLabel = TextStyle(
    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.em,
    fontFeatureSettings = TabularNums,
)

/**
 * [LargeTitle] with the active theme's [UaPalette.displayFontFamily] - serif in Cinema, unchanged
 * elsewhere. Use for big screen titles (nav bar screen title, Home's app-name title) - see
 * docs/DESIGN_SYSTEM.md "Themes".
 */
val DisplayTitle: TextStyle
    @Composable get() = LargeTitle.copy(fontFamily = UaTheme.palette.displayFontFamily)

/**
 * [CardTitle] with the active theme's [UaPalette.displayFontFamily]. Use for channel names at
 * CardTitle scale (the player's now-playing name). Channel/group names that use [BodyText] scale
 * instead (ChannelRow, group cards, the Home "continue watching" card) apply the same
 * `.copy(fontFamily = UaTheme.palette.displayFontFamily)` directly on [BodyText] at the call site,
 * rather than through a third named style - see docs/DESIGN_SYSTEM.md "Themes".
 */
val DisplayName: TextStyle
    @Composable get() = CardTitle.copy(fontFamily = UaTheme.palette.displayFontFamily)

val AppTypography = Typography(
    displayLarge = LargeTitle,
    headlineLarge = Title,
    titleLarge = CardTitle,
    titleMedium = CardTitle,
    bodyLarge = BodyText,
    bodyMedium = BodyRegular,
    bodySmall = Caption,
    // labelLarge is what Material3's own Button/OutlinedButton/TextButton use for their text by
    // default (every plain Button(onClick = ...) { Text(...) } with no explicit style, e.g. the
    // "Додати плейлист"/"Завантажити та зберегти"/onboarding "Продовжити" buttons) - it was
    // SectionLabel (9.5sp, meant for small uppercase section headers) until a user report that
    // button text was unreadably small confirmed it. labelMedium/labelSmall are meant to be
    // smaller than labelLarge; SectionLabel was actually smaller than both, which was itself a
    // sign this mapping was a mistake rather than a deliberate small-button-text design choice.
    labelLarge = ButtonLabel,
    labelMedium = CaptionSemibold,
    labelSmall = Micro,
)
