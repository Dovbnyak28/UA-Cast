package com.uacastplayer.remote

import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.data.remote.RemoteCipher
import java.security.Security
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Measures the real platform implementation without contacting a renderer or changing app data. */
@RunWith(AndroidJUnit4::class)
class RemoteCryptoCompatibilityInstrumentedTest {
    @Test fun platformKeyDerivationProvidersProduceTheSameWireKey() {
        val salt = ByteArray(RemoteCipher.SALT_BYTES) { it.toByte() }
        val start = SystemClock.elapsedRealtime()
        val expected = RemoteCipher.key("12345678", salt).encoded
        assertEquals(32, expected.size)
        report("default=${SecretKeyFactory.getInstance(ALGORITHM).provider.name}, elapsedMs=${SystemClock.elapsedRealtime() - start}")
        Security.getProviders().filter { it.getService("SecretKeyFactory", ALGORITHM) != null }.forEach { provider ->
            val spec = PBEKeySpec("12345678".toCharArray(), salt, 160_000, 256)
            try {
                val providerStart = SystemClock.elapsedRealtime()
                val key = SecretKeyFactory.getInstance(ALGORITHM, provider).generateSecret(spec).encoded
                assertArrayEquals(expected, key)
                report("provider=${provider.name}, elapsedMs=${SystemClock.elapsedRealtime() - providerStart}")
            } finally { spec.clearPassword() }
        }
    }

    private fun report(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0,
            Bundle().apply { putString("stream", "\nREMOTE_CRYPTO $message\n") })
    }

    private companion object { const val ALGORITHM = "PBKDF2WithHmacSHA1" }
}
