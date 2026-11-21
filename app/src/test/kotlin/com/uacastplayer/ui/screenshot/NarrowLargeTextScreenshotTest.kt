package com.uacastplayer.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.favorites.FavoritesSortOrder
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.favorites.FavoritesScreen
import com.uacastplayer.ui.language.LanguagePickerScreen
import com.uacastplayer.ui.legal.HelpScreen
import com.uacastplayer.ui.legal.TermsScreen
import com.uacastplayer.ui.player.PlaybackFailureCard
import com.uacastplayer.ui.playlist.AddPlaylistScreen
import com.uacastplayer.ui.settings.SettingsOverview
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.UaTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Release-hardening phase E: the screens and states a user meets first, at the narrowest width the
 * app supports (320dp) and the largest font scale Android offers (2.0), in both shipping themes
 * and both shipped UI languages.
 *
 * Two things are checked. The recorded image is the visual review: a clipped title or a button
 * pushed off the edge shows up as a diff against the reviewed reference. And every clickable node
 * must keep a touch target of at least 48dp - the one usability rule here that a picture cannot
 * prove, since a 32dp target looks the same as a 48dp one.
 *
 * Font scale goes through [LocalDensity], the channel Android itself uses to scale `sp`;
 * `@Config(qualifiers)` has no font-scale axis (see FontScaleLayoutTest).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class NarrowLargeTextScreenshotTest(private val theme: AppTheme, private val locale: String) {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun applyLocale() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h640dp-xhdpi")
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, FONT_SCALE)) {
                UaCastTheme(theme) {
                    Box(Modifier.fillMaxSize().background(UaTheme.palette.void)) { content() }
                }
            }
        }
        composeRule.onRoot().captureRoboImage(
            "src/test/screenshots/narrow_large_text/${name}_${theme.name.lowercase()}_$locale.png",
        )
        assertTouchTargets(name)
    }

    /** Every visible, clickable, enabled node keeps a touch target of at least 48dp on both axes. */
    private fun assertTouchTargets(name: String) {
        val density = composeRule.density
        val minPx = with(density) { (MIN_TOUCH_TARGET - SLACK).toPx() }
        val offenders = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { SemanticsProperties.Disabled !in it.config }
            .filter { node ->
                val bounds = node.touchBoundsInRoot
                // Zero area means scrolled out of the viewport, which is reachable, not small.
                val visible = bounds.width > 0f && bounds.height > 0f
                visible && (bounds.width < minPx || bounds.height < minPx)
            }
            .map { node ->
                val bounds = node.touchBoundsInRoot
                val label = node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
                    ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                    ?: "<unlabelled>"
                with(density) { "$label ${bounds.width.toDp()} x ${bounds.height.toDp()}" }
            }
        assertTrue(
            "$name/${theme.name}/$locale: touch targets under $MIN_TOUCH_TARGET: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun addPlaylist_empty() = capture("add_playlist_empty") { AddPlaylistIn(PlaylistUiState()) }

    @Test
    fun addPlaylist_loading() = capture("add_playlist_loading") { AddPlaylistIn(PlaylistUiState(isLoading = true)) }

    @Test
    fun addPlaylist_networkError() = capture("add_playlist_network_error") {
        AddPlaylistIn(PlaylistUiState(error = PlaylistError.Network))
    }

    @Test
    fun favorites_empty() = capture("favorites_empty") {
        FavoritesScreen(
            favorites = emptyList(),
            playlistChannels = emptyList(),
            sortOrder = FavoritesSortOrder.DEFAULT,
            onSortOrderSelected = {},
            onChannelSelected = { _, _ -> },
            onRemove = {},
            onReorder = {},
            onOpenChannels = {},
            resolveIcon = { null },
        )
    }

    @Test
    fun settings_overview() = capture("settings_overview") {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsOverview({}, {}, {}, {}, {})
        }
    }

    @Test
    fun help() = capture("help") { HelpScreen(onBackClick = {}, onBuildDiagnosticsReport = { "" }) }

    @Test
    fun terms_firstRun() = capture("terms_first_run") { TermsScreen(onAccept = {}, onDecline = {}) }

    @Test
    fun languagePicker() = capture("language_picker") { LanguagePickerScreen(onLanguageConfirmed = {}) }

    @Test
    fun playbackFailure() = capture("playback_failure") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PlaybackFailureCard(onRetry = {}, onNext = {}, onExit = {})
        }
    }

    @Composable
    private fun AddPlaylistIn(state: PlaylistUiState) {
        AddPlaylistScreen(
            playlistState = state,
            onSetDisplayName = {},
            onLoadUrl = {},
            onPickFile = {},
            onLoadXtream = { _, _, _ -> },
            onBackClick = {},
            onPlaylistLoaded = {},
        )
    }

    companion object {
        private const val FONT_SCALE = 2f
        private val MIN_TOUCH_TARGET = 48.dp
        private val SLACK = 0.5.dp

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun parameters(): List<Array<Any>> =
            listOf(AppTheme.AZURE, AppTheme.CINEMA).flatMap { theme ->
                listOf("uk", "en").map { locale -> arrayOf<Any>(theme, locale) }
            }
    }
}
