package com.uacastplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedPlayerRequestSaverTest {

    @Test
    fun restoresValidSavedRequest() {
        assertEquals(
            SavedPlayerRequest("channel-42", 7),
            SavedPlayerRequestSaver.restore(listOf("channel-42", 7)),
        )
    }

    @Test
    fun emptyStateMeansNoPendingRequest() {
        assertNull(SavedPlayerRequestSaver.restore(emptyList()))
    }

    @Test
    fun malformedStateIsDiscardedInsteadOfCrashing() {
        val malformed = listOf<List<Any>>(
            listOf("only-a-key"),
            listOf(42, 7),
            listOf("channel-42", "seven"),
            listOf("", 0),
            listOf("channel-42", -1),
        )

        malformed.forEach { saved ->
            assertNull("malformed saved state must be ignored: $saved", SavedPlayerRequestSaver.restore(saved))
        }
    }
}
