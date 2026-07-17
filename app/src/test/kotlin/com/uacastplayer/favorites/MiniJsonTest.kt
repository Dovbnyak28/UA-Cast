package com.uacastplayer.favorites

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
        Locale.setDefault(Locale("ar"))
        val objects = listOf(mapOf("name" to "BeforeAfter"))
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
        assertEquals(emptyList<Map<String, String?>>(), MiniJson.parseArrayOfObjects(MiniJson.writeArrayOfObjects(emptyList())))
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

    @Test
    fun `parses unicode escape sequences`() {
        val result = MiniJson.parseArrayOfObjects("""[{"a":"AB"}]""")
        assertEquals(listOf(mapOf("a" to "AB")), result)
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
    fun `handles a value containing brace and bracket characters inside a string`() {
        val objects = listOf(mapOf("weird" to "}],[{"))
        val json = MiniJson.writeArrayOfObjects(objects)
        assertEquals(objects, MiniJson.parseArrayOfObjects(json))
    }
}
