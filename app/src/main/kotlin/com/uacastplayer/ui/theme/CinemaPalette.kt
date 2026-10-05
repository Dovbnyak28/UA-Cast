package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// Backgrounds - deep aubergine ink instead of flat charcoal; still dark enough for late-night viewing.
private val CinemaVoid = Color(0xFF100D18)
private val CinemaVoidElevated = Color(0xFF191322)
private val CinemaSurface1 = Color(0xFF211A2D)
private val CinemaSurface2 = Color(0xFF30243C)

// Accent - brighter champagne melting into coral, for an unmistakable cinematic identity.
private val CinemaGradientTop = Color(0xFFFFD777)
private val CinemaGradientBottom = Color(0xFFFF9878)
// The solid accent hue is bright enough for chrome; small text uses the near-white accentText.
private val CinemaAccent = Color(0xFFFFCF70)

// Text - warm ivory/beige instead of Azure's cool white/gray.
private val CinemaLabelPrimary = Color(0xFFF2EDE2)
private val CinemaLabelSecondary = Color(0xFFC8BDAE)
private val CinemaLabelTertiary = Color(0xFFA08F7C)

// Route health - danger stays warm/muted; good/warn are left as the universal Azure values since
// route-health color meaning should stay recognizable across themes.
private val CinemaDanger = Color(0xFFC9695A)

// Depth - ivory/gold-tinted instead of Azure's neutral white, so raised edges read as warm chrome
// rather than a cool mismatch against the charcoal background. See ui/theme/Depth.kt.
private val CinemaEdgeHighlightNeutral = Color(0x35F8D9A0)
private val CinemaEdgeHighlightStrong = Color(0x59FFE1A3)
private val CinemaEdgeHighlightAccent = Color(0x99FFCF70)
private val CinemaShadowSoft = Color(0x88000000)

/**
 * Warm, "premium cinema" palette: aubergine-ink background, champagne-to-coral accent, bold sans
 * display type, pill-shaped buttons, and an ambient spotlight background.
 */
val CinemaUaPalette = UaPalette(
    void = CinemaVoid,
    voidElevated = CinemaVoidElevated,
    surface1 = CinemaSurface1,
    surface2 = CinemaSurface2,
    azure = CinemaAccent,
    azure2 = CinemaGradientBottom,
    accentGradientTop = CinemaGradientTop,
    accentGradientBottom = CinemaGradientBottom,
    accentGradient = Brush.linearGradient(listOf(CinemaGradientTop, CinemaGradientBottom)),
    accentOnFill = Color(0xFF2B160E),
    // accentText stays close to labelPrimary rather than gold: thin gold letters at Caption/Micro
    // scale read as washed-out on the warm charcoal background - see UaPalette.accentText doc.
    accentText = Color(0xFFFFF2D9),
    routeGreen = RouteGreen,
    routeAmber = RouteAmber,
    routeRed = CinemaDanger,
    labelPrimary = CinemaLabelPrimary,
    labelSecondary = CinemaLabelSecondary,
    labelTertiary = CinemaLabelTertiary,
    hairline = Color(0x50FFCF70),
    azureGlow = Color(0x99FFB85F),
    greenGlow = GreenGlow,
    amberGlow = AmberGlow,
    redGlow = Color(0x99C9695A),
    // Neutral video-overlay scrims - same reasoning as UaPalette.scrimBackground doc: they sit over
    // content, not chrome, so they don't follow the warm palette.
    scrimBackground = Color(0x66000000),
    overlayHighlight = Color(0x33FFFFFF),
    glassTone = Color(0xE6141210),
    // One explicit offline family across body and display text avoids OEM-dependent serif metrics
    // changing wrapping and golden screenshots. Cinema keeps its identity through palette/depth.
    displayFontFamily = FontFamily.SansSerif,
    edgeHighlightNeutral = CinemaEdgeHighlightNeutral,
    edgeHighlightStrong = CinemaEdgeHighlightStrong,
    edgeHighlightAccent = CinemaEdgeHighlightAccent,
    shadowSoft = CinemaShadowSoft,
    surfaceLiftAmount = 0.08f,
    vignette = true,
    wallpaperTexture = true,
    pillButtons = true,
    secondaryButtonStyle = SecondaryButtonStyle.GHOST,
)
