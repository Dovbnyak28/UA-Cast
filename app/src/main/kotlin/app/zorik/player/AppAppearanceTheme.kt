package app.zorik.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.LocalReducedEffects
import app.zorik.player.ui.theme.LocalReducedEffectsChange
import app.zorik.player.ui.theme.ZorikTheme
import app.zorik.player.ui.theme.themeForScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Only appearance changes invalidate this provider, not cache/prefetch counters. */
@Composable
internal fun AppAppearanceTheme(
    viewModel: AppViewModel, theme: AppTheme, television: Boolean, content: @Composable () -> Unit,
) {
    val effects = remember(viewModel) {
        viewModel.settingsState.map { it.reducedVisualEffects }.distinctUntilChanged()
    }
    val initialReduced = remember(viewModel) { viewModel.settingsState.value.reducedVisualEffects }
    val reduced by effects.collectAsStateWithLifecycle(initialReduced)
    CompositionLocalProvider(
        LocalReducedEffects provides reduced,
        LocalReducedEffectsChange provides viewModel::setReducedVisualEffects,
    ) {
        ZorikTheme(themeForScreen(theme, television), content)
    }
}
