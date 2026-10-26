package com.uacastplayer.ui.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.uacastplayer.player.IndexedChannel
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "en-w320dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class NextChannelsLayoutTest(private val fontScale: Float) {
    @get:Rule val rule = createComposeRule()

    @Test fun `similar channel names retain their distinguishing suffix and selection index`() {
        val names = listOf("Channel 2", "Channel 3", "Новини 2", "Canal 3")
        val channels = names.mapIndexed { index, name ->
            IndexedChannel(index + 4, M3uChannel(name, "https://unused.invalid/$index"))
        }
        var selected = -1
        show(channels) { selected = it.index }
        channels.forEachIndexed { position, channel ->
            rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToIndex(position)
            val node = rule.onNodeWithText(channel.channel.displayName, useUnmergedTree = true).performScrollTo()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("${channel.channel.displayName} clipped at $fontScale", layout.hasVisualOverflow)
            assertEquals(channel.channel.displayName.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            node.performClick()
            assertEquals(channel.index, selected)
        }
    }

    @Test fun `long names have explicit ellipsis instead of silently losing their end`() {
        val name = "National Geographic оригінальний міжнародний канал Español ".repeat(4)
        show(listOf(IndexedChannel(0, M3uChannel(name, "https://unused.invalid/live")))) {}
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(name, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue("Long name needs visible truncation at $fontScale", layout.isLineEllipsized(layout.lineCount - 1))
        assertTrue(layout.lineCount <= 2)
    }

    private fun show(channels: List<IndexedChannel>, onSelect: (IndexedChannel) -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                UaCastTheme(AppTheme.MIDNIGHT) {
                    NextChannelsRail(channels, 0, { null }, onSelect, {})
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "fontScale={0}")
        fun scales() = listOf(arrayOf(1f), arrayOf(1.5f), arrayOf(2f))
    }
}
