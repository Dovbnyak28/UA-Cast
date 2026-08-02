package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// Backgrounds - true #000000, not the "near black" the other two use. On an OLED panel a black
// pixel is an *off* pixel, so this is the one theme where the background costs nothing to draw and
// nothing to light; it is also why wallpaperTexture is false below. The surfaces above it are
// lifted just enough to separate a card from the void, with a faint violet cast so they read as
// deliberate rather than as washed-out grey.
private val MidnightVoid = Color(0xFF000000)
private val MidnightSurface1 = Color(0xFF0E0E14)
private val MidnightSurface2 = Color(0xFF17171F)

// Accent - violet, the one hue neither of the other themes and none of the three route-health
// colors already occupy. Contrast on true black, WCAG: accent 7.7:1, accentText 11.4:1, gradient
// bottom 5.6:1 (only ever a fill, never text).
private val MidnightAccent = Color(0xFFA78BFA)
private val MidnightAccentBright = Color(0xFFC4B5FD)
private val MidnightGradientTop = Color(0xFFC4B5FD)
private val MidnightGradientBottom = Color(0xFF8B6FEE)

// Text - pure white primary, because the point of this theme is the maximum contrast a display can
// physically produce (21:1). Secondary is 8.7:1 and tertiary 4.0:1 - the latter is below AA for
// body text, but it is only ever de-emphasised metadata, and it is still well ahead of the 30%-white
// tertiary the other two themes use.
private val MidnightLabelPrimary = Color(0xFFFFFFFF)
private val MidnightLabelSecondary = Color(0xFFA9A3BD)
private val MidnightLabelTertiary = Color(0xFF6E6980)

// Route health - green and amber stay the universal values so route status keeps meaning the same
// thing across themes (same reasoning as CinemaPalette); red is brightened for true black.
private val MidnightDanger = Color(0xFFFF5A52)

/**
 * Maximum-contrast OLED palette: true black background with no texture over it, a violet accent,
 * and white primary text - see docs/DESIGN_SYSTEM.md "Themes".
 *
 * It exists to cover the axis the other two leave open rather than to be a third colour scheme.
 * Azure is a neutral near-black with a cool accent and Cinema a warm charcoal with a gold one, but
 * both paint the same ambient wallpaper behind everything and both sit a few percent above black.
 * This one is flat, unlit, and as far apart in luminance as the panel allows - which is the theme
 * to reach for on an OLED phone, in a dark room, or when the other two read as too soft.
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
    accentOnFill = Color(0xFF12081F),
    // Unlike Cinema, the accent itself is used for small text here: violet on true black is 7.7:1,
    // so thin Caption-scale glyphs stay solid where Cinema's gold on warm charcoal did not. The
    // brighter of the two violets is used so they hold up at Micro scale as well.
    accentText = MidnightAccentBright,
    routeGreen = RouteGreen,
    routeAmber = RouteAmber,
    routeRed = MidnightDanger,
    labelPrimary = MidnightLabelPrimary,
    labelSecondary = MidnightLabelSecondary,
    labelTertiary = MidnightLabelTertiary,
    hairline = Color(0x33A78BFA),
    azureGlow = Color(0x80A78BFA),
    greenGlow = GreenGlow,
    amberGlow = AmberGlow,
    redGlow = Color(0x99FF5A52),
    // Neutral video-overlay scrims - same reasoning as UaPalette.scrimBackground: they sit over
    // content, not chrome.
    scrimBackground = Color(0x66000000),
    overlayHighlight = Color(0x33FFFFFF),
    glassTone = Color(0xE60A0A10),
    displayFontFamily = FontFamily.Default,
    edgeHighlightNeutral = Color(0x1FFFFFFF),
    edgeHighlightStrong = Color(0x38FFFFFF),
    edgeHighlightAccent = Color(0x73A78BFA),
    shadowSoft = Color(0x66000000),
    // A touch more than the other two: a raised edge has to climb out of pure black to be seen at
    // all, where 6% is already visible against a background that starts a few percent up.
    surfaceLiftAmount = 0.08f,
    vignette = false,
    wallpaperTexture = false,
    pillButtons = false,
    secondaryButtonStyle = SecondaryButtonStyle.RAISED,
)
