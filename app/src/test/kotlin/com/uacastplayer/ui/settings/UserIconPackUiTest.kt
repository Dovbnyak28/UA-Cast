package com.uacastplayer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class UserIconPackUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun emptyStateHasNoPredefinedPackAndAddRequiresAnAddress() {
        var added = ""
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    IconSourcesSection(emptyList(), null, { added = it }, {}, {})
                }
            }
        }
        rule.onNodeWithText("Пакети іконок каналів").assertExists()
        rule.onNodeWithText("Пакетів ще немає. Канали показуватимуть ініціали, доки ви не додасте власні іконки.")
            .assertExists()
        rule.onNodeWithText("https://cdn.epg.one/logo/", substring = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Додати пакет іконок").assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextInput("https://icons.example.test/logos/")
        rule.onNodeWithContentDescription("Додати пакет іконок").assertIsEnabled().performClick()
        assertEquals("https://icons.example.test/logos/", added)
    }

    @Test fun everyUserPackCanBeRemovedAndTheEmptyStateReturns() {
        val sources = mutableStateOf(listOf("https://icons.example.test/logos"))
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    IconSourcesSection(sources.value, null, {}, { sources.value = sources.value - it }, {})
                }
            }
        }
        rule.onNodeWithContentDescription("Видалити пакет іконок").performScrollTo().performClick()
        rule.onNodeWithText("https://icons.example.test/logos").assertDoesNotExist()
        assertEquals(emptyList<String>(), sources.value)
    }
}
