package com.uacastplayer.player

/**
 * Pure index arithmetic for moving between channels: plain next/previous with an optional
 * wrap-around, and an auto-skip variant that walks past channels a [isDead] predicate flags as
 * unplayable, stopping (rather than looping forever) once every channel has been tried.
 */
object ChannelNavigator {

    fun nextIndex(currentIndex: Int, count: Int, wrapAround: Boolean): Int? {
        if (count == 0) return null
        val next = currentIndex + 1
        return when {
            next < count -> next
            wrapAround -> 0
            else -> null
        }
    }

    fun previousIndex(currentIndex: Int, count: Int, wrapAround: Boolean): Int? {
        if (count == 0) return null
        val previous = currentIndex - 1
        return when {
            previous >= 0 -> previous
            wrapAround -> count - 1
            else -> null
        }
    }

    fun nextPlayableIndex(
        currentIndex: Int,
        count: Int,
        wrapAround: Boolean,
        isDead: (Int) -> Boolean,
    ): Int? {
        if (count == 0) return null
        var index = currentIndex
        repeat(count) {
            val candidate = nextIndex(index, count, wrapAround) ?: return null
            if (!isDead(candidate)) return candidate
            index = candidate
        }
        return null
    }
}
