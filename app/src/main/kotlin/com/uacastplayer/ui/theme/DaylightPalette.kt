package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Plain, high-contrast phone chrome. Video surfaces retain the dark player palette. */
val DaylightUaPalette = AzureUaPalette.copy(
    void = Color(0xFFF4F6FA), voidElevated = Color.White,
    surface1 = Color.White, surface2 = Color(0xFFE8EDF5),
    azure = Color(0xFF1855B5), azure2 = Color(0xFF1855B5),
    accentGradientTop = Color(0xFF1855B5), accentGradientBottom = Color(0xFF16499C),
    accentGradient = Brush.verticalGradient(listOf(Color(0xFF1855B5), Color(0xFF16499C))),
    accentOnFill = Color.White, accentText = Color(0xFF174EAB),
    labelPrimary = Color(0xFF172238), labelSecondary = Color(0xFF43536D), labelTertiary = Color(0xFF53627A),
    hairline = Color(0xFFCED6E2), glassTone = Color(0xF5FFFFFF),
    routeGreen = Color(0xFF176039), routeAmber = Color(0xFF785000), routeRed = Color(0xFFAE2538),
    edgeHighlightNeutral = Color(0xFFE0E5ED), edgeHighlightStrong = Color(0xFFBCCAE0),
    edgeHighlightAccent = Color(0xFF3068C0), shadowSoft = Color(0x140C2144),
    surfaceLiftAmount = 0f, vignette = false, wallpaperTexture = false,
    secondaryButtonStyle = SecondaryButtonStyle.GHOST, isDark = false,
)
