package app.zorik.player.ui.guidedtour

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.zorik.player.app.GuidedTourController
import app.zorik.player.guidedtour.GuidedTip
import app.zorik.player.guidedtour.GuidedTourKeys
import app.zorik.player.guidedtour.GuidedTourPhase
import app.zorik.player.guidedtour.GuidedTourState
import app.zorik.player.guidedtour.GuidedTourStorage
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class InMemoryTourStorage(
    override var guidedTourCompleted: Boolean = true,
    override var guidedTourVersion: Int = 2,
    override var guidedTipsSeen: Set<String> = emptySet(),
) : GuidedTourStorage

/** Longer than the host's settle delay, so a tip whose element is on screen has had time to show. */
private const val PAST_SETTLE_MILLIS = 1_000L

/**
 * The intro and the tips as the user meets them.
 *
 * The intro is driven through [GuidedTourOverlay] with a state supplied directly; the tips through
 * [GuidedTourHost] with a real [GuidedTourController], because *when* a tip appears is decided by
 * the host from what is on screen, and that is the behaviour worth pinning.
 *
 * The narrow viewport is deliberate: a 4.5" phone, which is also what any phone becomes at the
 * largest Display Size setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class GuidedTourOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intro = GuidedTourState(phase = GuidedTourPhase.INTRO)

    @Test
    fun nothingIsDrawnOverTheAppWhileIdle() {
        setOverlay(GuidedTourState())

        composeRule.onNodeWithText("Ваше ТБ — у кишені").assertDoesNotExist()
        composeRule.onNodeWithText("Зрозуміло").assertDoesNotExist()
    }

    @Test
    fun theFirstPageSaysWhatTheAppIsForAndOffersSkipAndNext() {
        setOverlay(intro)

        composeRule.onNodeWithText("Ваше ТБ — у кишені").assertIsDisplayed()
        composeRule.onNodeWithText("Пропустити").assertIsDisplayed()
        composeRule.onNodeWithText("Далі").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("1 / 3").assertExists()
    }

    @Test
    fun nextAndSkipAreReported() {
        var next = 0
        var finished = 0
        setOverlay(intro, GuidedTourCallbacks(onIntroNext = { next++ }, onFinishIntro = { finished++ }))

        composeRule.onNodeWithText("Далі").performClick()
        composeRule.onNodeWithText("Пропустити").performClick()

        assertEquals(1, next)
        assertEquals(1, finished)
    }

    @Test
    fun theLastPageLeadsAnEmptyAppToAddingAPlaylist() {
        var added = 0
        var finished = 0
        setOverlay(
            intro.copy(introPage = 2),
            GuidedTourCallbacks(onAddPlaylist = { added++ }, onFinishIntro = { finished++ }),
        )

        composeRule.onNodeWithText("На великому екрані").assertIsDisplayed()
        composeRule.onNodeWithText("Пропустити").assertDoesNotExist()
        composeRule.onNodeWithText("Додати плейлист").performClick()
        composeRule.onNodeWithText("Пізніше").performClick()

        assertEquals(1, added)
        assertEquals(1, finished)
    }

    /** Opened again from Settings by someone who already has channels: adding another playlist is
     * not the next step for them, so the last page simply closes. */
    @Test
    fun theLastPageJustClosesForSomeoneWhoHasChannels() {
        var finished = 0
        setOverlay(intro.copy(introPage = 2), GuidedTourCallbacks(onFinishIntro = { finished++ }), hasChannels = true)

        composeRule.onNodeWithText("Додати плейлист").assertDoesNotExist()
        composeRule.onNodeWithText("Пізніше").assertDoesNotExist()
        composeRule.onNodeWithText("Почати користуватися").performClick()

        assertEquals(1, finished)
    }

    @Test
    fun aTipAppearsOnceItsElementIsOnScreenAndIsRememberedWhenDismissed() {
        val storage = InMemoryTourStorage()
        val controller = GuidedTourController(storage)
        setTips(controller, keys = listOf(GuidedTourKeys.CHANNEL_SEARCH))

        composeRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)
        composeRule.onNodeWithText("Знайдіть будь-який канал").assertIsDisplayed()

        composeRule.onNodeWithText("Зрозуміло").performClick()
        composeRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)

        composeRule.onNodeWithText("Знайдіть будь-який канал").assertDoesNotExist()
        assertEquals(setOf("search"), storage.guidedTipsSeen)
    }

    @Test
    fun aTapOutsideTheCardDismissesTheTipToo() {
        val storage = InMemoryTourStorage()
        val controller = GuidedTourController(storage)
        setTips(controller, keys = listOf(GuidedTourKeys.CHANNEL_SEARCH))
        composeRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)

        composeRule.onRoot().performTouchInput { click(Offset(width - 4f, 4f)) }
        composeRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)

        assertEquals(GuidedTourPhase.IDLE, controller.state.value.phase)
        assertEquals(setOf("search"), storage.guidedTipsSeen)
    }

    @Test
    fun noTipLandsOnTopOfADialogOrTheIntro() {
        val controller = GuidedTourController(InMemoryTourStorage())
        val enabled = mutableStateOf(false)
        setTips(controller, keys = listOf(GuidedTourKeys.CHANNEL_SEARCH), enabled = { enabled.value })

        composeRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)
        composeRule.onNodeWithText("Знайдіть будь-який канал").assertDoesNotExist()

        // The element arrived while blocked; it still counts as new once the block lifts.
        update { enabled.value = true }
        composeRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)
        composeRule.onNodeWithText("Знайдіть будь-який канал").assertIsDisplayed()
    }

    @Test
    fun playerTipsWaitForThePlayerPage() {
        val controller = GuidedTourController(InMemoryTourStorage())
        val expanded = mutableStateOf(false)
        setTips(controller, keys = listOf(GuidedTourKeys.PLAYER_VIDEO), playerExpanded = { expanded.value })

        composeRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)
        composeRule.onNodeWithText("Керування плеєром").assertDoesNotExist()

        update { expanded.value = true }
        composeRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)
        composeRule.onNodeWithText("Керування плеєром").assertIsDisplayed()
    }

    /**
     * The player page brings the video and the Cast buttons on screen together. One tip, then the
     * other on the *next* opening - not two cards back to back the first time someone presses play.
     */
    @Test
    fun oneTipPerAppearanceSoCastWaitsForTheNextOpening() {
        val controller = GuidedTourController(InMemoryTourStorage())
        val playerOpen = mutableStateOf(true)
        setTips(
            controller,
            keys = listOf(GuidedTourKeys.PLAYER_VIDEO, GuidedTourKeys.CAST_BUTTON),
            playerExpanded = { playerOpen.value },
            present = { playerOpen.value },
        )

        composeRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)
        assertEquals(GuidedTip.PLAYER, controller.state.value.tip)
        composeRule.onNodeWithText("Зрозуміло").performClick()
        composeRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)
        assertEquals(GuidedTourPhase.IDLE, controller.state.value.phase)

        update { playerOpen.value = false }
        composeRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)
        update { playerOpen.value = true }
        composeRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)

        assertEquals(GuidedTip.CAST, controller.state.value.tip)
        composeRule.onNodeWithText("Дивіться на телевізорі").assertIsDisplayed()
    }

    @Test
    fun anElementThatOnlyPassesThroughRaisesNoTip() {
        val controller = GuidedTourController(InMemoryTourStorage())
        val present = mutableStateOf(true)
        setTips(controller, keys = listOf(GuidedTourKeys.CHANNEL_SEARCH), present = { present.value })

        composeRule.mainClock.advanceTimeBy(200)
        update { present.value = false }
        composeRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)

        assertEquals(GuidedTourPhase.IDLE, controller.state.value.phase)
        assertTrue(GuidedTip.SEARCH in controller.pendingTips.value)
    }

    /** A state write from the test thread reaches the composition only once its snapshot is applied;
     * without this the clock below would run the pending tip's delay against the old state. */
    private fun update(block: () -> Unit) {
        block()
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun setOverlay(
        state: GuidedTourState,
        callbacks: GuidedTourCallbacks = GuidedTourCallbacks(),
        hasChannels: Boolean = false,
    ) {
        composeRule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                GuidedTourOverlay(state = state, callbacks = callbacks, hasChannels = hasChannels)
            }
        }
    }

    /** A host wired to [controller] the way `MainAppContent` wires it, over boxes registered under
     * [keys] while [present] says they are composed. */
    private fun setTips(
        controller: GuidedTourController,
        keys: List<String>,
        enabled: () -> Boolean = { true },
        playerExpanded: () -> Boolean = { false },
        present: () -> Boolean = { true },
    ) {
        val callbacks = GuidedTourCallbacks(
            onShowTip = { controller.showTip(it) },
            onDismissTip = controller::dismissTip,
        )
        composeRule.setContent {
            val state by controller.state.collectAsState()
            val pending by controller.pendingTips.collectAsState()
            ZorikTheme(AppTheme.CINEMA) {
                GuidedTourHost(
                    state = state,
                    callbacks = callbacks,
                    tips = GuidedTipConditions(pending, enabled(), playerExpanded()),
                ) {
                    Column {
                        if (present()) {
                            keys.forEach { key -> Box(Modifier.size(64.dp).guidedTourTarget(key)) }
                        }
                    }
                }
            }
        }
    }
}
