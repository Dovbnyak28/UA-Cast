package com.uacastplayer.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CastArtworkPolicyTest {
    private fun artwork(id: String?, packs: List<String> = emptyList()) =
        CastArtworkPolicy.artworkUrl(IconResolver.candidates(id, packs))

    @Test fun `a channel ID alone does not authorize automatic TV artwork`() {
        assertNull(artwork("ch1"))
    }

    @Test fun `the first explicitly added pack supplies receiver artwork`() {
        assertEquals(
            "https://first.example/logos/ch1.png",
            artwork("ch1", listOf("https://first.example/logos", "https://second.example")),
        )
    }

    @Test fun `a selected pack without channel ID has no TV artwork`() {
        assertNull(artwork(null, listOf("https://first.example/logos")))
    }

    @Test fun `empty ID does not create a guessed receiver URL`() {
        assertNull(artwork(" ", listOf("https://first.example/logos")))
    }

    @Test fun `invalid pack address is not passed to the receiver`() {
        assertNull(artwork("ch1", listOf("file:///private", "javascript:alert(1)")))
    }
}
