package com.uacastplayer.parentalcontrol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalControlPinPolicyTest {

    @Test
    fun `four digits is valid`() {
        assertTrue(ParentalControlPinPolicy.isValidFormat("1234"))
        assertTrue(ParentalControlPinPolicy.isValidFormat("0000"))
    }

    @Test
    fun `wrong length is invalid`() {
        assertFalse(ParentalControlPinPolicy.isValidFormat("123"))
        assertFalse(ParentalControlPinPolicy.isValidFormat("12345"))
        assertFalse(ParentalControlPinPolicy.isValidFormat(""))
    }

    @Test
    fun `non-digit characters are invalid`() {
        assertFalse(ParentalControlPinPolicy.isValidFormat("12a4"))
        assertFalse(ParentalControlPinPolicy.isValidFormat("12 4"))
        assertFalse(ParentalControlPinPolicy.isValidFormat("-123"))
    }
}
