package com.uacastplayer.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.favorites.FavoritesStore
import com.uacastplayer.data.parentalcontrol.ParentalControlStore
import com.uacastplayer.data.playlist.GroupVisibilityStore
import com.uacastplayer.log.LogBuffer
import com.uacastplayer.log.LogLevel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val CORRUPT_CONTENT = "[]credential-like-content"

@RunWith(RobolectricTestRunner::class)
class CorruptJsonStoreDiagnosticsTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        LogBuffer.clear()
        storedFiles.forEach(::deleteAtomicFileParts)
    }

    @After
    fun tearDown() {
        storedFiles.forEach(::deleteAtomicFileParts)
        LogBuffer.clear()
    }

    @Test
    fun `corrupt favorites fall back safely and leave a sanitized warning`() = runBlocking {
        file(FAVORITES_FILE).writeText(CORRUPT_CONTENT)

        val loaded = FavoritesStore(application, Dispatchers.Unconfined).load()

        assertTrue(loaded.isEmpty())
        assertMalformedWarning("FavoritesStore")
    }

    @Test
    fun `corrupt parental locks fall back safely and leave a sanitized warning`() = runBlocking {
        file(PARENTAL_CONTROL_FILE).writeText(CORRUPT_CONTENT)

        val loaded = ParentalControlStore(application, Dispatchers.Unconfined).load()

        assertTrue(loaded.isEmpty())
        assertMalformedWarning("ParentalControlStore")
    }

    @Test
    fun `corrupt group visibility falls back safely and leaves a sanitized warning`() = runBlocking {
        file(GROUP_VISIBILITY_FILE).writeText(CORRUPT_CONTENT)

        val loaded = GroupVisibilityStore(application, Dispatchers.Unconfined).load()

        assertTrue(loaded.isEmpty())
        assertMalformedWarning("GroupVisibilityStore")
    }

    private fun assertMalformedWarning(expectedTag: String) {
        val entry = LogBuffer.snapshot().single()
        assertEquals(LogLevel.WARN, entry.level)
        assertEquals(expectedTag, entry.tag)
        assertTrue(entry.message.contains("malformed"))
        assertFalse(entry.message.contains(CORRUPT_CONTENT))
    }

    private val storedFiles: List<String>
        get() = listOf(FAVORITES_FILE, PARENTAL_CONTROL_FILE, GROUP_VISIBILITY_FILE)

    private fun file(name: String) = File(application.filesDir, name)

    private fun deleteAtomicFileParts(name: String) {
        file(name).delete()
        file("$name.bak").delete()
        file("$name.new").delete()
    }

    private companion object {
        const val FAVORITES_FILE = "favorites.json"
        const val PARENTAL_CONTROL_FILE = "parental_control_locked_channels.json"
        const val GROUP_VISIBILITY_FILE = "group_visibility.json"
    }
}
