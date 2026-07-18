package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// Backgrounds - warm charcoal instead of Azure's neutral black.
private val CinemaVoid = Color(0xFF0B0A08)
private val CinemaVoidElevated = Color(0xFF121009)
private val CinemaSurface1 = Color(0xFF171410)
private val CinemaSurface2 = Color(0xFF1E1A14)

// Accent - champagne gold gradient, see docs/DESIGN_SYSTEM.md "Themes".
private val CinemaGradientTop = Color(0xFFEAD6A4)
private val CinemaGradientBottom = Color(0xFFC9A96E)
// The single solid accent hue (icon tints, borders, most accent text) - contrast on CinemaVoid is
// ~7:1, verified against WCAG AA. Small (<12sp) text uses accentText instead - see UaPalette.
private val CinemaAccent = Color(0xFFD4B780)

// Text - warm ivory/beige instead of Azure's cool white/gray.
private val CinemaLabelPrimary = Color(0xFFF2EDE2)
private val CinemaLabelSecondary = Color(0xFF9C917B)
private val CinemaLabelTertiary = Color(0xFF6E6555)

// Route health - danger stays warm/muted; good/warn are left as the universal Azure values since
// route-health color meaning should stay recognizable across themes.
private val CinemaDanger = Color(0xFFC9695A)

/**
 * Warm, "premium cinema" palette: charcoal background, champagne-gold accent, serif display type
 * (wired in once the font lands), pill-shaped buttons, and a radial vignette background - see
 * docs/DESIGN_SYSTEM.md "Themes".
 */
val CinemaUaPalette = UaPalette(
    void = CinemaVoid,
    voidElevated = CinemaVoidElevated,
    surface1 = CinemaSurface1,
    surface2 = CinemaSurface2,
    azure = CinemaAccent,
    azure2 = CinemaGradientTop,
    accentGradientTop = CinemaGradientTop,
    accentGradientBottom = CinemaGradientBottom,
    accentGradient = Brush.linearGradient(listOf(CinemaGradientTop, CinemaGradientBottom)),
    accentOnFill = Color(0xFF1A1508),
    // accentText stays close to labelPrimary rather than gold: thin gold letters at Caption/Micro
    // scale read as washed-out on the warm charcoal background - see UaPalette.accentText doc.
    accentText = CinemaLabelPrimary,
    routeGreen = RouteGreen,
    routeAmber = RouteAmber,
    routeRed = CinemaDanger,
    labelPrimary = CinemaLabelPrimary,
    labelSecondary = CinemaLabelSecondary,
    labelTertiary = CinemaLabelTertiary,
    hairline = Color(0x40D4B780),
    azureGlow = Color(0x80D4B780),
    greenGlow = GreenGlow,
    amberGlow = AmberGlow,
    redGlow = Color(0x99C9695A),
    // Serif family lands in a later block; Cinema looks identical to Azure typographically until then.
    displayFontFamily = FontFamily.Default,
    vignette = true,
    // Pill buttons/ghost secondary style land in a later block.
    pillButtons = false,
    secondaryButtonStyle = SecondaryButtonStyle.RAISED,
)
