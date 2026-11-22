package app.zorik.player.data.favorites

import app.zorik.player.favorites.FavoriteChannel
import app.zorik.player.favorites.FavoriteKey
import app.zorik.player.playlist.M3uChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeFavoritesStorage(
    initial: List<FavoriteChannel>,
    private val loadGate: CompletableDeferred<Unit>,
) : FavoritesStorage {
    val loadStarted = CompletableDeferred<Unit>()
    var saved: List<FavoriteChannel> = initial
        private set

    override suspend fun load(): List<FavoriteChannel> {
        val snapshot = saved
        loadStarted.complete(Unit)
        loadGate.await()
        return snapshot
    }

    override suspend fun save(favorites: List<FavoriteChannel>) {
        saved = favorites
    }
}

class FavoritesRepositoryTest {

    @Test
    fun `late initial read merges a favorite added while disk IO was pending`() = runTest {
        val old = favorite(key = "old", streamUrl = "https://example.test/old")
        val channel = M3uChannel(
            displayName = "New",
            streamUrl = "https://example.test/new",
        )
        val loadGate = CompletableDeferred<Unit>()
        val storage = FakeFavoritesStorage(listOf(old), loadGate)
        val repository = FavoritesRepository(storage, backgroundScope)

        storage.loadStarted.await()
        repository.toggleFavorite(channel)
        loadGate.complete(Unit)
        testScheduler.runCurrent()

        assertEquals(
            setOf(old.key, FavoriteKey.of(channel)),
            repository.favorites.value.mapTo(mutableSetOf()) { it.key },
        )
        assertEquals(repository.favorites.value, storage.saved)
        assertTrue(repository.isFavorite(channel))
    }

    @Test
    fun `cancelling the owner scope stops the persistence actor`() = runTest {
        val storage = FakeFavoritesStorage(emptyList(), CompletableDeferred(Unit))
        val ownerJob = SupervisorJob()
        val ownerScope = CoroutineScope(ownerJob + StandardTestDispatcher(testScheduler))
        val repository = FavoritesRepository(storage, ownerScope)
        val channel = M3uChannel(displayName = "Late", streamUrl = "https://example.test/late")
        testScheduler.runCurrent()

        ownerJob.cancel()
        repository.toggleFavorite(channel)
        testScheduler.runCurrent()

        assertEquals(emptyList<FavoriteChannel>(), storage.saved)
    }

    @Test fun `undo writes the restored record and ordering to durable storage`() = runTest {
        val a = favorite("a", "https://example.test/a")
        val b = favorite("b", "https://example.test/b").copy(userAgent = "Agent", referrer = "Referrer")
        val storage = FakeFavoritesStorage(listOf(a, b), CompletableDeferred(Unit))
        val repository = FavoritesRepository(storage, backgroundScope)
        testScheduler.runCurrent()
        repository.removalUndo.removeWithUndo("b")
        val token = checkNotNull(repository.removalUndo.notice.value).token
        testScheduler.runCurrent()
        assertEquals(listOf(a), storage.saved)
        repository.removalUndo.undo(token)
        testScheduler.runCurrent()
        assertEquals(listOf(a, b), repository.favorites.value)
        assertEquals(listOf(a, b), storage.saved)
    }

    @Test fun `replacing favorites invalidates an earlier undo`() = runTest {
        val a = favorite("a", "https://example.test/a")
        val replacement = favorite("new", "https://example.test/new")
        val storage = FakeFavoritesStorage(listOf(a), CompletableDeferred(Unit))
        val repository = FavoritesRepository(storage, backgroundScope)
        testScheduler.runCurrent()
        repository.removalUndo.removeWithUndo("a")
        val token = checkNotNull(repository.removalUndo.notice.value).token
        repository.reorder(listOf(replacement))
        repository.removalUndo.undo(token)
        testScheduler.runCurrent()
        assertEquals(listOf(replacement), storage.saved)
    }

    private fun favorite(key: String, streamUrl: String) = FavoriteChannel(
        key = key,
        displayName = key,
        streamUrl = streamUrl,
        tvgId = null,
        groupTitle = null,
        addedAtMillis = 1L,
    )
}
