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
}
