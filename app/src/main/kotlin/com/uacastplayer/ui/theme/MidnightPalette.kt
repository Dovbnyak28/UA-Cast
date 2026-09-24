package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// Backgrounds - true #000000, not the "near black" the other two use. On an OLED panel a black
// pixel is an *off* pixel, so this is the one theme where the background costs nothing to draw and
// nothing to light; it is also why wallpaperTexture is false below. The surfaces above it are
// lifted just enough to separate a card from the void, with a cool slate cast so they read as
// deliberate rather than as washed-out grey.
private val MidnightVoid = Color(0xFF000000)
private val MidnightSurface1 = Color(0xFF101521)
private val MidnightSurface2 = Color(0xFF1A2232)

// Electric lavender and ice-blue keep the OLED-black option vivid without lifting its background.
private val MidnightAccent = Color(0xFFB39AFF)
private val MidnightAccentBright = Color(0xFF75E3FF)
private val MidnightGradientTop = Color(0xFFB9A5FF)
private val MidnightGradientBottom = Color(0xFF62DFFF)

// Text - pure white primary for maximum display contrast; secondary and tertiary are tuned to stay
// clearly readable on the lifted OLED surfaces without washing out the true-black canvas.
private val MidnightLabelPrimary = Color(0xFFFFFFFF)
private val MidnightLabelSecondary = Color(0xFFB9C3D6)
private val MidnightLabelTertiary = Color(0xFF8997AF)

// Route health - green and amber stay the universal values so route status keeps meaning the same
// thing across themes (same reasoning as CinemaPalette); red is brightened for true black. These
// are the only saturated colors in the theme, which is the entire point - see below.
private val MidnightDanger = Color(0xFFFF5A52)

/**
 * OLED palette: true black background with no texture over it, white primary text, and vivid
 * lavender/ice accents - see docs/DESIGN_SYSTEM.md "Themes".
 *
 * It exists to cover the axis the other two leave open rather than to be a third colour scheme.
 * Azure is a neutral near-black with a cool accent and Cinema a warm charcoal with a gold one, but
 * both paint the same ambient wallpaper behind everything and both sit a few percent above black.
 * This one is flat, unlit, and as far apart in luminance as the panel allows - which is the theme
 * to reach for on an OLED phone, in a dark room, or when the other two read as too soft.
 *
 * Colorful controls provide useful hierarchy while true-black surfaces keep the OLED-friendly
 * character. Status colors remain semantically distinct from the lavender/ice accent.
 */
val MidnightUaPalette = UaPalette(
    void = MidnightVoid,
    // Equal to void on purpose: appBackground() fades between the two, and any lift here would put
    // a gradient of lit pixels across a background whose whole value is being switched off.
    voidElevated = MidnightVoid,
    surface1 = MidnightSurface1,
    surface2 = MidnightSurface2,
    azure = MidnightAccent,
    azure2 = MidnightAccentBright,
    accentGradientTop = MidnightGradientTop,
    accentGradientBottom = MidnightGradientBottom,
    accentGradient = Brush.linearGradient(listOf(MidnightGradientTop, MidnightGradientBottom)),
    accentOnFill = Color(0xFF10101D),
    // The bright ice tone remains readable at caption scale on true black.
    accentText = Color(0xFFD9D0FF),
    routeGreen = RouteGreen,
    routeAmber = RouteAmber,
    routeRed = MidnightDanger,
    labelPrimary = MidnightLabelPrimary,
    labelSecondary = MidnightLabelSecondary,
    labelTertiary = MidnightLabelTertiary,
    hairline = Color(0x4F9F86FF),
    azureGlow = Color(0x99A58CFF),
    greenGlow = GreenGlow,
    amberGlow = AmberGlow,
    redGlow = Color(0x99FF5A52),
    // Neutral video-overlay scrims - same reasoning as UaPalette.scrimBackground: they sit over
    // content, not chrome.
    scrimBackground = Color(0x66000000),
    overlayHighlight = Color(0x33FFFFFF),
    glassTone = Color(0xE60A0C10),
    displayFontFamily = FontFamily.Default,
    edgeHighlightNeutral = Color(0x2A9BB3D9),
    edgeHighlightStrong = Color(0x4DABC5FF),
    edgeHighlightAccent = Color(0x99A58CFF),
    shadowSoft = Color(0x66000000),
    // A touch more than the other two: a raised edge has to climb out of pure black to be seen at
    // all, where 6% is already visible against a background that starts a few percent up.
    surfaceLiftAmount = 0.08f,
    vignette = false,
    wallpaperTexture = false,
    pillButtons = false,
    secondaryButtonStyle = SecondaryButtonStyle.RAISED,
)
