package com.uacastplayer.ui.favorites

import com.uacastplayer.favorites.FavoriteChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteReorderStateTest {
    private val source = List(4) { FavoriteChannel("$it", "Channel $it", "https://example/$it", null, null) }

    @Test fun `cancel restores exact upstream order after multiple swaps`() {
        val state = FavoriteReorderState(source)
        state.rowHeight = 100f
        state.start(0)
        state.drag(220f)
        assertEquals(listOf("1", "2", "0", "3"), state.ordered.map { it.key })
        state.cancel()
        assertEquals(source, state.ordered)
        assertNull(state.draggedIndex)
        assertEquals(0f, state.offsetFor(0))
    }

    @Test fun `commit emits complete order once and resets drag state`() {
        val state = FavoriteReorderState(source)
        state.rowHeight = 100f
        state.start(3)
        state.drag(-1000f)
        var received: List<FavoriteChannel>? = null
        state.commit { received = it }
        assertEquals(listOf("3", "0", "1", "2"), received?.map { it.key })
        assertNull(state.draggedIndex)
    }

    @Test fun `empty or unmeasured list ignores impossible movement`() {
        val state = FavoriteReorderState(emptyList())
        state.start(0)
        state.drag(100f)
        state.cancel()
        assertEquals(emptyList<FavoriteChannel>(), state.ordered)
    }
}
