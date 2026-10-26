package com.uacastplayer.playlist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextCredentialPolicyTest {
    @Test fun `https protects credentials in transit`() =
        assertFalse(CleartextCredentialPolicy.exposesCredentials("https://provider.example"))

    @Test fun `http exposes credentials in transit`() =
        assertTrue(CleartextCredentialPolicy.exposesCredentials("http://provider.example:8080"))

    @Test fun `a bare Xtream address becomes http and therefore exposes credentials`() =
        assertTrue(CleartextCredentialPolicy.exposesCredentials("provider.example:8080"))

    @Test fun `an empty field is not warned before the user enters a server`() =
        assertFalse(CleartextCredentialPolicy.exposesCredentials("  "))

    @Test fun `direct http playlist is reported as cleartext`() =
        assertTrue(CleartextCredentialPolicy.isCleartextPlaylistUrl(" HTTP://provider.example/list.m3u "))

    @Test fun `direct https playlist is not reported as cleartext`() =
        assertFalse(CleartextCredentialPolicy.isCleartextPlaylistUrl("https://provider.example/list.m3u"))

    @Test fun `bare direct playlist input is left to the URL validator`() =
        assertFalse(CleartextCredentialPolicy.isCleartextPlaylistUrl("provider.example/list.m3u"))
}
