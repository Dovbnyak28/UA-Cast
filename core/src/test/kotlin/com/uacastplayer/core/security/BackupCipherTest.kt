package com.uacastplayer.core.security

import java.util.concurrent.CancellationException
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCipherTest {
    private val password = "довга таємна фраза 🔐".toCharArray()

    @Test fun matchesIndependentJcaPbkdf2ForUnicodeAndMultipleWorkFactors() {
        val salt = ByteArray(16) { it.toByte() }
        for (rounds in listOf(1, 2, 4096)) {
            val spec = PBEKeySpec(password, salt, rounds, 256)
            val expected = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            assertArrayEquals(expected, BackupCipher.deriveKey(password, salt, rounds))
            spec.clearPassword()
        }
    }

    @Test fun portableRoundTripAndFreshSaltNonceHideCredentials() {
        val plain = "{\"token\":\"private-provider-password\"}".toByteArray()
        val first = BackupCipher.encrypt(plain, password)
        val second = BackupCipher.encrypt(plain, password)
        assertTrue(BackupCipher.isEncrypted(first))
        assertFalse(first.contentEquals(second))
        assertFalse(first.toString(Charsets.UTF_8).contains("private-provider-password"))
        assertArrayEquals(plain, BackupCipher.decrypt(first, password))
    }

    @Test fun wrongPasswordAndDamagedPayloadNeverExposePlaintext() {
        val encrypted = BackupCipher.encrypt("payload".toByteArray(), password)
        assertThrows(AEADBadTagException::class.java) {
            BackupCipher.decrypt(encrypted, "another long password".toCharArray())
        }
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { BackupCipher.decrypt(encrypted, password) }
    }

    @Test fun attackerWorkFactorAndFutureVersionRejectedBeforeKdf() {
        val encrypted = BackupCipher.encrypt(byteArrayOf(1), password)
        encrypted[8] = 2
        assertThrows(IllegalArgumentException::class.java) { BackupCipher.decrypt(encrypted, password) }
        encrypted[8] = 1
        encrypted[9] = 127
        assertThrows(IllegalArgumentException::class.java) { BackupCipher.decrypt(encrypted, password) }
    }

    @Test fun cancelledKdfStopsAndCallerPasswordIsNotMutated() {
        val original = password.copyOf()
        var checks = 0
        assertThrows(CancellationException::class.java) {
            BackupCipher.deriveKey(password, ByteArray(16), BackupCipher.ITERATIONS) {
                if (++checks == 2) throw CancellationException()
            }
        }
        assertEquals(2, checks)
        assertArrayEquals(original, password)
    }

    @Test fun invalidSizesAndWeakPasswordsRejected() {
        assertFalse(BackupCipher.acceptsPassword("123".toCharArray()))
        assertFalse(BackupCipher.acceptsPassword(" ".repeat(12).toCharArray()))
        assertThrows(IllegalArgumentException::class.java) { BackupCipher.decrypt(ByteArray(8), password) }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCipher.encrypt(ByteArray(BackupCipher.MAX_PLAINTEXT_BYTES + 1), password)
        }
    }

    @Test fun saltAndNonceAreAuthenticated() {
        val encrypted = BackupCipher.encrypt("credentials".toByteArray(), password)
        for (offset in listOf(13, 29)) {
            val damaged = encrypted.copyOf()
            damaged[offset] = (damaged[offset].toInt() xor 1).toByte()
            assertThrows(AEADBadTagException::class.java) { BackupCipher.decrypt(damaged, password) }
        }
    }
}
