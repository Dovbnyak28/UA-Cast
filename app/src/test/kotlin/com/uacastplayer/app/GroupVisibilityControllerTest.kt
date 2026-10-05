package com.uacastplayer.app

import com.uacastplayer.playlist.GroupVisibilityEntry
import com.uacastplayer.playlist.GroupVisibilityStorage
import com.uacastplayer.playlist.GroupVisibilityState
import com.uacastplayer.playlist.LEGACY_SOURCE_ID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** In-memory stand-in for [com.uacastplayer.data.playlist.GroupVisibilityStore], which needs a
 * `Context` and an `AtomicFile`. [saved] is what the controller last persisted, which several
 * tests assert on directly - a change that updates the exposed flows but never reaches the store
 * would be invisible until the next app start otherwise. */
private class FakeGroupVisibilityStorage(
    initial: List<GroupVisibilityEntry> = emptyList(),
    private val loadGate: CompletableDeferred<Unit>? = null,
) : GroupVisibilityStorage {
    var saved: List<GroupVisibilityEntry> = initial
        private set
    var saveCount: Int = 0
        private set

    override suspend fun load(): List<GroupVisibilityEntry> {
        val snapshot = saved
        loadGate?.await()
        return snapshot
    }

    override suspend fun save(entries: List<GroupVisibilityEntry>) {
        saved = entries
        saveCount++
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GroupVisibilityControllerTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun controller(storage: GroupVisibilityStorage) =
        GroupVisibilityController(storage, TestScope(dispatcher))

    @Test
    fun `late initial read cannot overwrite an override made while disk IO was pending`() = runTest(dispatcher) {
        val loadGate = CompletableDeferred<Unit>()
        val storage = FakeGroupVisibilityStorage(
            initial = listOf(GroupVisibilityEntry("source-a", "old", GroupVisibilityState.PINNED)),
            loadGate = loadGate,
        )
        val controller = controller(storage)
        controller.loadInitial()
        controller.setActiveSource("source-a")

        controller.pinGroup("new")
        loadGate.complete(Unit)

        assertEquals(setOf("old", "new"), controller.pinnedKeys.value)
        assertEquals(
            setOf(
                GroupVisibilityEntry("source-a", "old", GroupVisibilityState.PINNED),
                GroupVisibilityEntry("source-a", "new", GroupVisibilityState.PINNED),
            ),
            storage.saved.toSet(),
        )
    }

    @Test
    fun `pinning a group exposes it as pinned for the active source`() = runTest(dispatcher) {
        val storage = FakeGroupVisibilityStorage()
        val controller = controller(storage)
        controller.loadInitial()
        controller.setActiveSource("source-a")

        controller.pinGroup("movies")

        assertEquals(setOf("movies"), controller.pinnedKeys.value)
        assertEquals(emptySet<String>(), controller.hiddenKeys.value)
        assertEquals(listOf(GroupVisibilityEntry("source-a", "movies", GroupVisibilityState.PINNED)), storage.saved)
    }

    /** Pin and hide are the same slot, not two independent flags - hiding an already-pinned group
     * must replace the pin, not leave the group in both sets at once. */
    @Test
    fun `hiding an already-pinned group replaces the pin rather than adding to it`() = runTest(dispatcher) {
        val controller = controller(FakeGroupVisibilityStorage())
        controller.loadInitial()
        controller.setActiveSource("source-a")
        controller.pinGroup("movies")

        controller.hideGroup("movies")

        assertEquals(emptySet<String>(), controller.pinnedKeys.value)
        assertEquals(setOf("movies"), controller.hiddenKeys.value)
    }

    @Test
    fun `clearing an override restores a group to the default state`() = runTest(dispatcher) {
        val controller = controller(FakeGroupVisibilityStorage())
        controller.loadInitial()
        controller.setActiveSource("source-a")
        controller.hideGroup("news")

        controller.clearOverride("news")

        assertEquals(emptySet<String>(), controller.hiddenKeys.value)
        assertEquals(emptySet<String>(), controller.pinnedKeys.value)
    }

    /** The whole reason overrides are source-scoped: switching playlists must not carry one
     * playlist's pins into another, and must not lose them either. */
    @Test
    fun `switching the active source swaps which overrides are exposed`() = runTest(dispatcher) {
        val storage = FakeGroupVisibilityStorage(
            listOf(
                GroupVisibilityEntry("source-a", "movies", GroupVisibilityState.PINNED),
                GroupVisibilityEntry("source-b", "sports", GroupVisibilityState.PINNED),
                GroupVisibilityEntry("source-b", "news", GroupVisibilityState.HIDDEN),
            ),
        )
        val controller = controller(storage)
        controller.loadInitial()

        controller.setActiveSource("source-a")
        assertEquals(setOf("movies"), controller.pinnedKeys.value)
        assertEquals(emptySet<String>(), controller.hiddenKeys.value)

        controller.setActiveSource("source-b")
        assertEquals(setOf("sports"), controller.pinnedKeys.value)
        assertEquals(setOf("news"), controller.hiddenKeys.value)

        controller.setActiveSource("source-a")
        assertEquals(setOf("movies"), controller.pinnedKeys.value)
    }

    @Test
    fun `no source active means no overrides are exposed and edits are ignored`() = runTest(dispatcher) {
        val storage = FakeGroupVisibilityStorage(
            listOf(GroupVisibilityEntry("source-a", "movies", GroupVisibilityState.PINNED)),
        )
        val controller = controller(storage)
        controller.loadInitial()

        controller.setActiveSource(null)
        controller.pinGroup("sports")

        assertEquals(emptySet<String>(), controller.pinnedKeys.value)
        assertEquals(0, storage.saveCount)
    }

    /** See GroupVisibilityController.migrateLegacyEntries: a file written before overrides were
     * source-scoped has no source to attribute its entries to, so they adopt whichever source
     * connects first - the old format could only ever describe one playlist anyway. */
    @Test
    fun `legacy entries are adopted by the first source that becomes active`() = runTest(dispatcher) {
        val storage = FakeGroupVisibilityStorage(
            listOf(GroupVisibilityEntry(LEGACY_SOURCE_ID, "movies", GroupVisibilityState.PINNED)),
        )
        val controller = controller(storage)
        controller.loadInitial()

        controller.setActiveSource("source-a")

        assertEquals(setOf("movies"), controller.pinnedKeys.value)
        assertEquals(listOf(GroupVisibilityEntry("source-a", "movies", GroupVisibilityState.PINNED)), storage.saved)
    }

    /** The migration must be one-shot: re-running it on a later source switch would drag the same
     * entries from playlist to playlist every time the user changed source. */
    @Test
    fun `already-migrated entries do not follow a later source switch`() = runTest(dispatcher) {
        val storage = FakeGroupVisibilityStorage(
            listOf(GroupVisibilityEntry(LEGACY_SOURCE_ID, "movies", GroupVisibilityState.PINNED)),
        )
        val controller = controller(storage)
        controller.loadInitial()
        controller.setActiveSource("source-a")
        val saveCountAfterMigration = storage.saveCount

        controller.setActiveSource("source-b")

        assertEquals(emptySet<String>(), controller.pinnedKeys.value)
        assertEquals(saveCountAfterMigration, storage.saveCount)
        assertTrue(storage.saved.all { it.sourceId == "source-a" })
    }
}
