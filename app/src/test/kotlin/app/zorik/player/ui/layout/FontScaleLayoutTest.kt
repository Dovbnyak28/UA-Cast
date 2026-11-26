package app.zorik.player.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.language.LanguagePickerScreen
import app.zorik.player.guidedtour.GuidedTourPhase
import app.zorik.player.guidedtour.GuidedTourState
import app.zorik.player.guidedtour.GuidedTip
import app.zorik.player.ui.guidedtour.GuidedTipCard
import app.zorik.player.ui.guidedtour.GuidedTourCallbacks
import app.zorik.player.ui.guidedtour.GuidedTourOverlay
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import app.zorik.player.ui.components.GlassTabBar
import app.zorik.player.ui.components.GlassNavigationRail
import app.zorik.player.ui.components.TabBarItem
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.settings.DataSettingsSection
import app.zorik.player.ui.settings.PlaylistSettingsSection
import app.zorik.player.playlist.PlaylistUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two screens a first install cannot get past, held at the smallest viewport this app supports
 * and pushed through every font scale Android offers.
 *
 * Both are a `Column` ending in a primary button, which is the layout that fails quietly: the text
 * above the button grows with the font scale, the button keeps its size, and past some scale it is
 * simply pushed off the bottom. Nothing crashes and nothing is logged - the user just cannot
 * continue, and a person who needs 2.0x text is exactly the person who cannot work around it.
 *
 * That is not hypothetical here: `LanguagePickerScreenshotTest` exists because this same button
 * disappeared once already, at 1.0x on a short landscape viewport. This covers the other axis.
 *
 * The viewport is `w320dp-h480dp` - a 4.5" phone, and also what a modern phone becomes at the
 * largest Display Size setting, since that setting works by raising density and so shrinks the dp
 * viewport. Font scale is applied through [LocalDensity] because that is the exact channel Android
 * uses to scale `sp` in Compose; `@Config(qualifiers)` has no font-scale axis.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class FontScaleLayoutTest(private val fontScale: Float) {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContentAtScale(content: @Composable () -> Unit) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                ZorikTheme(AppTheme.CINEMA) { content() }
            }
        }
    }

    /**
     * Fails if [text] is off-screen, or on-screen but with any edge past the viewport - the second
     * case is the one `assertIsDisplayed` alone lets through, and a half-visible button is not a
     * usable one.
     */
    private fun assertFullyOnScreen(text: String) {
        val node = composeRule.onNodeWithText(text)
        node.assertIsDisplayed()

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val bounds = node.getUnclippedBoundsInRoot()
        val slack = 0.5.dp

        assertTrue(
            "at fontScale $fontScale '$text' is cut off: $bounds vs viewport $root",
            bounds.left >= root.left - slack &&
                bounds.top >= root.top - slack &&
                bounds.right <= root.right + slack &&
                bounds.bottom <= root.bottom + slack,
        )
    }

    @Test
    fun languagePicker_continueStaysOnScreen() {
        setContentAtScale { LanguagePickerScreen(onLanguageConfirmed = {}) }
        assertFullyOnScreen("Продовжити")
    }

    @Test
    fun languagePicker_lastLanguageRemainsReachableAbovePinnedAction() {
        setContentAtScale { LanguagePickerScreen(onLanguageConfirmed = {}) }
        composeRule.onNodeWithText("Español").performScrollTo().assertIsDisplayed()
        assertFullyOnScreen("Продовжити")
    }

    /**
     * The intro's buttons sit under a pager whose text grows with the font. At 2.0x on a 4.5"
     * viewport that text is the first thing that would push Next off the bottom edge - the pages
     * scroll instead, and the buttons stay put.
     */
    @Test
    fun intro_nextButtonStaysOnScreen() {
        setContentAtScale {
            GuidedTourOverlay(state = GuidedTourState(phase = GuidedTourPhase.INTRO), callbacks = GuidedTourCallbacks())
        }
        assertFullyOnScreen("Далі")
    }

    @Test
    fun intro_addPlaylistAndLaterStayOnScreen() {
        setContentAtScale {
            GuidedTourOverlay(
                state = GuidedTourState(phase = GuidedTourPhase.INTRO, introPage = 2),
                callbacks = GuidedTourCallbacks(),
            )
        }
        assertFullyOnScreen("Додати плейлист")
        assertFullyOnScreen("Пізніше")
    }

    @Test
    fun guidedTip_gotItStaysOnScreen() {
        setContentAtScale {
            GuidedTipCard(tip = GuidedTip.PLAYER, onDismiss = {})
        }
        assertFullyOnScreen("Зрозуміло")
    }

    @Test
    fun bottomNavigation_keepsEveryDestinationReachable() {
        setContentAtScale {
            GlassTabBar(
                items = listOf(
                    TabBarItem("Головна", AppIcons.Home, selected = true, onClick = {}),
                    TabBarItem("Канали", AppIcons.Channels, selected = false, onClick = {}),
                    TabBarItem("Улюблені", AppIcons.Favorites, selected = false, onClick = {}),
                    TabBarItem(
                        label = "Налашт.",
                        icon = AppIcons.Settings,
                        selected = false,
                        onClick = {},
                        contentDescription = "Налаштування",
                    ),
                ),
            )
        }

        composeRule.onNodeWithContentDescription("Налаштування").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Налаштування").assertDoesNotExist()
        assertFullyOnScreen("Налашт.")
    }

    @Test
    fun navigationRail_keepsFullAccessibilityNameWhenVisualLabelIsCompact() {
        setContentAtScale {
            GlassNavigationRail(
                items = listOf(
                    TabBarItem(
                        label = "Налашт.",
                        icon = AppIcons.Settings,
                        selected = true,
                        onClick = {},
                        contentDescription = "Налаштування",
                    ),
                ),
            )
        }

        composeRule.onNodeWithContentDescription("Налаштування").assertIsDisplayed()
        composeRule.onNodeWithText("Налашт.").assertIsDisplayed()
    }

    @Test
    fun dataActions_stayAccessibleWithLargeText() {
        setContentAtScale {
            // Settings places this section in a scrollable page. The vertical file actions no
            // longer need equal row heights, but must remain fully reachable at 200% text size.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                DataSettingsSection(onImportBackup = {}, onShowExportWarning = {})
            }
        }
        for (label in listOf("Зберегти у файл", "Відновити з файлу")) {
            composeRule.onNodeWithText(label).performScrollTo().assertHasClickAction()
            assertFullyOnScreen(label)
        }
    }

    @Test
    fun emptyPlaylist_doesNotClaimAnActiveSource() {
        setContentAtScale {
            PlaylistSettingsSection(
                playlistState = PlaylistUiState(),
                hiddenGroupKeys = emptySet(),
                onOpenAddPlaylist = {},
                onRestoreGroup = {},
            )
        }
        composeRule.onNodeWithText("Активний плейлист").assertDoesNotExist()
        composeRule.onNodeWithText("Додати плейлист").assertIsDisplayed()
    }

    companion object {
        /** Every scale Android's Settings > Display > Font size offers, smallest to largest. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "fontScale={0}")
        fun fontScales(): List<Array<Any>> =
            listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 2.0f).map { arrayOf<Any>(it) }
    }
}
