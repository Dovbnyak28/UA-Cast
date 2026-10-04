package com.uacastplayer.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uacastplayer.core.security.BackupCipher
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android JCA provider, API 24-compatible KDF and AEAD; no user data or passwords. */
@RunWith(AndroidJUnit4::class)
class BackupCipherInstrumentedTest {
    @Test fun actualAndroidProviderRoundTripAndTamperingDetection() {
        val password = "synthetic test phrase only".toCharArray()
        val data = "portable Cyrillic Україна".toByteArray()
        val bytes = BackupCipher.encrypt(data, password)
        assertArrayEquals(data, BackupCipher.decrypt(bytes, password))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { BackupCipher.decrypt(bytes, password) }
    }
}
