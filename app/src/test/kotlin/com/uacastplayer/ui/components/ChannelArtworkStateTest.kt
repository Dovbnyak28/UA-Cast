package com.uacastplayer.ui.components

import android.graphics.Bitmap
import android.content.Context
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoritesSortOrder
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.favorites.FavoritesScreen
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises actual file decoding and Compose producer replacement, not only cache lookups. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
@OptIn(DelicateCoilApi::class)
class ChannelArtworkStateTest {
    @get:Rule val rule = createComposeRule()
    @get:Rule val files = TemporaryFolder()

    private val first = M3uChannel("Alpha News", "https://unused.invalid/alpha")
    private val second = M3uChannel("Beta News", "https://unused.invalid/beta")
    private val decodeErrors = AtomicInteger()
    private val decodedImages = AtomicInteger()
    private lateinit var previousLoader: ImageLoader
    private lateinit var loader: ImageLoader

    @Before fun observeRealDecoder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        previousLoader = SingletonImageLoader.get(context)
        // Use Coil's real legacy decoder on the host; hardware ImageDecoder buffers require a device.
        loader = ImageLoader.Builder(context).components { add(BitmapFactoryDecoder.Factory()) }
            .allowHardware(false).eventListener(object : EventListener() {
            override fun onError(request: ImageRequest, result: ErrorResult) {
                decodeErrors.incrementAndGet()
            }
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { decodedImages.incrementAndGet() }
        }).build()
        SingletonImageLoader.setUnsafe(loader)
    }

    @After fun restoreLoader() {
        SingletonImageLoader.setUnsafe(previousLoader)
        loader.shutdown()
    }

    @Test fun changingChannelDoesNotDisplayPreviousLogoWhileResolving() {
        val logo = redLogo("first.png")
        val channel = mutableStateOf(first)
        val next = CompletableDeferred<File?>()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                ChannelIcon(channel.value, { if (it == first) logo else next.await() },
                    Modifier.testTag("logo"))
            }
        }
        awaitDecodedLogo()

        rule.runOnIdle { channel.value = second }
        rule.onNodeWithText("BN").assertIsDisplayed()
        rule.runOnIdle { next.complete(null) }
        rule.onNodeWithText("BN").assertIsDisplayed()
    }

    @Test fun refreshingRepairedFileAtSamePathRetriesFailedDecode() {
        val logo = files.newFile("repaired.png")
        val refresh = mutableStateOf(0)
        val resolutions = AtomicInteger()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                ChannelIcon(first, { resolutions.incrementAndGet(); logo }, Modifier.testTag("logo"),
                    refreshKey = refresh.value)
            }
        }
        // A corrupt file is a real Coil error. Wait past the initial unresolved placeholder.
        rule.waitUntil(5_000) { rule.waitForIdle(); decodeErrors.get() == 1 }
        rule.onNodeWithText("AN").assertIsDisplayed()

        writeRedLogo(logo)
        rule.runOnIdle { refresh.value++ }
        rule.runOnIdle { assertEquals("Refresh must re-resolve the file", 2, resolutions.get()) }
        awaitDecodedLogo()
        rule.onNodeWithText("AN").assertDoesNotExist()
    }

    @Test fun changingChannelDoesNotReusePreviousArtworkTone() {
        val logo = redLogo("tone.png")
        val channel = mutableStateOf(first)
        val next = CompletableDeferred<File?>()
        var observed: Color? = null
        rule.setContent {
            val tone = rememberArtworkTone(channel.value, { if (it == first) logo else next.await() })
            SideEffect { observed = tone }
        }
        rule.waitUntil(5_000) { rule.waitForIdle(); observed != null }
        rule.runOnIdle { assertNotNull(observed); channel.value = second }
        rule.runOnIdle { assertNull("Old channel tint must not survive a pending new logo", observed) }
        rule.runOnIdle { next.complete(null) }
        rule.runOnIdle { assertNull(observed) }
    }

    @Test fun favoritesRetryMissingLogosAfterPrefetchAndEpgRefresh() {
        val logo = redLogo("favorite.png")
        val refresh = mutableStateOf(0 to 0)
        val resolutions = AtomicInteger()
        val favorite = FavoriteChannel("alpha", first.displayName, first.streamUrl, null, null)
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                val key = refresh.value
                FavoritesScreen(
                    favorites = listOf(favorite), playlistChannels = listOf(first),
                    sortOrder = FavoritesSortOrder.MANUAL, onSortOrderSelected = {},
                    onChannelSelected = { _, _ -> }, onRemove = {}, onReorder = {}, onOpenChannels = {},
                    resolveIcon = { resolutions.incrementAndGet(); if (refresh.value.second > 0) logo else null },
                    iconRefreshKey = key,
                )
            }
        }
        rule.onNodeWithText("AN", useUnmergedTree = true).assertIsDisplayed()
        rule.runOnIdle { assertEquals(1, resolutions.get()); refresh.value = 0 to 1 }
        awaitDecodedLogo()
        rule.onNodeWithText("AN", useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle { assertEquals(2, resolutions.get()); refresh.value = 1 to 1 }
        rule.runOnIdle { assertEquals("EPG replacement must also refresh", 3, resolutions.get()) }
    }

    @Test fun ordinaryRecompositionDoesNotRetryCorruptLogoForever() {
        val logo = files.newFile("still-broken.png")
        val padding = mutableStateOf(0)
        val resolutions = AtomicInteger()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                ChannelIcon(first, { resolutions.incrementAndGet(); logo },
                    Modifier.padding(padding.value.dp), refreshKey = 1)
            }
        }
        rule.waitUntil(5_000) { rule.waitForIdle(); decodeErrors.get() == 1 }
        repeat(3) {
            rule.runOnIdle { padding.value++ }
            rule.onNodeWithText("AN").assertIsDisplayed()
        }
        rule.runOnIdle {
            assertEquals(1, resolutions.get())
            assertEquals(1, decodeErrors.get())
        }
    }

    @Test fun rapidChannelChangesCancelOldResolversAndIgnoreTheirLateResults() {
        val channel = mutableStateOf(first)
        val latest = M3uChannel("Gamma News", "https://unused.invalid/gamma")
        val pending = mapOf(first to CompletableDeferred<File?>(), second to CompletableDeferred())
        val started = mutableListOf<M3uChannel>()
        val retired = mutableListOf<M3uChannel>()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                ChannelIcon(channel.value, {
                    started += it
                    try { pending[it]?.await() } finally { retired += it }
                })
            }
        }
        rule.runOnIdle { assertEquals(listOf(first), started); channel.value = second }
        rule.runOnIdle { assertEquals(listOf(first), retired); channel.value = latest }
        rule.runOnIdle {
            assertEquals(listOf(first, second, latest), retired)
            pending.values.forEach { it.complete(redLogo("late-${it.hashCode()}.png")) }
        }
        rule.onNodeWithText("GN").assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, decodedImages.get()) }
    }

    private fun redLogo(name: String): File = files.newFile(name).also(::writeRedLogo)

    private fun writeRedLogo(file: File) {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            // Below ArtworkTonePolicy's near-white/value ceiling, with an unambiguous red hue.
            bitmap.eraseColor(0xFFE00000.toInt())
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun awaitDecodedLogo() {
        rule.waitUntil(5_000) {
            rule.waitForIdle()
            decodedImages.get() == 1
        }
    }
}
