package com.uacastplayer.data.favorites

import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.playlist.M3uChannel
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

    private fun favorite(key: String, streamUrl: String) = FavoriteChannel(
        key = key,
        displayName = key,
        streamUrl = streamUrl,
        tvgId = null,
        groupTitle = null,
        addedAtMillis = 1L,
    )
}
