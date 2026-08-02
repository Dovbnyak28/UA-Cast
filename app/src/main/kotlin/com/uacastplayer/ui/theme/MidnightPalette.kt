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
private val MidnightSurface1 = Color(0xFF0E1116)
private val MidnightSurface2 = Color(0xFF171C23)

// Accent - pewter: a steel grey with just enough blue in it to stay cool, at roughly a fifth of the
// saturation a normal accent carries. Muted on purpose (see the palette doc). Contrast on true
// black, WCAG: accent 7.5:1, accentText 11.2:1, gradient bottom 6.0:1 (only ever a fill, never
// text).
private val MidnightAccent = Color(0xFF8E9BB3)
private val MidnightAccentBright = Color(0xFFB4BECF)
private val MidnightGradientTop = Color(0xFFB4BECF)
private val MidnightGradientBottom = Color(0xFF7C89A3)

// Text - pure white primary, because the point of this theme is the maximum contrast a display can
// physically produce (21:1). Secondary is 9.1:1 and tertiary 5.0:1 - the tertiary clears AA here,
// where the other two themes' 30%-white sits below it.
private val MidnightLabelPrimary = Color(0xFFFFFFFF)
private val MidnightLabelSecondary = Color(0xFFA3ABBA)
private val MidnightLabelTertiary = Color(0xFF757C8C)

// Route health - green and amber stay the universal values so route status keeps meaning the same
// thing across themes (same reasoning as CinemaPalette); red is brightened for true black. These
// are the only saturated colors in the theme, which is the entire point - see below.
private val MidnightDanger = Color(0xFFFF5A52)

/**
 * Maximum-contrast OLED palette: true black background with no texture over it, white primary text,
 * and a deliberately near-neutral pewter accent - see docs/DESIGN_SYSTEM.md "Themes".
 *
 * It exists to cover the axis the other two leave open rather than to be a third colour scheme.
 * Azure is a neutral near-black with a cool accent and Cinema a warm charcoal with a gold one, but
 * both paint the same ambient wallpaper behind everything and both sit a few percent above black.
 * This one is flat, unlit, and as far apart in luminance as the panel allows - which is the theme
 * to reach for on an OLED phone, in a dark room, or when the other two read as too soft.
 *
 * **The accent is muted on purpose, and that is the theme's actual idea.** A saturated accent on
 * true black is the loudest thing a phone screen can do - there is no ambient tone for it to sit
 * against, so it glows. The first attempt here was a violet and read as candy. Pulling the accent
 * down to a near-neutral pewter leaves chrome reading as chrome and hands saturation to the only
 * things in the app that should compete for the eye: [routeGreen], [routeAmber] and [routeRed]. In
 * this theme colour means status and nothing else. Anything added later that wants to be noticed
 * should earn it with contrast or size, not by turning the accent up.
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
    accentOnFill = Color(0xFF0B0E14),
    // Unlike Cinema, the accent itself is used for small text here: pewter on true black is 7.5:1,
    // so thin Caption-scale glyphs stay solid where Cinema's gold on warm charcoal did not. The
    // brighter of the two is used so they hold up at Micro scale as well.
    accentText = MidnightAccentBright,
    routeGreen = RouteGreen,
    routeAmber = RouteAmber,
    routeRed = MidnightDanger,
    labelPrimary = MidnightLabelPrimary,
    labelSecondary = MidnightLabelSecondary,
    labelTertiary = MidnightLabelTertiary,
    hairline = Color(0x33B4BECF),
    azureGlow = Color(0x808E9BB3),
    greenGlow = GreenGlow,
    amberGlow = AmberGlow,
    redGlow = Color(0x99FF5A52),
    // Neutral video-overlay scrims - same reasoning as UaPalette.scrimBackground: they sit over
    // content, not chrome.
    scrimBackground = Color(0x66000000),
    overlayHighlight = Color(0x33FFFFFF),
    glassTone = Color(0xE60A0C10),
    displayFontFamily = FontFamily.Default,
    edgeHighlightNeutral = Color(0x1FFFFFFF),
    edgeHighlightStrong = Color(0x38FFFFFF),
    edgeHighlightAccent = Color(0x738E9BB3),
    shadowSoft = Color(0x66000000),
    // A touch more than the other two: a raised edge has to climb out of pure black to be seen at
    // all, where 6% is already visible against a background that starts a few percent up.
    surfaceLiftAmount = 0.08f,
    vignette = false,
    wallpaperTexture = false,
    pillButtons = false,
    secondaryButtonStyle = SecondaryButtonStyle.RAISED,
)
