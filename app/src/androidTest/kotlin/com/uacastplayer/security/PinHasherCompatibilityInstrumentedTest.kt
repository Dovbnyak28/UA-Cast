package com.uacastplayer.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uacastplayer.core.security.PinHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinHasherCompatibilityInstrumentedTest {
    @Test fun existingSha256PinHashRemainsUsableOnEverySupportedAndroidVersion() {
        // Independently derived with OpenSSL at the existing 120,000-iteration work factor.
        val expected = "95c156af00455b2003522250f315f732fb5e28590ee253e259fb78054312aa78"
        assertEquals(expected, PinHasher.hash("1234", "fixed-salt"))
        assertTrue(PinHasher.verify("1234", "fixed-salt", expected))
        assertFalse(PinHasher.verify("4321", "fixed-salt", expected))
    }
}
