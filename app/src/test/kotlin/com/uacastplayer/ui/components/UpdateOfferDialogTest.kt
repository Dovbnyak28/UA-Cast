package com.uacastplayer.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.update.AppVersion
import com.uacastplayer.update.GitHubRelease
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class UpdateOfferDialogTest {
    @get:Rule val composeRule = createComposeRule()

    private val release = GitHubRelease(
        version = AppVersion.parse("v1.4.0")!!,
        tagName = "v1.4.0",
        releaseUrl = "https://github.com/Dovbnyak28/UA-Cast/releases/tag/v1.4.0",
        releaseNotes = "• Покращено DLNA\n• Виправлено запуск плеєра",
    )

    @Test
    fun installRequiresAnExplicitTap() {
        var installs = 0
        var later = 0
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                UpdateOfferDialog(
                    release,
                    onInstall = { installs++ },
                    onLater = { later++ },
                    onOpenRelease = {},
                )
            }
        }

        assertEquals(0, installs)
        composeRule.onNodeWithText(
            "Версія v1.4.0 готова до оновлення. Ваші плейлисти та налаштування залишаться на пристрої.",
        )
            .assertExists()
        composeRule.onNodeWithText("Що нового").assertExists()
        composeRule.onNodeWithText("• Покращено DLNA\n• Виправлено запуск плеєра").assertExists()
        composeRule.onNodeWithText("Нагадати пізніше").performClick()
        assertEquals(0, installs)
        assertEquals(1, later)
    }

    @Test
    fun installButtonIsAvailable() {
        var installs = 0
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                UpdateOfferDialog(release, onInstall = { installs++ }, onLater = {}, onOpenRelease = {})
            }
        }

        composeRule.onNodeWithText("Оновити").performClick()
        assertEquals(1, installs)
    }

    @Test
    fun missingNotesFallBackToAnHonestMessageAndReleaseLink() {
        var opened: String? = null
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                UpdateOfferDialog(
                    release.copy(releaseNotes = null),
                    onInstall = {},
                    onLater = {},
                    onOpenRelease = { opened = it },
                )
            }
        }

        composeRule.onNodeWithText("Опис змін до цього релізу не додано.").assertExists()
        composeRule.onNodeWithText("Переглянути реліз на GitHub").performClick()
        assertEquals(release.releaseUrl, opened)
    }

    @Test
    fun longNotesDoNotPushActionsOffASmallLargeTextScreen() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) {
                UaCastTheme(AppTheme.CINEMA) {
                    UpdateOfferDialog(
                        release.copy(releaseNotes = "Покращення DLNA. ".repeat(100)),
                        onInstall = {},
                        onLater = {},
                        onOpenRelease = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Оновити").assertIsDisplayed()
        composeRule.onNodeWithText("Нагадати пізніше").assertIsDisplayed()
    }
}
