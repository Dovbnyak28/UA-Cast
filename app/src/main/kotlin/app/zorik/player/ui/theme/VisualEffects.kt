package app.zorik.player.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

val LocalReducedEffects = staticCompositionLocalOf { false }
internal val LocalReducedEffectsChange = staticCompositionLocalOf<(Boolean) -> Unit> { {} }
