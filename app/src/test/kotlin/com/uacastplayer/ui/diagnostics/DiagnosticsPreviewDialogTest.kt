package com.uacastplayer.ui.diagnostics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the "Send diagnostics" preview costs to open.
 *
 * Two field reports off a Mi A2 carried the evidence in their own attached logcat: `Davey!
 * duration=1110ms` with `Skipped 63 frames`, and `duration=1046ms` with `Skipped 60`, both
 * timestamped to the moment this dialog opened, on two separate days. The reports are 520 lines and
 * about 48KB, and the dialog laid all of it out as a single `Text` inside a `verticalScroll` - which
 * has to measure every line before it can draw the first.
 *
 * The fix is a `LazyColumn`, and what this pins is the property that makes it a fix: the number of
 * lines actually composed does not grow with the report. Swap the lazy list back for a `Text` in a
 * `verticalScroll` and the last test here fails, because every line is suddenly a node.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class DiagnosticsPreviewDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** The shape of a real report: a short header, then the 500-entry log ring. */
    private fun realisticReport(): String = buildString {
        appendLine("UA Cast diagnostics report")
        appendLine("App version: 0.9.0")
        repeat(LOG_RING_ENTRIES) { appendLine("2026-08-12 22:45:0$it [DEBUG] ProxyServer: line number $it") }
    }

    private fun show(report: String) {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                DiagnosticsPreviewDialog(report = report, onCancel = {}, onSend = {})
            }
        }
    }

    @Test
    fun `the top of the report is on screen`() {
        show(realisticReport())

        composeRule.onNodeWithText("UA Cast diagnostics report").assertIsDisplayed()
    }

    @Test
    fun `the body scrolls, so the rest is reachable`() {
        show(realisticReport())

        composeRule.onNode(hasTestTag(UiTestTags.DIAGNOSTICS_PREVIEW_BODY) and hasScrollAction())
            .assertExists()
    }

    /**
     * The measurement, as an assertion, in the two halves that together mean "bounded".
     *
     * A lazy list is not proved by counting nodes alone: one `Text` holding the whole report is also
     * one node, and would pass a "fewer than 500" check while being the exact defect. So this pins
     * both that the lines are separate nodes *and* that no single node carries more than a line of
     * the report. The bounds are generous - they are here to tell "what fits on screen" from "all of
     * it", not to pin a count that a font metric could move.
     */
    @Test
    fun `only what fits on screen is laid out`() {
        val report = realisticReport()
        show(report)

        val texts = composeRule.onAllNodes(hasText("line number", substring = true))
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .map { it.text }

        assertTrue("nothing was laid out at all, so this proves nothing", texts.isNotEmpty())
        val longest = texts.maxOf { it.length }
        assertTrue(
            "one node holds $longest characters of a ${report.length}-character report; " +
                "the preview is laying the whole thing out as a single Text",
            longest < LONGEST_REASONABLE_LINE,
        )
        assertTrue(
            "${texts.size} of $LOG_RING_ENTRIES log lines were laid out; the preview is not lazy",
            texts.size < LOG_RING_ENTRIES / 2,
        )
    }

    private companion object {
        /** [com.uacastplayer.log.LogBuffer]'s capacity, which is what the report carries. */
        const val LOG_RING_ENTRIES = 500

        /** Longer than any single line a report writes, far shorter than the report. */
        const val LONGEST_REASONABLE_LINE = 400
    }
}
