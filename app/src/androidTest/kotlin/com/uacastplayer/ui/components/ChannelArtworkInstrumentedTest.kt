package com.uacastplayer.ui.components

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoritesSortOrder
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.favorites.FavoritesScreen
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android/Coil decoders; isolated Activity content and owned temporary artwork only. */
@RunWith(AndroidJUnit4::class)
@OptIn(DelicateCoilApi::class)
class ChannelArtworkInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val channel = M3uChannel("Alpha News", "https://unused.invalid/alpha")
    private val temporaryFiles = mutableListOf<File>()
    private val errors = AtomicInteger()
    private val successes = AtomicInteger()
    private lateinit var previousLoader: ImageLoader
    private lateinit var loader: ImageLoader

    @Before fun debugOnlyDecoderObserver() {
        assertEquals("com.uacastplayer.debug", rule.activity.packageName)
        previousLoader = SingletonImageLoader.get(rule.activity)
        // Retain the app's real decoder configuration; only isolate caches and observe results.
        loader = previousLoader.newBuilder().memoryCache(null).diskCache(null)
            .eventListener(object : EventListener() {
                override fun onError(request: ImageRequest, result: ErrorResult) { errors.incrementAndGet() }
                override fun onSuccess(request: ImageRequest, result: SuccessResult) { successes.incrementAndGet() }
            }).build()
        SingletonImageLoader.setUnsafe(loader)
    }

    @After fun releaseFixture() {
        SingletonImageLoader.setUnsafe(previousLoader)
        loader.shutdown()
        temporaryFiles.forEach { it.delete() }
    }

    @Test fun channelSwitchClearsOldLogoWhileNextResolutionWaits() {
        val file = temporaryFile().also(::writeLogo)
        val current = mutableStateOf(channel)
        val pending = CompletableDeferred<File?>()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                ChannelIcon(current.value, { if (it == channel) file else pending.await() })
            }
        }
        awaitSuccessfulDecode()
        rule.runOnIdle { current.value = M3uChannel("Beta News", "https://unused.invalid/beta") }
        rule.onNodeWithText("BN").assertIsDisplayed()
        rule.runOnIdle { pending.complete(null) }
        rule.onNodeWithText("BN").assertIsDisplayed()
    }

    @Test fun repairedFileAtSamePathIsDecodedAfterRefresh() {
        val file = temporaryFile()
        val refresh = mutableStateOf(0)
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) { ChannelIcon(channel, { file }, refreshKey = refresh.value) }
        }
        rule.waitUntil(10_000) { errors.get() == 1 }
        rule.onNodeWithText("AN").assertIsDisplayed()
        writeLogo(file)
        rule.runOnIdle { refresh.value++ }
        awaitSuccessfulDecode()
        rule.onNodeWithText("AN").assertDoesNotExist()
    }

    @Test fun removingPackRevisionClearsAlreadyDecodedChannelLogo() {
        val file = temporaryFile().also(::writeLogo)
        val refresh = mutableStateOf(0L)
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                ChannelIcon(channel, { file.takeIf { refresh.value == 0L } }, refreshKey = refresh.value)
            }
        }
        awaitSuccessfulDecode()
        rule.runOnIdle { refresh.value++ }
        rule.onNodeWithText("AN").assertIsDisplayed()
    }

    @Test fun favoritesRefreshAfterArtworkArrivesWithoutReopeningScreen() {
        val file = temporaryFile().also(::writeLogo)
        val revision = mutableStateOf(0)
        val favorite = FavoriteChannel("alpha", channel.displayName, channel.streamUrl, null, null)
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                FavoritesScreen(
                    favorites = listOf(favorite), playlistChannels = listOf(channel),
                    sortOrder = FavoritesSortOrder.MANUAL, onSortOrderSelected = {},
                    onChannelSelected = { _, _ -> }, onRemove = {}, onReorder = {}, onOpenChannels = {},
                    resolveIcon = { file.takeIf { revision.value > 0 } }, iconRefreshKey = revision.value,
                )
            }
        }
        rule.onNodeWithText("AN", useUnmergedTree = true).assertIsDisplayed()
        rule.runOnIdle { revision.value++ }
        awaitSuccessfulDecode()
        rule.onNodeWithText("AN", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun channelSwitchClearsOldTintWhileNextResolutionWaits() {
        val file = temporaryFile().also(::writeLogo)
        val current = mutableStateOf(channel)
        val pending = CompletableDeferred<File?>()
        var tone: Color? = null
        rule.setContent {
            val value = rememberArtworkTone(current.value, { if (it == channel) file else pending.await() })
            SideEffect { tone = value }
        }
        rule.waitUntil(10_000) { tone != null }
        rule.runOnIdle { current.value = M3uChannel("Beta News", "https://unused.invalid/beta") }
        rule.runOnIdle { assertNull(tone); pending.complete(null) }
        rule.runOnIdle { assertNull(tone) }
    }

    private fun temporaryFile(): File = File.createTempFile("artwork-regression-", ".png", rule.activity.cacheDir)
        .also { temporaryFiles += it }

    private fun writeLogo(file: File) {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(0xFFE00000.toInt())
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun awaitSuccessfulDecode() {
        rule.waitUntil(10_000) { successes.get() == 1 }
    }
}
