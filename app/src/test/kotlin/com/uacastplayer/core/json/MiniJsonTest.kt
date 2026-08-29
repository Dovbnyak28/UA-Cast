package com.uacastplayer.core.json

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    private val originalLocale: Locale = Locale.getDefault()

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `round-trips a control character under a non-ASCII-digit default locale`() {
        // Arabic uses Arabic-indic digits by default - "%04x".format(...) without Locale.ROOT
        // would render the escape's hex digits in that script instead of ASCII, corrupting the
        // JSON this method itself has to be able to parse back.
        Locale.setDefault(Locale.forLanguageTag("ar"))
        val objects = listOf(mapOf("name" to "Before\u0001After"))
        val json = MiniJson.writeArrayOfObjects(objects)
        assertEquals(objects, MiniJson.parseArrayOfObjects(json))
    }

    @Test
    fun `round-trips a simple object`() {
        val objects = listOf(mapOf("a" to "1", "b" to "2"))
        val json = MiniJson.writeArrayOfObjects(objects)
        assertEquals(objects, MiniJson.parseArrayOfObjects(json))
    }

    @Test
    fun `round-trips null values`() {
        val objects = listOf(mapOf("a" to "1", "b" to null))
        val json = MiniJson.writeArrayOfObjects(objects)
        assertEquals(objects, MiniJson.parseArrayOfObjects(json))
    }

    @Test
    fun `round-trips an empty array`() {
        assertEquals(
            emptyList<Map<String, String?>>(),
            MiniJson.parseArrayOfObjects(MiniJson.writeArrayOfObjects(emptyList())),
        )
    }

    @Test
    fun `round-trips special characters requiring escaping`() {
        val objects = listOf(mapOf("name" to "Quote\" Backslash\\ Newline\n Tab\t"))
        val json = MiniJson.writeArrayOfObjects(objects)
        assertEquals(objects, MiniJson.parseArrayOfObjects(json))
    }

    @Test
    fun `round-trips unicode content`() {
        val objects = listOf(mapOf("name" to "Канал Плюс 1"))
        val json = MiniJson.writeArrayOfObjects(objects)
        assertEquals(objects, MiniJson.parseArrayOfObjects(json))
    }

    @Test
    fun `round-trips multiple objects preserving order`() {
        val objects = listOf(mapOf("k" to "1"), mapOf("k" to "2"), mapOf("k" to "3"))
        val json = MiniJson.writeArrayOfObjects(objects)
        assertEquals(objects, MiniJson.parseArrayOfObjects(json))
    }

    /** The input is a regular escaped string, not a raw one: the doubled backslash is what makes
     * the parser receive the two characters `\` and `u`. This test previously used a raw string
     * whose escape had been flattened away, so its input was the literal `AB` and it exercised no
     * escape handling at all while still passing. */
    @Test
    fun `parses unicode escape sequences`() {
        val result = MiniJson.parseArrayOfObjects("[{\"a\":\"\\u0041\\u0042\"}]")
        assertEquals(listOf(mapOf("a" to "AB")), result)
    }

    @Test
    fun `parses a unicode escape for a non-ASCII character`() {
        val result = MiniJson.parseArrayOfObjects("[{\"a\":\"b\\u00e9c\"}]")
        assertEquals(listOf(mapOf("a" to "béc")), result)
    }

    @Test
    fun `throws on malformed input`() {
        assertThrows(IllegalArgumentException::class.java) { MiniJson.parseArrayOfObjects("not json") }
    }

    @Test
    fun `throws on truncated input`() {
        assertThrows(IllegalArgumentException::class.java) { MiniJson.parseArrayOfObjects("""[{"a":"1"""") }
    }

    @Test
    fun `throws on trailing content after an empty or populated array`() {
        listOf("[]garbage", """[{"a":"1"}]garbage""").forEach { json ->
            assertThrows(IllegalArgumentException::class.java) { MiniJson.parseArrayOfObjects(json) }
        }
    }

    @Test
    fun `allows whitespace after the complete document`() {
        assertEquals(emptyList<Map<String, String?>>(), MiniJson.parseArrayOfObjects("[] \r\n\t"))
    }

    @Test
    fun `throws on unsupported escape sequences`() {
        assertThrows(IllegalArgumentException::class.java) {
            MiniJson.parseArrayOfObjects("""[{"a":"bad\qescape"}]""")
        }
    }

    @Test
    fun `throws on a raw control character inside a string`() {
        assertThrows(IllegalArgumentException::class.java) {
            MiniJson.parseArrayOfObjects("[{\"a\":\"before\u0001after\"}]")
        }
    }

    @Test
    fun `handles a value containing brace and bracket characters inside a string`() {
        val objects = listOf(mapOf("weird" to "}],[{"))
        val json = MiniJson.writeArrayOfObjects(objects)
        assertEquals(objects, MiniJson.parseArrayOfObjects(json))
    }

    /** Malformed hex used to surface as a bare NumberFormatException from toInt(16) rather than
     * this parser's own positioned error - never a crash, since every caller catches broadly, but
     * useless for telling which byte of a corrupt file is at fault. */
    @Test
    fun `reports a malformed unicode escape as a parse error with a position`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MiniJson.parseArrayOfObjects("""[{"a":"\uZZZZ"}]""")
        }
        assertTrue("message was: ${error.message}", error.message.orEmpty().contains("position"))
    }

    @Test
    fun `reports a truncated unicode escape as a parse error`() {
        assertThrows(IllegalArgumentException::class.java) {
            MiniJson.parseArrayOfObjects("""[{"a":"\u00""")
        }
    }
}
