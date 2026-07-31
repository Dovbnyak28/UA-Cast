package com.uacastplayer.data.favorites

import android.content.Context
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.playlist.M3uChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FavoritesRepository(context: Context) {

    private val store = FavoritesStore(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _favorites = MutableStateFlow<List<FavoriteChannel>>(emptyList())
    val favorites: StateFlow<List<FavoriteChannel>> = _favorites.asStateFlow()

    // Membership index over _favorites, rebuilt on every mutation. [isFavorite] is called once per
    // channel row on every recomposition of a list (see ChannelRow/ChannelTile), which made the
    // list scan it used to do O(favorites) per row per frame - and the favorites list is
    // user-grown and unbounded. The list itself stays the source of truth: it carries the manual
    // sort order (see [reorder]), which a Set cannot.
    @Volatile private var favoriteKeys: Set<String> = emptySet()

    private fun publish(updated: List<FavoriteChannel>) {
        favoriteKeys = updated.mapTo(HashSet(updated.size)) { it.key }
        _favorites.value = updated
        scope.launch { store.save(updated) }
    }

    init {
        scope.launch {
            val loaded = store.load()
            favoriteKeys = loaded.mapTo(HashSet(loaded.size)) { it.key }
            _favorites.value = loaded
        }
    }

    fun isFavorite(channel: M3uChannel): Boolean = FavoriteKey.of(channel) in favoriteKeys

    fun toggleFavorite(channel: M3uChannel) {
        val key = FavoriteKey.of(channel)
        val current = _favorites.value
        val updated = if (key in favoriteKeys) {
            current.filterNot { it.key == key }
        } else {
            current + FavoriteChannel(
                key = key,
                displayName = channel.displayName,
                streamUrl = channel.streamUrl,
                tvgId = channel.tvgId,
                groupTitle = channel.groupTitle,
                addedAtMillis = System.currentTimeMillis(),
            )
        }
        publish(updated)
    }

    fun remove(key: String) {
        publish(_favorites.value.filterNot { it.key == key })
    }

    /** Persists a manually reordered favorites list (see MANUAL sort order / ReorderPolicy) - the
     * list order itself doubles as the stored order, so this just replaces it wholesale. */
    fun reorder(newOrder: List<FavoriteChannel>) {
        publish(newOrder)
    }
}
