package com.uacastplayer.ui.cast

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.mediarouter.app.MediaRouteButton
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.uacastplayer.testing.RequiresComposeTestManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [CastButtonFactoryTestShadow::class])
@Category(RequiresComposeTestManifest::class)
class CastButtonInitializationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun moduleSetupUsesTheBackgroundExecutorAndApplicationContext() {
        rule.setContent { CastButton() }
        rule.waitForIdle()
        assertTrue(CastButtonFactoryTestShadow.finished.await(5, TimeUnit.SECONDS))
        assertEquals(0, CastButtonFactoryTestShadow.synchronousCalls)
        assertSame(Looper.getMainLooper().thread, CastButtonFactoryTestShadow.setupThread)
        assertTrue(CastButtonFactoryTestShadow.workerThread.get() !== Looper.getMainLooper().thread)
        assertSame(ApplicationProvider.getApplicationContext<Application>(), CastButtonFactoryTestShadow.context)
        assertTrue(CastButtonFactoryTestShadow.button?.context !== CastButtonFactoryTestShadow.context)
    }
}

@Implements(value = CastButtonFactory::class, isInAndroidSdk = false)
// Robolectric constructs the shadow even when the real API contains only static methods.
@Suppress("UtilityClassWithPublicConstructor")
class CastButtonFactoryTestShadow {
    companion object {
        val finished = CountDownLatch(1)
        val workerThread = AtomicReference<Thread?>()
        var synchronousCalls = 0
        var setupThread: Thread? = null
        var context: Context? = null
        var button: MediaRouteButton? = null

        @JvmStatic @Implementation
        fun setUpMediaRouteButton(context: Context, button: MediaRouteButton) {
            this.context = context
            this.button = button
            synchronousCalls++
        }

        @JvmStatic @Implementation
        fun setUpMediaRouteButton(context: Context, executor: Executor, button: MediaRouteButton): Task<Void> {
            this.context = context
            this.button = button
            setupThread = Thread.currentThread()
            executor.execute { workerThread.set(Thread.currentThread()); finished.countDown() }
            return Tasks.forResult(null)
        }
    }
}
