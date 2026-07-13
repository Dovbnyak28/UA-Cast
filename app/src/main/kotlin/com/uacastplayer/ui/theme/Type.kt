package com.uacastplayer.ui.theme

import androidx.compose.material3.Typography
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
    fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.em,
    fontFeatureSettings = TabularNums,
)

val AppTypography = Typography(
    displayLarge = LargeTitle,
    headlineLarge = Title,
    titleLarge = CardTitle,
    titleMedium = CardTitle,
    bodyLarge = BodyText,
    bodyMedium = BodyRegular,
    bodySmall = Caption,
    labelLarge = SectionLabel,
    labelMedium = CaptionSemibold,
    labelSmall = Micro,
)
