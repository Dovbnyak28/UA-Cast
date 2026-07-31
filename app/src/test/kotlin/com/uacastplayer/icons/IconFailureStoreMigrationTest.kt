package com.uacastplayer.icons

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconFailureStoreMigrationTest {

    @Test
    fun `missing stored version needs a clear`() {
        assertTrue(IconFailureStoreMigration.shouldClearPermanentFailures(storedVersion = null, currentVersion = 1))
    }

    @Test
    fun `stored version behind current needs a clear`() {
        assertTrue(IconFailureStoreMigration.shouldClearPermanentFailures(storedVersion = 1, currentVersion = 2))
    }

    @Test
    fun `stored version matching current does not need a clear`() {
        assertFalse(IconFailureStoreMigration.shouldClearPermanentFailures(storedVersion = 2, currentVersion = 2))
    }

    @Test
    fun `stored version ahead of current does not need a clear`() {
        assertFalse(IconFailureStoreMigration.shouldClearPermanentFailures(storedVersion = 3, currentVersion = 2))
    }
}
