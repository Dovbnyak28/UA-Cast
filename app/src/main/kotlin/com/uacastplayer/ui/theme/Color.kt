package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Backgrounds
val Void = Color(0xFF0A0A0C)
val VoidElevated = Color(0xFF101012) // ~2.5% brighter than Void - see appBackground()
val Surface1 = Color(0xFF1C1C1E)
val Surface2 = Color(0xFF2C2C2E)

// Accent
val Azure = Color(0xFF0A84FF)
val Azure2 = Color(0xFF64D2FF)
val AzureGradient = Brush.linearGradient(listOf(Azure, Azure2))

// Route health semantics
val RouteGreen = Color(0xFF30D158)
val RouteAmber = Color(0xFFFFD60A)
val RouteRed = Color(0xFFFF453A)

// Text
val LabelPrimary = Color(0xFFF5F5F7)
val LabelSecondary = Color(0x99EBEBF5)
val LabelTertiary = Color(0x4DEBEBF5)

// Lines
val Hairline = Color(0x14FFFFFF)

// Glow
val AzureGlow = Color(0x800A84FF)
val GreenGlow = Color(0x9930D158)
val AmberGlow = Color(0x80FFD60A)
val RedGlow = Color(0x99FF453A)

// Depth (raised/sunken surface edges + shadow) - see ui/theme/Depth.kt
val EdgeHighlightNeutral = Color(0x1FFFFFFF)
val EdgeHighlightStrong = Color(0x33FFFFFF)
val EdgeHighlightAccent = Color(0x730A84FF)
val ShadowSoft = Color(0x4D000000)
