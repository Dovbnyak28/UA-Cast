package com.uacastplayer.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.components.PrimaryButton
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.components.UaCard
import com.uacastplayer.ui.components.UaListItem
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w960dp-h540dp-land-xhdpi")
@Category(RequiresComposeTestManifest::class)
class CinemaFoundationTvTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dpadSkipsDisabledActionsAndActivatesCardsAndRowsOnlyOnce() {
        val selected = mutableStateOf(false)
        var clicks = 0
        rule.setContent { UaCastTheme(AppTheme.CINEMA) {
            TvPresentation(true, remember { TvInputRegistry() }) {
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { first.requestFocus() }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("Дивитися", {}, Modifier.width(200.dp).focusRequester(first))
                    SecondaryButton("Недоступно", {}, Modifier.width(120.dp), enabled = false)
                    UaCard(Modifier.width(220.dp), selected = selected.value,
                        onClick = { selected.value = true; clicks++ }) { Text("Картка") }
                    UaListItem("Рядок", modifier = Modifier.width(220.dp), onClick = { clicks++ })
                }
            }
        } }
        rule.onNodeWithText("Дивитися").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithText("Картка").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onNodeWithText("Картка").assertIsSelected().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithText("Рядок").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(2, clicks) }
    }
}
