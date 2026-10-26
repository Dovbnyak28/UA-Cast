package com.uacastplayer.player

import android.app.Application
import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.asAndroidBitmap
import com.uacastplayer.ui.player.InlinePlayerContent
import com.uacastplayer.ui.player.PlayerScreenContent
import com.uacastplayer.ui.player.PlayerScreenActions
import com.uacastplayer.ui.player.PlayerScreenTransientState
import com.uacastplayer.ui.player.PlayerActionsSheet
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.dlna.DlnaConnectionState
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uacastplayer.data.cast.ProxyServer
import com.uacastplayer.R
import com.uacastplayer.cast.CastChannel
import com.uacastplayer.cast.CastMediaLoader
import com.uacastplayer.cast.CastStatusContentPolicy
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.uacastplayer.data.media.AndroidLocalVideoReader
import com.uacastplayer.data.media.AndroidLocalVideoSource
import com.uacastplayer.data.media.LocalVideoHandle
import com.uacastplayer.data.media.LocalVideoSource
import com.uacastplayer.media.LocalVideo
import com.uacastplayer.media.LocalVideoFormats
import com.uacastplayer.dlna.UpnpFault
import com.uacastplayer.dlna.VolumeResponseParser
import com.uacastplayer.dlna.DlnaTransportStateReader
import com.uacastplayer.dlna.DlnaTransportHealth
import com.uacastplayer.dlna.AvTransportSoapBuilder
import com.uacastplayer.data.prefs.AppPreferences
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Generates only a tiny silent clip in its own cache file; no private playlists/media are used. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class LocalVideoInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val store = ViewModelStore()
    private lateinit var file: File
    private lateinit var video: LocalVideo
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun generateVideo() {
        file = File(app.cacheDir, "diagnostics/local-video-${UUID.randomUUID()}.mp4")
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        SyntheticVideoEncoder.encode(file)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.diagnostics", file)
        video = runBlocking { AndroidLocalVideoReader(app).read(uri.toString(), persist = false) }
        assertEquals("video/mp4", video.mimeType)
        assertFalse(video.canRestore)
    }

    @After fun release() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        PlaybackActivity.setActive(false)
        if (::file.isInitialized) file.delete()
    }

    @Test fun selectedDocumentActuallyPlaysAndSeeksOnMedia3WithoutReplacingTheLastChannel() {
        val preferences = AppPreferences(app)
        val previousKey = preferences.lastWatchedChannelKey
        val playerModel = startVideo()
        rule.waitUntil(15_000) { playerModel.uiState.value.isPlaying || playerModel.uiState.value.fatalError }
        assertFalse(playerModel.uiState.value.fatalError)
        assertTrue(playerModel.uiState.value.isPlaying)
        assertTrue(playerModel.uiState.value.canSeek)
        assertEquals(previousKey, preferences.lastWatchedChannelKey)
        rule.runOnUiThread { playerModel.player.seekTo(500) }
        rule.waitUntil(5000) {
            var position = 0L
            rule.runOnUiThread { position = playerModel.player.currentPosition }
            position >= 500
        }
        rule.runOnUiThread {
            playerModel.releasePlayback()
            assertEquals(0, playerModel.player.mediaItemCount)
            assertEquals(Player.STATE_IDLE, playerModel.player.playbackState)
            assertFalse(playerModel.player.isPlaying)
        }
    }

    @Test fun naturalCompletionOffersReplayAndRestartsOnOneTap() {
        val model = startVideo()
        awaitCompletion(model)
        rule.runOnUiThread {
            assertTrue(model.uiState.value.canControlPlayback)
            assertFalse("A completed file must offer Play, not Pause", model.uiState.value.wantsToPlay)
            model.onEnterBackground(false)
            model.onReturnToForeground()
            assertEquals(Player.STATE_ENDED, model.player.playbackState)
            assertFalse("Returning to the app must not restart a completed file", model.player.playWhenReady)
            model.togglePlayback()
        }
        rule.waitUntil(5000) { model.uiState.value.isPlaying }
        rule.runOnUiThread {
            assertTrue(model.player.currentPosition < model.player.duration)
            assertTrue(model.player.playWhenReady)
        }
    }

    @Test fun aCompletedFileCanBeReplayedThroughTheMediaSession() {
        val model = startVideo()
        awaitCompletion(model)
        rule.runOnUiThread { checkNotNull(model.mediaSession).player.play() }
        rule.waitUntil(5000) { model.uiState.value.isPlaying }
        rule.runOnUiThread { assertTrue(model.player.currentPosition < model.player.duration) }
    }

    private fun startVideo(): PlayerViewModel {
        var model: PlayerViewModel? = null
        rule.runOnUiThread {
            model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[PlayerViewModel::class.java]
        }
        val playerModel = checkNotNull(model)
        rule.setContent {
            AndroidView(factory = { context -> PlayerView(context).apply { player = playerModel.player } },
                onRelease = { it.player = null })
        }
        rule.runOnUiThread { playerModel.start(listOf(com.uacastplayer.playlist.M3uChannel(
            video.displayName, video.uri, localVideo = video)), 0) }
        return playerModel
    }

    private fun awaitCompletion(model: PlayerViewModel) {
        rule.waitUntil(15_000) {
            var ended = false
            rule.runOnUiThread { ended = model.player.playbackState == Player.STATE_ENDED }
            ended
        }
        rule.waitForIdle()
    }

    @Test fun castFileIdentitySurvivesSdkParcelWithoutChangingThePlayableProxyUrl() {
        val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        val server = ProxyServer(client, localVideoSource = AndroidLocalVideoSource(app))
        val parcel = Parcel.obtain()
        try {
            server.start("native-cast-file-session", "127.0.0.1", flattenHlsToStream = true)
            val url = server.buildLocalUrl(server.registerPlaylist(video.uri, localVideo = video))
            val channel = CastChannel(0, video.uri, video.displayName, localVideo = video)
            val previous = checkNotNull(CastMediaLoader.forPlayback(url, video.displayName, channel, null).mediaInfo)
            val request = CastMediaLoader.forPlayback(url, video.displayName, channel, null)
            request.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = MediaLoadRequestData.CREATOR.createFromParcel(parcel)
            val info = checkNotNull(restored.mediaInfo)
            assertEquals(url, info.contentUrl)
            assertEquals(MediaInfo.STREAM_TYPE_BUFFERED, info.streamType)
            assertEquals(video.mimeType, info.contentType)
            assertFalse(CastStatusContentPolicy.shouldAccept(previous.contentId, info.contentId))
            assertTrue(CastStatusContentPolicy.shouldAccept(info.contentId, info.contentId))
            client.newCall(Request.Builder().url(checkNotNull(info.contentUrl)).header("Range", "bytes=32-95").build())
                .execute().use {
                    assertEquals(206, it.code)
                    assertArrayEquals(file.readBytes().copyOfRange(32, 96), it.body.bytes())
                }
        } finally {
            parcel.recycle()
            server.stop()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun platformSoapValuesRespectXmlTextAndRejectExternalEntities() {
        val fault = UpnpFault.parse("<u:UPnPError xmlns:u='urn:upnp'><u:errorCode>&#55;01</u:errorCode></u:UPnPError>")
        assertTrue(fault.isTransitionNotAvailable)
        assertEquals(17, VolumeResponseParser.parse("<CurrentVolume vendor='x'><![CDATA[17]]></CurrentVolume>"))
        assertEquals(DlnaTransportHealth.INACTIVE, DlnaTransportStateReader.parse(
            200, "<CurrentTransportState><![CDATA[STOPPED]]></CurrentTransportState>",
        ))
        assertFalse(UpnpFault.parse("<!DOCTYPE x [<!ENTITY e '701'>]><errorCode>&e;</errorCode>")
            .isTransitionNotAvailable)
    }

    @Test fun theProviderMimeSurvivesMisleadingNamesInProxyCastAndDlnaMetadata() {
        val selected = video.copy(displayName = "Alias.mkv", mimeType = checkNotNull(
            LocalVideoFormats.mimeType("Alias.mkv", video.mimeType)))
        val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        val server = ProxyServer(client, localVideoSource = AndroidLocalVideoSource(app))
        try {
            server.start("native-mime-session", "127.0.0.1", flattenHlsToStream = true)
            val url = server.buildLocalUrl(server.registerPlaylist(selected.uri, localVideo = selected))
            val channel = CastChannel(0, selected.uri, selected.displayName, localVideo = selected)
            val info = checkNotNull(CastMediaLoader.forPlayback(url, selected.displayName, channel, null).mediaInfo)
            assertEquals(video.mimeType, info.contentType)
            val soap = AvTransportSoapBuilder.setAvTransportUriEnvelope(url, selected.displayName, selected.mimeType)
            assertTrue(soap.contains("video/mp4"))
            assertFalse(soap.contains("video/x-matroska"))
            client.newCall(Request.Builder().url(url).head().build()).execute().use {
                assertEquals(200, it.code)
                assertEquals(video.mimeType, it.header("Content-Type"))
            }
        } finally {
            server.stop()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun theFileInterfaceSeeksWithoutResumingPauseOrOfferingChannelMenus() {
        var model: PlayerViewModel? = null
        rule.runOnUiThread {
            model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[PlayerViewModel::class.java]
        }
        val vm = checkNotNull(model)
        val transient = PlayerScreenTransientState(null, null)
        val actions = PlayerScreenActions(vm, {}, { false }, {}, { null }, {})
        rule.setContent {
            val state by vm.uiState.collectAsStateWithLifecycle()
            UaCastTheme(AppTheme.CINEMA) {
                InlinePlayerContent(
                    PlayerScreenContent(state, DlnaConnectionState(), 0, AspectRatioFrameLayout.RESIZE_MODE_FIT),
                    actions, transient,
                )
                if (transient.showActionsSheet) PlayerActionsSheet(transient, actions, false, false, true)
            }
        }
        rule.runOnUiThread { vm.start(PlayerRequest.forLocalVideo(video).channels, 0) }
        rule.waitUntil(15_000) { vm.uiState.value.isPlaying }
        rule.runOnUiThread { vm.togglePlayback() }
        rule.waitUntil(5000) { !vm.uiState.value.isPlaying && vm.uiState.value.canSeek }
        captureUi("file-player-ui-before")
        rule.onNodeWithText(app.getString(R.string.player_tv_guide)).assertDoesNotExist()
        rule.onNodeWithText(app.getString(R.string.player_next)).assertDoesNotExist()
        rule.onNodeWithContentDescription(app.getString(R.string.local_video_seek)).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        rule.runOnUiThread {
            assertTrue(kotlin.math.abs(vm.player.currentPosition - vm.player.duration / 2) < 100)
            assertFalse(vm.player.playWhenReady)
        }
        captureUi("file-player-ui")
        rule.onNodeWithText(app.getString(R.string.player_more_controls)).performClick()
        rule.onNodeWithText(app.getString(R.string.player_tv_guide)).assertDoesNotExist()
        rule.onNodeWithText(app.getString(R.string.player_view_all)).assertDoesNotExist()
    }

    private fun captureUi(name: String) {
        val capture = File(app.cacheDir, "diagnostics/$name.png")
        capture.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap()
            .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun actualContentResolverDescriptorServesSeekableBytesAndClosesEveryResponse() {
        val closed = AtomicInteger()
        val platformSource = AndroidLocalVideoSource(app)
        val source = object : LocalVideoSource {
            override fun open(video: LocalVideo, cancellation: android.os.CancellationSignal): LocalVideoHandle {
                val delegate = platformSource.open(video, cancellation)
                return object : LocalVideoHandle by delegate {
                    override fun close() { delegate.close(); closed.incrementAndGet() }
                }
            }
        }
        val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        val server = ProxyServer(client, localVideoSource = source)
        try {
            server.start("native-file-session", "127.0.0.1", flattenHlsToStream = true)
            val url = server.buildLocalUrl(server.registerPlaylist(video.uri, localVideo = video))
            client.newCall(Request.Builder().url(url).head().build()).execute().use {
                assertEquals(200, it.code)
                assertEquals(file.length().toString(), it.header("Content-Length"))
                assertEquals("video/mp4", it.header("Content-Type"))
            }
            client.newCall(Request.Builder().url(url).header("Range", "bytes=32-95").build()).execute().use {
                assertEquals(206, it.code)
                assertArrayEquals(file.readBytes().copyOfRange(32, 96), it.body.bytes())
            }
            rule.waitUntil(5000) { closed.get() == 2 }
            assertEquals(2, closed.get())
        } finally {
            server.stop()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
