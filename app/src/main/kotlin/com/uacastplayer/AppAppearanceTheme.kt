package com.uacastplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.LocalReducedEffects
import com.uacastplayer.ui.theme.LocalReducedEffectsChange
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.themeForScreen
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
        UaCastTheme(themeForScreen(theme, television), content)
    }
}
