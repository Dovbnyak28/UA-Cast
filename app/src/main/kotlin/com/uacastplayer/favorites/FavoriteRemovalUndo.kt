package com.uacastplayer.favorites

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FavoriteRemovalNotice(val token: Long, val displayName: String)

/** One pending UI action. The repository owns the data; the accessible snackbar owns its timeout. */
class FavoriteRemovalUndo(
    private val current: () -> List<FavoriteChannel>,
    private val remove: (String) -> Unit,
    private val restore: (FavoriteChannel, Int) -> Unit,
) {
    private data class Removed(
        val item: FavoriteChannel, val index: Int, val nextKey: String?, val previousKey: String?,
    )
    private var removed: Removed? = null
    private var generation = 0L
    private val mutableNotice = MutableStateFlow<FavoriteRemovalNotice?>(null)
    val notice = mutableNotice.asStateFlow()

    fun removeWithUndo(key: String) {
        val before = current()
        val index = before.indexOfFirst { it.key == key }
        if (index < 0) return
        val snapshot = Removed(before[index], index, before.getOrNull(index + 1)?.key, before.getOrNull(index - 1)?.key)
        remove(key)
        removed = snapshot
        mutableNotice.value = FavoriteRemovalNotice(++generation, snapshot.item.displayName)
    }

    fun undo(token: Long) {
        val snapshot = removed?.takeIf { mutableNotice.value?.token == token } ?: return
        dismiss(token)
        val items = current()
        if (items.none { it.key == snapshot.item.key }) {
            val next = items.indexOfFirst { it.key == snapshot.nextKey }
            val previous = items.indexOfFirst { it.key == snapshot.previousKey }
            val index = when {
                next >= 0 -> next
                previous >= 0 -> previous + 1
                else -> snapshot.index.coerceAtMost(items.size)
            }
            restore(snapshot.item, index)
        }
    }

    fun dismiss(token: Long? = mutableNotice.value?.token) {
        if (mutableNotice.value?.token != token) return
        removed = null
        mutableNotice.value = null
    }

    fun forgetIfKeyChanged(key: String) {
        if (removed?.item?.key == key) dismiss()
    }
}
