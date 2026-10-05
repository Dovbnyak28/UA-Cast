package com.uacastplayer.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `verify succeeds for the pin the hash was generated from`() {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash("1234", salt)
        assertTrue(PinHasher.verify("1234", salt, hash))
    }

    @Test
    fun `verify fails for a different pin`() {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash("1234", salt)
        assertFalse(PinHasher.verify("4321", salt, hash))
    }

    @Test
    fun `verify fails when the salt does not match`() {
        val hash = PinHasher.hash("1234", PinHasher.generateSalt())
        assertFalse(PinHasher.verify("1234", PinHasher.generateSalt(), hash))
    }

    @Test
    fun `same pin and salt hash deterministically`() {
        val salt = PinHasher.generateSalt()
        assertEquals(PinHasher.hash("1234", salt), PinHasher.hash("1234", salt))
    }

    @Test
    fun `same pin with different salts hashes differently`() {
        val hashA = PinHasher.hash("1234", PinHasher.generateSalt())
        val hashB = PinHasher.hash("1234", PinHasher.generateSalt())
        assertNotEquals(hashA, hashB)
    }

    @Test
    fun `generateSalt produces distinct values`() {
        assertNotEquals(PinHasher.generateSalt(), PinHasher.generateSalt())
    }

    @Test
    fun `hash output is lowercase hex`() {
        val hash = PinHasher.hash("1234", PinHasher.generateSalt())
        assertEquals(hash, hash.lowercase())
        assertTrue(hash.all { it in "0123456789abcdef" })
    }
}
