package com.uacastplayer.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedLogTailTest {

    @Test
    fun `retains only the newest lines without collecting the source`() {
        val tail = BoundedLogTail(maxLines = 3, maxChars = 100)

        repeat(10_000) { tail.add("line-$it") }

        assertEquals("line-9997\nline-9998\nline-9999", tail.contentOrNull())
        assertTrue(tail.retainedCharCount() <= 100)
    }

    @Test
    fun `character budget drops complete oldest lines`() {
        val tail = BoundedLogTail(maxLines = 10, maxChars = 11)

        tail.add("12345")
        tail.add("67890")
        tail.add("abcde")

        assertEquals("67890\nabcde", tail.contentOrNull())
        assertEquals(11, tail.retainedCharCount())
    }

    @Test
    fun `one oversized line keeps its newest tail`() {
        val tail = BoundedLogTail(maxLines = 10, maxChars = 5)

        tail.add("0123456789")

        assertEquals("56789", tail.contentOrNull())
        assertEquals(5, tail.retainedCharCount())
    }

    @Test
    fun `blank input produces no attachment body`() {
        val tail = BoundedLogTail(maxLines = 2, maxChars = 10)

        tail.add("")

        assertNull(tail.contentOrNull())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero line budget is refused`() {
        BoundedLogTail(maxLines = 0, maxChars = 10)
    }
}
