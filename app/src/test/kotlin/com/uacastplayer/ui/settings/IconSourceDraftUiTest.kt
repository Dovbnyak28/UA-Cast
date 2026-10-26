package com.uacastplayer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import com.uacastplayer.settings.IconSourceAddError
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.tv.TvInputRegistry
import com.uacastplayer.ui.tv.TvPresentation
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
class IconSourceDraftUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun invalidAddressKeepsTheDraftForCorrection() =
        rejectedDraftIsRetained(IconSourceAddError.INVALID_URL, "icons.example.test/logos")

    @Test fun duplicateAddressKeepsTheDraft() =
        rejectedDraftIsRetained(IconSourceAddError.ALREADY_ADDED, RAW_URL)

    @Test fun sourceLimitKeepsTheDraft() =
        rejectedDraftIsRetained(IconSourceAddError.LIMIT_REACHED, RAW_URL)

    @Test fun successfulAddClearsOnlyAfterCanonicalSourceIsAcknowledged() {
        val sources = mutableStateOf(emptyList<String>())
        showEditor(sources = sources)
        submit(RAW_URL)
        rule.onNode(hasSetTextAction()).assertTextEquals(RAW_URL)
        rule.runOnIdle { sources.value = listOf(CANONICAL_URL) }
        // An empty field exposes its placeholder as Text; assert the editable value instead.
        rule.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
    }

    @Test fun gatedOrUnacknowledgedAddDoesNotDiscardTheDraft() {
        showEditor()
        submit(RAW_URL)
        rule.onNode(hasSetTextAction()).assertTextEquals(RAW_URL)
    }

    @Test fun successfulOldSubmissionDoesNotEraseANewerDraft() {
        val sources = mutableStateOf(emptyList<String>())
        showEditor(sources = sources)
        submit(RAW_URL)
        rule.onNode(hasSetTextAction()).performTextReplacement(OTHER_URL)
        rule.runOnIdle { sources.value = listOf(CANONICAL_URL) }
        rule.onNode(hasSetTextAction()).assertTextEquals(OTHER_URL)
    }

    @Test fun unrelatedSourceUpdateDoesNotAcknowledgeTheDraft() {
        val sources = mutableStateOf(emptyList<String>())
        showEditor(sources = sources)
        submit(RAW_URL)
        rule.runOnIdle { sources.value = listOf(OTHER_URL) }
        rule.onNode(hasSetTextAction()).assertTextEquals(RAW_URL)
    }

    @Test fun sourceAlreadyPresentBeforeSubmissionDoesNotCountAsAcknowledgement() {
        showEditor(sources = mutableStateOf(listOf(CANONICAL_URL)))
        submit(RAW_URL)
        rule.onNode(hasSetTextAction()).assertTextEquals(RAW_URL)
    }

    @Test fun televisionUpLeavesTheEditorForTheExistingSourceAction() {
        showEditor(sources = mutableStateOf(listOf(OTHER_URL)), television = true)
        val editor = rule.onNode(hasSetTextAction())
        editor.performTextInput(RAW_URL)
        editor.performSemanticsAction(SemanticsActions.RequestFocus)
        editor.assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithContentDescription("Видалити пакет іконок").assertIsFocused()
    }

    private fun rejectedDraftIsRetained(error: IconSourceAddError, draft: String) {
        val state = mutableStateOf<IconSourceAddError?>(null)
        showEditor(error = state, onAdd = { state.value = error })
        submit(draft)
        rule.onNode(hasSetTextAction()).assertTextEquals(draft)
    }

    private fun submit(draft: String) {
        rule.onNode(hasSetTextAction()).performTextInput(draft)
        rule.onNodeWithContentDescription("Додати пакет іконок").performClick()
        rule.waitForIdle()
    }

    private fun showEditor(
        sources: MutableState<List<String>> = mutableStateOf(emptyList()),
        error: MutableState<IconSourceAddError?> = mutableStateOf(null),
        onAdd: (String) -> Unit = {},
        television: Boolean = false,
    ) {
        val input = TvInputRegistry()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                TvPresentation(television, input) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
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
