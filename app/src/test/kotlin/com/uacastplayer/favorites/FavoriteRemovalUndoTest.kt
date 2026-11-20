package com.uacastplayer.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteRemovalUndoTest {
    private fun item(key: String) = FavoriteChannel(key, key, "https://example.test/$key", key, "Group",
        addedAtMillis = 42L, tvgLogo = "https://example.test/logo", userAgent = "Agent", referrer = "Referrer")

    private class Fixture(initial: List<FavoriteChannel>) {
        var items = initial
        val undo = FavoriteRemovalUndo({ items }, { key -> items = items.filterNot { it.key == key } },
            { item, index -> items = items.toMutableList().apply { add(index, item) } })
    }

    @Test fun restoresTheExactRecordAndPositionWithoutDiscardingLaterAdditions() {
        val a = item("a"); val b = item("b"); val c = item("c"); val d = item("d")
        val fixture = Fixture(listOf(a, b, c))
        fixture.undo.removeWithUndo("b")
        val token = checkNotNull(fixture.undo.notice.value).token
        fixture.items = fixture.items + d
        fixture.undo.undo(token)
        assertEquals(listOf(a, b, c, d), fixture.items)
        assertNull(fixture.undo.notice.value)
    }

    @Test fun staleActionCannotRestoreAnEarlierRemovalDuringARapidBurst() {
        val fixture = Fixture(listOf(item("a"), item("b")))
        fixture.undo.removeWithUndo("a")
        val old = checkNotNull(fixture.undo.notice.value).token
        fixture.undo.removeWithUndo("b")
        val latest = checkNotNull(fixture.undo.notice.value).token
        fixture.undo.dismiss(old)
        fixture.undo.undo(old)
        assertEquals(emptyList<FavoriteChannel>(), fixture.items)
        fixture.undo.undo(latest)
        assertEquals(listOf(item("b")), fixture.items)
    }

    @Test fun newerReaddedMetadataIsNeverOverwrittenOrDuplicated() {
        val fixture = Fixture(listOf(item("a")))
        fixture.undo.removeWithUndo("a")
        val token = checkNotNull(fixture.undo.notice.value).token
        val newer = item("a").copy(userAgent = "New agent", addedAtMillis = 99)
        fixture.items = listOf(newer)
        fixture.undo.undo(token)
        assertEquals(listOf(newer), fixture.items)
    }

    @Test fun removedNeighborFallsBackToTheSurvivingPreviousItem() {
        val fixture = Fixture(listOf(item("a"), item("b"), item("c")))
        fixture.undo.removeWithUndo("b")
        val token = checkNotNull(fixture.undo.notice.value).token
        fixture.items = fixture.items.filterNot { it.key == "c" }
        fixture.undo.undo(token)
        assertEquals(listOf(item("a"), item("b")), fixture.items)
    }

    @Test fun dismissedOrUnknownActionsDoNotResurrectData() {
        val fixture = Fixture(listOf(item("a")))
        fixture.undo.removeWithUndo("missing")
        assertNull(fixture.undo.notice.value)
        fixture.undo.removeWithUndo("a")
        val token = checkNotNull(fixture.undo.notice.value).token
        fixture.undo.dismiss(token)
        fixture.undo.undo(token)
        assertEquals(emptyList<FavoriteChannel>(), fixture.items)
    }
}
