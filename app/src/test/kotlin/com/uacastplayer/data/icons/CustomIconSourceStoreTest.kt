package com.uacastplayer.data.icons

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.icons.CustomIconSourcePolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CustomIconSourceStoreTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    @After
    fun clearPreferences() {
        application.getSharedPreferences("custom_icon_sources", Application.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `storage drops malformed duplicates and caps source count`() {
        val urls = buildList {
            add("not-a-url")
            add(" HTTPS://cdn.example.com/logos/ ")
            add("https://cdn.example.com/logos")
            repeat(CustomIconSourcePolicy.MAX_SOURCES + 4) { index ->
                add("https://cdn$index.example.com/logos/")
            }
        }

        val store = CustomIconSourceStore(application)
        store.saveBaseUrls(urls)
        val restored = store.getBaseUrls()

        assertEquals(CustomIconSourcePolicy.MAX_SOURCES, restored.size)
        assertEquals("https://cdn.example.com/logos", restored.first())
        assertTrue(restored.all { it.startsWith("https://") && !it.endsWith('/') })
    }
}
