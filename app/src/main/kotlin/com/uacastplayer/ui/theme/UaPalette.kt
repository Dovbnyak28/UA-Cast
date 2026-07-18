package com.uacastplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Which visual style is active - see docs/DESIGN_SYSTEM.md "Themes". */
enum class AppTheme { AZURE, CINEMA }

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
    val accentGradient: Brush,
    /** Text/icon color for content drawn on top of a filled accent surface (e.g. play button icon). */
    val accentOnFill: Color,
    val routeGreen: Color,
    val routeAmber: Color,
    val routeRed: Color,
    val labelPrimary: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val hairline: Color,
    val azureGlow: Color,
    val greenGlow: Color,
    val amberGlow: Color,
    val redGlow: Color,
    /** Serif in Cinema, the platform default elsewhere - see Type.kt DisplayTitle/DisplayName. */
    val displayFontFamily: FontFamily,
    /** Radial "spotlight" background instead of the plain top-to-bottom gradient - see appBackground(). */
    val vignette: Boolean,
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
    accentGradient = AzureGradient,
    accentOnFill = Color.White,
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
    displayFontFamily = FontFamily.Default,
    vignette = false,
    pillButtons = false,
    secondaryButtonStyle = SecondaryButtonStyle.RAISED,
)

val LocalUaPalette = staticCompositionLocalOf { AzureUaPalette }

object UaTheme {
    val palette: UaPalette
        @Composable get() = LocalUaPalette.current
}
