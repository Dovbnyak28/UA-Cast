package com.uacastplayer.data.favorites

import android.content.Context
import com.uacastplayer.core.concurrent.LatestValueWriter
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.M3uChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "FavoritesRepository"

class FavoritesRepository internal constructor(
    private val store: FavoritesStorage,
    private val scope: CoroutineScope,
) {
    /** The caller owns [scope]. A repository contains a long-lived writer actor, so silently
     * creating a detached scope here would retain the repository after its UI owner is cleared. */
    constructor(context: Context, scope: CoroutineScope) : this(
        store = FavoritesStore(context.applicationContext),
        scope = scope,
    )

    private val writer = LatestValueWriter(scope, store::save) { error ->
        AppLog.w(TAG) { "Favorites persistence failed: ${error.javaClass.simpleName}" }
    }
    private var initialLoadFinished = false
    private val pendingOverrides = linkedMapOf<String, FavoriteChannel?>()
    private var pendingReplacement: List<FavoriteChannel>? = null

    private val _favorites = MutableStateFlow<List<FavoriteChannel>>(emptyList())
    val favorites: StateFlow<List<FavoriteChannel>> = _favorites.asStateFlow()

    // Membership index over _favorites, rebuilt on every mutation. [isFavorite] is called once per
    // channel row on every recomposition of a list (see ChannelRow/ChannelTile), which made the
    // list scan it used to do O(favorites) per row per frame - and the favorites list is
    // user-grown and unbounded. The list itself stays the source of truth: it carries the manual
    // sort order (see [reorder]), which a Set cannot.
    @Volatile private var favoriteKeys: Set<String> = emptySet()

    private fun updateState(updated: List<FavoriteChannel>) {
        favoriteKeys = updated.mapTo(HashSet(updated.size)) { it.key }
        _favorites.value = updated
    }

    private fun publishOverride(updated: List<FavoriteChannel>, key: String, value: FavoriteChannel?) {
        if (initialLoadFinished) {
            writer.submit(updated)
        } else if (pendingReplacement != null) {
            pendingReplacement = updated
        } else {
            pendingOverrides[key] = value
        }
        updateState(updated)
    }

    private fun publishReplacement(updated: List<FavoriteChannel>) {
        if (initialLoadFinished) {
            writer.submit(updated)
        } else {
            pendingReplacement = updated
            pendingOverrides.clear()
        }
        updateState(updated)
        // Fire-and-forget, and safe to be: the store reports a failed write rather than throwing
        // into the writer actor (see com.uacastplayer.data.writeSafely). The list above is already
        // published either way.
    }

    init {
        scope.launch {
            val loaded = store.load()
            val hadPendingMutation = pendingReplacement != null || pendingOverrides.isNotEmpty()
            val resolved = pendingReplacement ?: loaded.toMutableList().apply {
                for ((key, override) in pendingOverrides) {
                    removeAll { it.key == key }
                    if (override != null) add(override)
                }
            }
            pendingReplacement = null
            pendingOverrides.clear()
            initialLoadFinished = true
            updateState(resolved)
            if (hadPendingMutation) writer.submit(resolved)
        }
    }

    fun isFavorite(channel: M3uChannel): Boolean = FavoriteKey.of(channel) in favoriteKeys

    fun toggleFavorite(channel: M3uChannel) {
        val key = FavoriteKey.of(channel)
        val current = _favorites.value
        val added: FavoriteChannel?
        val updated = if (key in favoriteKeys) {
            added = null
            current.filterNot { it.key == key }
        } else {
            added = FavoriteChannel(
                key = key,
                displayName = channel.displayName,
                streamUrl = channel.streamUrl,
                tvgId = channel.tvgId,
                groupTitle = channel.groupTitle,
                addedAtMillis = System.currentTimeMillis(),
            )
            current + added
        }
        publishOverride(updated, key, added)
    }

    fun remove(key: String) {
        publishOverride(_favorites.value.filterNot { it.key == key }, key, null)
    }

    /** Persists a manually reordered favorites list (see MANUAL sort order / ReorderPolicy) - the
     * list order itself doubles as the stored order, so this just replaces it wholesale. */
    fun reorder(newOrder: List<FavoriteChannel>) {
        publishReplacement(newOrder)
    }
}
