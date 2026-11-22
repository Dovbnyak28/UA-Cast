package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// Ink-blue surfaces keep the cinema palette aligned with the Zorik brand and readable at night.
private val CinemaVoid = Color(0xFF0C111B)
private val CinemaVoidElevated = Color(0xFF121C2B)
private val CinemaSurface1 = Color(0xFF192436)
private val CinemaSurface2 = Color(0xFF24324A)

// Champagne-to-gold accents distinguish primary actions from the cool ink surfaces.
private val CinemaGradientTop = Color(0xFFFFD777)
private val CinemaGradientBottom = Color(0xFFE4B65C)
// The solid accent hue is bright enough for chrome; small text uses the near-white accentText.
private val CinemaAccent = Color(0xFFFFCF70)

// Text - cool whites and blue-greys separate the reading hierarchy from gold actions.
private val CinemaLabelPrimary = Color(0xFFF1F4FA)
private val CinemaLabelSecondary = Color(0xFFBBC7D9)
private val CinemaLabelTertiary = Color(0xFF93A4BC)

// Route health - danger stays warm/muted; good/warn are left as the universal Azure values since
// route-health color meaning should stay recognizable across themes.
private val CinemaDanger = Color(0xFFF38B82)

// Depth - blue-grey neutral edges sit quietly on ink-blue surfaces; accent edges stay gold.
// See ui/theme/Depth.kt and PanelSurface.kt for the shared surface treatment.
private val CinemaEdgeHighlightNeutral = Color(0x355B7498)
private val CinemaEdgeHighlightStrong = Color(0x596C8AB5)
private val CinemaEdgeHighlightAccent = Color(0x99FFCF70)
private val CinemaShadowSoft = Color(0x88000000)

/**
 * Warm, "premium cinema" palette: ink-blue background, champagne-to-gold accent, bold sans
 * display type, rounded rectangular buttons, and an ambient spotlight background.
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
    hairline = Color(0x404B6485),
    azureGlow = Color(0x99FFB85F),
    greenGlow = GreenGlow,
    amberGlow = AmberGlow,
    redGlow = Color(0x99C9695A),
    // Neutral video-overlay scrims - same reasoning as UaPalette.scrimBackground doc: they sit over
    // content, not chrome, so they don't follow the warm palette.
    scrimBackground = Color(0x66000000),
    overlayHighlight = Color(0x33FFFFFF),
    glassTone = Color(0xF0192436),
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
    pillButtons = false,
    secondaryButtonStyle = SecondaryButtonStyle.GHOST,
)
