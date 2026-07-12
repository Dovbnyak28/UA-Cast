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

    init {
        scope.launch { _favorites.value = store.load() }
    }

    fun isFavorite(channel: M3uChannel): Boolean {
        val key = FavoriteKey.of(channel)
        return _favorites.value.any { it.key == key }
    }

    fun toggleFavorite(channel: M3uChannel) {
        val key = FavoriteKey.of(channel)
        val current = _favorites.value
        val updated = if (current.any { it.key == key }) {
            current.filterNot { it.key == key }
        } else {
            current + FavoriteChannel(key, channel.displayName, channel.streamUrl, channel.tvgId, channel.groupTitle)
        }
        _favorites.value = updated
        scope.launch { store.save(updated) }
    }

    fun remove(key: String) {
        val updated = _favorites.value.filterNot { it.key == key }
        _favorites.value = updated
        scope.launch { store.save(updated) }
    }
}
