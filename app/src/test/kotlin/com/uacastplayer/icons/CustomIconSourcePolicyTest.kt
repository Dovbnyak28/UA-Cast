package com.uacastplayer.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomIconSourcePolicyTest {

    @Test
    fun `canonicalizes whitespace, scheme case and trailing slash`() {
        assertEquals(
            "https://cdn.example.com/logos",
            CustomIconSourcePolicy.canonicalize("  HTTPS://cdn.example.com/logos/  "),
        )
    }

    @Test
    fun `rejects malformed or credential-bearing icon sources`() {
        listOf(
            "",
            "ftp://cdn.example.com/logos",
            "https:///missing-host",
            "https://user:password@cdn.example.com/logos",
            "https://cdn.example.com/logos?token=secret",
            "https://cdn.example.com/logos#fragment",
        ).forEach { value -> assertNull(value, CustomIconSourcePolicy.canonicalize(value)) }
    }

    @Test
    fun `rejects an overlong source`() {
        val value = "https://cdn.example.com/" + "x".repeat(CustomIconSourcePolicy.MAX_URL_LENGTH)
        assertNull(CustomIconSourcePolicy.canonicalize(value))
    }
}
