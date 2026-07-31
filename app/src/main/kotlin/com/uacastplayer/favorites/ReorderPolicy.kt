package com.uacastplayer.favorites

import kotlin.math.roundToInt

/**
 * Pure list-reordering math for manual drag-to-reorder on the Favorites screen (MANUAL sort
 * order - see [FavoritesSorter]). Kept separate from the drag gesture handling in the UI so the
 * "move an item" and "how far did this drag go" logic is unit-testable without Compose's pointer
 * input APIs. Deliberately hand-rolled instead of pulling in a reorder library.
 */
object ReorderPolicy {

    /**
     * Moves the item at [fromIndex] to [toIndex], shifting the items in between. Out-of-range
     * indices return [items] unchanged rather than throwing, since a drag gesture can transiently
     * compute an index outside the list bounds (e.g. dragging past the first/last row).
     */
    fun <T> move(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
        if (fromIndex == toIndex) return items
        if (fromIndex !in items.indices || toIndex !in items.indices) return items
        val mutable = items.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }

    /**
     * Converts an accumulated vertical drag offset into a whole number of rows to move by,
     * rounding to the nearest row - a drag must cross a row's midpoint before it counts as a swap,
     * rather than swapping the instant the drag brushes the next row's edge.
     */
    fun indexDelta(dragOffsetPx: Float, rowHeightPx: Float): Int {
        if (rowHeightPx <= 0f) return 0
        return (dragOffsetPx / rowHeightPx).roundToInt()
    }
}
