package com.uacastplayer.ui.favorites

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.ReorderPolicy

/** A drag is a UI transaction: commit emits a full order, cancellation restores the upstream snapshot. */
@Stable
internal class FavoriteReorderState(private val source: List<FavoriteChannel>) {
    val ordered = source.toMutableStateList()
    var draggedIndex by mutableStateOf<Int?>(null)
        private set
    private var offsetY by mutableFloatStateOf(0f)
    var rowHeight by mutableFloatStateOf(0f)

    fun start(index: Int) { draggedIndex = index; offsetY = 0f }

    fun offsetFor(index: Int): Float = if (draggedIndex == index) offsetY else 0f

    fun drag(deltaY: Float) {
        val from = draggedIndex ?: return
        if (ordered.isEmpty()) return
        offsetY += deltaY
        val target = (from + ReorderPolicy.indexDelta(offsetY, rowHeight)).coerceIn(ordered.indices)
        if (target != from) {
            val moved = ReorderPolicy.move(ordered.toList(), from, target)
            ordered.clear()
            ordered.addAll(moved)
            offsetY -= (target - from) * rowHeight
            draggedIndex = target
        }
    }

    fun commit(onReorder: (List<FavoriteChannel>) -> Unit) {
        draggedIndex = null
        offsetY = 0f
        onReorder(ordered.toList())
    }

    fun cancel() {
        draggedIndex = null
        offsetY = 0f
        ordered.clear()
        ordered.addAll(source)
    }
}

@Composable
internal fun rememberFavoriteReorderState(source: List<FavoriteChannel>): FavoriteReorderState =
    remember(source) { FavoriteReorderState(source) }

@Composable
internal fun Modifier.favoriteDrag(
    state: FavoriteReorderState,
    key: String,
    index: Int,
    enabled: Boolean,
    onReorder: (List<FavoriteChannel>) -> Unit,
): Modifier {
    val currentIndex by rememberUpdatedState(index)
    val commit by rememberUpdatedState(onReorder)
    return graphicsLayer { translationY = state.offsetFor(index) }
        .onSizeChanged { state.rowHeight = it.height.toFloat() }
        .then(if (enabled) Modifier.pointerInput(key, state) {
            detectDragGesturesAfterLongPress(
                onDragStart = { state.start(currentIndex) },
                onDragEnd = { state.commit(commit) },
                onDragCancel = state::cancel,
                onDrag = { change, delta -> change.consume(); state.drag(delta.y) },
            )
        } else Modifier)
}
