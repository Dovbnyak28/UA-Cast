package com.uacastplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Which visual style is active - see docs/DESIGN_SYSTEM.md "Themes". */
enum class AppTheme {
    AZURE, CINEMA, MIDNIGHT;

    companion object {
        // Cinema (warm charcoal + champagne-gold, radial vignette on every screen) is the default
        // look for new installs - see docs/DESIGN_SYSTEM.md "Themes" for the full palette.
        val DEFAULT = CINEMA
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}

/** How the design system's secondary button renders - see docs/DESIGN_SYSTEM.md "Themes". */
enum class SecondaryButtonStyle { RAISED, GHOST }

/**
 * Every semantic color (plus the handful of per-theme shape/font toggles introduced alongside
 * Cinema) a screen or component may need. Components must go through [UaTheme.palette] rather
 * than reading a Color.kt constant or a `Color(0x...)` literal directly, so a new theme only ever
 * means a new [UaPalette] value - see docs/DESIGN_SYSTEM.md "Themes" for the full rule.
 */
data class UaPalette(
    val void: Color,
    val voidElevated: Color,
    val surface1: Color,
    val surface2: Color,
    val azure: Color,
    val azure2: Color,
    val accentGradientTop: Color,
    val accentGradientBottom: Color,
    val accentGradient: Brush,
    /** Text/icon color for content drawn on top of a filled accent surface (e.g. play button icon). */
    val accentOnFill: Color,
    /**
     * The accent color used for small (< 12sp, i.e. Caption/Micro-scale) plain text runs - badges,
     * status labels, "view all" links. Distinct from [azure] because a theme's regular accent hue
     * can be too thin/low-contrast at that size even when it reads fine on icons or larger text -
     * see docs/DESIGN_SYSTEM.md "Themes". Icon tints and non-Caption text keep using [azure].
     */
    val accentText: Color,
    val routeGreen: Color,
    val routeAmber: Color,
    val routeRed: Color,
    val labelPrimary: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val hairline: Color,
    /** Glows are **fills and shadow spot colors, never text.** They are the route-health hues at
     * ~50% alpha, which is what makes a halo read as a halo - and what makes them fail contrast the
     * moment one is used as a foreground: `amberGlow` over black composites to #806B05, 3.99:1, and
     * shipped that way in Settings until Midnight's true-black background made it obvious. For
     * colored text use [routeGreen]/[routeAmber]/[routeRed] or [accentText]. */
    val azureGlow: Color,
    val greenGlow: Color,
    val amberGlow: Color,
    val redGlow: Color,
    /** Semi-transparent black behind small icon buttons overlaid on video/photo content (player
     * controls) - neutral on purpose, so it doesn't color-cast the content underneath. Same value
     * in every theme. */
    val scrimBackground: Color,
    /** Semi-transparent white for light-on-video overlays (e.g. the volume/brightness gesture
     * fill). Same value in every theme, for the same reason as [scrimBackground]. */
    val overlayHighlight: Color,
    /** Near-opaque tint behind the frosted-glass tab bar - unlike the scrim colors above this is
     * theme chrome, not a video overlay, so it follows the theme's background warmth. */
    val glassTone: Color,
    /** Display family selected per theme - see Type.kt DisplayTitle/DisplayName. */
    val displayFontFamily: FontFamily,
    /** Edge-highlight tone for [raisedSurface]'s border - the default, used on ordinary raised
     * chrome (round buttons, cards). Warm/ivory-tinted in Cinema rather than pure white - see
     * ui/theme/Depth.kt and docs/DESIGN_SYSTEM.md "§D Depth". */
    val edgeHighlightNeutral: Color,
    /** A brighter edge highlight for surfaces that need to read as more prominently raised (e.g.
     * the selected SegmentedControl segment) than [edgeHighlightNeutral] alone provides. */
    val edgeHighlightStrong: Color,
    /** Accent-tinted edge highlight, for raised surfaces that are themselves an accent-colored
     * control (e.g. a focused text field's underline glow). */
    val edgeHighlightAccent: Color,
    /** Ambient/spot shadow color for [raisedSurface] when `shadow = true`. */
    val shadowSoft: Color,
    /** How much lighter [raisedSurface]'s top edge gets relative to its base color, as a
     * [lighten] fraction (0f..1f) - the actual "raised" look, independent of the border. */
    val surfaceLiftAmount: Float,
    /** Radial "spotlight" background instead of the plain top-to-bottom gradient - see appBackground(). */
    val vignette: Boolean,
    /**
     * Whether the screen background paints the decorative wallpaper texture at all (see
     * appBackground()). True for the themes that want the ambient glow; false for Midnight, where
     * the whole point is that background pixels are *off* - a near-black texture would light every
     * one of them on an OLED panel and take the contrast down with it.
     */
    val wallpaperTexture: Boolean,
    /** Pill-shaped (fully rounded) primary/secondary buttons instead of RadiusItem corners. */
    val pillButtons: Boolean,
    val secondaryButtonStyle: SecondaryButtonStyle,
)

/** The palette in effect before this theme system existed - values are unchanged from Color.kt. */
val AzureUaPalette = UaPalette(
    void = Void,
    voidElevated = VoidElevated,
    surface1 = Surface1,
    surface2 = Surface2,
    azure = Azure,
    azure2 = Azure2,
    accentGradientTop = Azure,
    accentGradientBottom = Azure2,
    accentGradient = AzureGradient,
    // Azure2 is intentionally bright; a deep navy foreground keeps play buttons and selected
    // navigation pills readable across both gradient endpoints (white only passed the darker top).
    accentOnFill = Color(0xFF001B36),
    accentText = Azure,
    routeGreen = RouteGreen,
    routeAmber = RouteAmber,
    routeRed = RouteRed,
    labelPrimary = LabelPrimary,
    labelSecondary = LabelSecondary,
    labelTertiary = LabelTertiary,
    hairline = Hairline,
    azureGlow = AzureGlow,
    greenGlow = GreenGlow,
    amberGlow = AmberGlow,
    redGlow = RedGlow,
    scrimBackground = Color(0x66000000),
    overlayHighlight = Color(0x33FFFFFF),
    glassTone = Color(0xE6121214),
    displayFontFamily = FontFamily.Default,
    edgeHighlightNeutral = EdgeHighlightNeutral,
    edgeHighlightStrong = EdgeHighlightStrong,
    edgeHighlightAccent = EdgeHighlightAccent,
    shadowSoft = ShadowSoft,
    surfaceLiftAmount = 0.06f,
    vignette = false,
    wallpaperTexture = true,
    pillButtons = false,
    secondaryButtonStyle = SecondaryButtonStyle.RAISED,
)

// staticCompositionLocalOf is intentional, not an oversight: the Settings theme switcher changes
// this at the UaCastTheme root, and a theme switch is meant to force the *entire* app subtree to
// recompose with the new palette immediately - the exact opposite of the fine-grained,
// change-only-what-reads-it behavior compositionLocalOf would give. See docs/DESIGN_SYSTEM.md
// "Themes".
val LocalUaPalette = staticCompositionLocalOf { AzureUaPalette }

object UaTheme {
    val palette: UaPalette
        @Composable get() = LocalUaPalette.current
}
