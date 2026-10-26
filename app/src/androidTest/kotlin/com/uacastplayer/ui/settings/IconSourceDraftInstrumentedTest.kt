package com.uacastplayer.ui.settings

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uacastplayer.R
import com.uacastplayer.settings.IconSourceAddError
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.tv.TvInputRegistry
import com.uacastplayer.ui.tv.TvPresentation
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated UI fixtures: no source persistence, icon requests or personal playlists. */
@RunWith(AndroidJUnit4::class)
class IconSourceDraftInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun debugOnly() {
        assertEquals("com.uacastplayer.debug", rule.activity.packageName)
    }

    @Test fun rejectedAddressRemainsEditable() {
        val error = mutableStateOf<IconSourceAddError?>(null)
        showEditor(error = error, onAdd = { error.value = IconSourceAddError.INVALID_URL })
        submit("icons.example.test/logos")
        rule.onNode(hasSetTextAction()).assertTextEquals("icons.example.test/logos")
    }

    @Test fun canonicalSourceAcknowledgementClearsTheSubmittedDraft() {
        val sources = mutableStateOf(emptyList<String>())
        showEditor(sources = sources)
        submit(RAW_URL)
        rule.onNode(hasSetTextAction()).assertTextEquals(RAW_URL)
        rule.runOnIdle { sources.value = listOf(CANONICAL_URL) }
        rule.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
    }

    @Test fun oldAcknowledgementDoesNotEraseANewerEdit() {
        val sources = mutableStateOf(emptyList<String>())
        showEditor(sources = sources)
        submit(RAW_URL)
        rule.onNode(hasSetTextAction()).performTextReplacement(OTHER_URL)
        rule.runOnIdle { sources.value = listOf(CANONICAL_URL) }
        rule.onNode(hasSetTextAction()).assertTextEquals(OTHER_URL)
    }

    @Test fun televisionDpadUpLeavesTheEditor() {
        showEditor(sources = mutableStateOf(listOf(OTHER_URL)), television = true)
        val editor = rule.onNode(hasSetTextAction())
        editor.performTextInput(RAW_URL)
        editor.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        editor.assertIsFocused()
        rule.runOnIdle {
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP))
        }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.settings_icon_sources_remove))
            .assertIsFocused()
    }

    private fun submit(draft: String) {
        rule.onNode(hasSetTextAction()).performTextInput(draft)
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.settings_icon_sources_add)).performClick()
        rule.waitForIdle()
    }

    private fun showEditor(
        sources: MutableState<List<String>> = mutableStateOf(emptyList()),
        error: MutableState<IconSourceAddError?> = mutableStateOf(null),
        onAdd: (String) -> Unit = {},
        television: Boolean = false,
    ) {
        val registry = TvInputRegistry()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                TvPresentation(television, registry) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        IconSourcesSection(sources.value, error.value, onAdd, {}, { error.value = null })
                    }
                }
            }
        }
    }

    private companion object {
        const val RAW_URL = "HTTPS://icons.example.test/logos/"
        const val CANONICAL_URL = "https://icons.example.test/logos"
        const val OTHER_URL = "https://other.example.test/logos"
    }
}
