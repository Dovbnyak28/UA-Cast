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
        var result: Int? = null
        var index = currentIndex
        var checked = 0
        while (checked < count && result == null) {
            val candidate = nextIndex(index, count, wrapAround)
            if (candidate == null) {
                checked = count
            } else {
                if (!isDead(candidate)) result = candidate
                index = candidate
                checked++
            }
        }
        return result
    }
}
