package com.uacastplayer.data.icons

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IconEmptyResolutionTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun resetSources() {
        app.getSharedPreferences("custom_icon_sources", Application.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `no pack does not dispatch a separate IO task for every channel`() = runBlocking {
        val dispatcher = CountingDispatcher()
        val repository = IconRepository(app, dispatcher)
        repeat(1_024) { assertNull(repository.resolveIconFile("channel-$it")) }
        assertEquals(0, dispatcher.dispatches.get())
    }

    @Test fun `missing channel IDs do not dispatch IO even with a configured pack`() = runBlocking {
        val dispatcher = CountingDispatcher()
        val repository = IconRepository(app, dispatcher)
        repository.addCustomIconSource("https://unused.example.test/logos")
        listOf(null, "", "   ").forEach { assertNull(repository.resolveIconFile(it)) }
        assertEquals(0, dispatcher.dispatches.get())
    }

    private class CountingDispatcher : CoroutineDispatcher() {
        val dispatches = AtomicInteger()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches.incrementAndGet()
            Dispatchers.Default.dispatch(context, block)
        }
    }
}
