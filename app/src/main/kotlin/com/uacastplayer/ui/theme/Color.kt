package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Backgrounds
val Void = Color(0xFF080D18)
val VoidElevated = Color(0xFF11192A)
val Surface1 = Color(0xFF162138)
val Surface2 = Color(0xFF202E49)

// Accent
val Azure = Color(0xFF679BFF)
val Azure2 = Color(0xFF42D9CF)
val AzureGradient = Brush.linearGradient(listOf(Azure, Azure2))

// Route health semantics
val RouteGreen = Color(0xFF30D158)
val RouteAmber = Color(0xFFFFD60A)
val RouteRed = Color(0xFFFF453A)

// Text
val LabelPrimary = Color(0xFFF5F5F7)
val LabelSecondary = Color(0xFFB9C5DA)
val LabelTertiary = Color(0xFF8F9DB4)

// Lines
val Hairline = Color(0x263E62A3)

// Glow
val AzureGlow = Color(0x80679BFF)
val GreenGlow = Color(0x9930D158)
val AmberGlow = Color(0x80FFD60A)
val RedGlow = Color(0x99FF453A)

// Depth (raised/sunken surface edges + shadow) - see ui/theme/Depth.kt
val EdgeHighlightNeutral = Color(0x2A8DA7D8)
val EdgeHighlightStrong = Color(0x4D9DBAFF)
val EdgeHighlightAccent = Color(0x80679BFF)
val ShadowSoft = Color(0x66000000)
