package com.uacastplayer.playlist

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.MainActivity
import com.uacastplayer.R
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.loadPlaylistFromFile
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.setPlaylistDisplayName
import com.uacastplayer.testsupport.appViewModelOf
import com.uacastplayer.testsupport.openChannelViaSearch
import com.uacastplayer.testsupport.setAutoSkipDeadChannels
import com.uacastplayer.testsupport.skipOnboarding
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit, local-only acceptance test. No URLs, titles, tokens or exception messages in output. */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class TemporaryPlaylistInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var channels: List<M3uChannel>
    private var previousAutoSkip: Boolean? = null

    @Before
    fun importOptInFixture() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("temporaryPlaylist") == "true")
        assertTrue("Only the debug application may import this fixture", context.packageName.endsWith(".debug"))
        val bytes = context.assets.open("temporary-playlist.m3u8").use { it.readBytes() }
        val parsed = M3uParser.parse(CharsetDetector.decode(bytes))
        channels = parsed.channels
        assertTrue("Playlist must contain channels", channels.isNotEmpty())
        assertEquals("No malformed channel lines expected", 0, parsed.skippedLineCount)
        val fixture = File(context.filesDir, "temporary-playlist.m3u8")
        fixture.writeBytes(bytes)
        previousAutoSkip = setAutoSkipDeadChannels(context, false)
        val repository = PlaylistRepository(context)
        val existingIds = runBlocking { repository.loadSources() }.map { it.id }.toSet()
        rule.waitUntil(30_000) {
            appViewModelOf(rule.activity).playlistSources.value.map { it.id }.containsAll(existingIds)
        }
        val started = SystemClock.elapsedRealtime()
        rule.activityRule.scenario.onActivity {
            skipOnboarding(it)
            appViewModelOf(it).loadPlaylistFromFile(Uri.fromFile(fixture))
        }
        rule.waitUntil(30_000) {
            val state = appViewModelOf(rule.activity).playlistState.value
            !state.isLoading && state.hasChannels && state.channels.size == channels.size
        }
        // Boolean assertions avoid printing credential-bearing M3uChannel.toString() on failure.
        assertTrue("Every imported stream must match the source file", appViewModelOf(rule.activity)
            .playlistState.value.channels.map { it.streamUrl }.sorted() == channels.map { it.streamUrl }.sorted())
        rule.activityRule.scenario.onActivity { appViewModelOf(it).setPlaylistDisplayName("Temporary device test") }
        val inMemory = appViewModelOf(rule.activity).playlistSources.value
        report("source-save existing=${existingIds.size} memory=${inMemory.size} " +
            "preserved=${inMemory.map { it.id }.containsAll(existingIds)} " +
            "fixture=${inMemory.any { it.location == Uri.fromFile(fixture).toString() }}")
        // AtomicFile.openRead is not a passive observer while a write is in flight: it can
        // restore the backup. Wait on the controller's durable boundary before reading it.
        var saveJob: Job? = null
        rule.activityRule.scenario.onActivity {
            val controller = appViewModelOf(it).playlistController
            saveJob = controller.applyImportedSources(controller.playlistSources.value)
        }
        runBlocking { checkNotNull(saveJob).join() }
        val saved = runBlocking { repository.loadSources() }
        assertTrue("Existing sources must remain saved", saved.map { it.id }.containsAll(existingIds))
        assertTrue("Temporary source must be persisted", saved.any { it.location == Uri.fromFile(fixture).toString() })
        report("import channels=${channels.size} skipped=${parsed.skippedLineCount} millis=${SystemClock.elapsedRealtime() - started}")
    }

    @After
    fun restorePreference() {
        previousAutoSkip?.let { setAutoSkipDeadChannels(context, it) }
    }

    @Test
    fun sampleStreamsRenderVideoAndLifecycleRemainsCorrect() {
        val nameCounts = channels.groupingBy { it.displayName }.eachCount()
        val first = channels.indexOfFirst { nameCounts[it.displayName] == 1 }
        assertTrue("A uniquely named channel is required for the UI search smoke test", first >= 0)
        rule.openChannelViaSearch(channels[first].displayName)
        val requested = InstrumentationRegistry.getArguments().getString("temporaryPlaylistIndices")
            ?.split(',')?.map { it.toInt() }
        val samples = requested ?: (listOf(first, 1, 2) + (1..7).map { it * (channels.size - 1) / 7 })
            .distinct().filter { it in channels.indices }
        assertTrue("Sample indices must exist", samples.isNotEmpty() && samples.all { it in channels.indices })
        val successful = samples.filter { probe(it) }
        report("sample-summary played=${successful.size} tested=${samples.size} total=${channels.size}")
        assertTrue("No sampled channel rendered moving video; inspect sanitized TEMP_PLAYLIST results", successful.isNotEmpty())
        checkLifecycle(successful.first())
    }

    @Test
    fun inspectSelectedManifestsWithoutPlayback() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("temporaryPlaylistNetworkProbe") == "true")
        val indices = InstrumentationRegistry.getArguments().getString("temporaryPlaylistIndices")
            ?.split(',')?.map { it.toInt() } ?: listOf(0)
        val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).build()
        try {
            for (index in indices) {
                val started = SystemClock.elapsedRealtime()
                try {
                    val request = Request.Builder().url(channels[index].streamUrl)
                        .header("User-Agent", com.uacastplayer.core.net.HttpDefaults.BROWSER_USER_AGENT).build()
                    client.newCall(request).execute().use { response ->
                        val body = response.peekBody(16_384).string()
                        report("manifest index=$index http=${response.code} hls=${body.startsWith("#EXTM3U")} " +
                            "master=${body.contains("#EXT-X-STREAM-INF")} " +
                            "segments=${Regex("#EXTINF:").findAll(body).count()} " +
                            "millis=${SystemClock.elapsedRealtime() - started}")
                        val segment = body.lineSequence().map { it.trim() }
                            .firstOrNull { it.isNotEmpty() && !it.startsWith('#') }
                            ?.let { response.request.url.resolve(it) }
                        if (!body.contains("#EXT-X-STREAM-INF") && segment != null) {
                            val segmentRequest = Request.Builder().url(segment)
                                .header("User-Agent", com.uacastplayer.core.net.HttpDefaults.BROWSER_USER_AGENT)
                                .header("Range", "bytes=0-16383").build()
                            client.newCall(segmentRequest).execute().use { media ->
                                val sample = media.peekBody(16_384).bytes()
                                val tsSync = sample.size > 188 && sample[0] == 0x47.toByte() &&
                                    sample[188] == 0x47.toByte()
                                val programme = com.uacastplayer.core.cast.TsProgramInfoParser.parse(sample)
                                report("segment index=$index http=${media.code} bytes=${sample.size} tsSync=$tsSync " +
                                    "encrypted=${body.contains("METHOD=AES")} video=${programme?.videoCodec} " +
                                    "audio=${programme?.audioCodecs}")
                            }
                        }
                    }
                } catch (failure: java.io.IOException) {
                    report("manifest index=$index failure=${failure.javaClass.simpleName} " +
                        "millis=${SystemClock.elapsedRealtime() - started}")
                }
            }
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun probe(index: Int): Boolean {
        val frames = AtomicInteger()
        val errors = AtomicInteger()
        val httpStatus = AtomicInteger()
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { frames.incrementAndGet() }
            override fun onPlayerError(error: PlaybackException) {
                errors.set(error.errorCode)
                generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                    .firstOrNull()?.let { httpStatus.set(it.responseCode) }
            }
        }
        onPlayer {
            it.player.addListener(listener)
            it.start(channels, index)
        }
        val started = SystemClock.elapsedRealtime()
        var playing = false
        var advanced = false
        var firstPosition: Long? = null
        var width = 0
        var height = 0
        var state = Player.STATE_IDLE
        var wantsToPlay = false
        val timeout = InstrumentationRegistry.getArguments().getString("temporaryPlaylistTimeoutMs")
            ?.toLong()?.coerceIn(5_000, 90_000) ?: 25_000L
        try {
            while (SystemClock.elapsedRealtime() - started < timeout && errors.get() == 0 && !advanced) {
                onPlayer {
                    playing = it.player.isPlaying
                    state = it.player.playbackState
                    wantsToPlay = it.player.playWhenReady
                    width = it.player.videoSize.width
                    height = it.player.videoSize.height
                    if (playing && frames.get() > 0) {
                        val position = it.player.currentPosition
                        if (firstPosition == null) firstPosition = position
                        advanced = position - checkNotNull(firstPosition) >= 2_000
                    }
                }
                SystemClock.sleep(200)
            }
        } finally {
            onPlayer { it.player.removeListener(listener) }
        }
        val ok = advanced && frames.get() > 0 && playing && width > 0 && height > 0
        report("probe index=$index video=$ok firstFrame=${frames.get() > 0} size=${width}x$height " +
            "state=$state wants=$wantsToPlay error=${errors.get()} http=${httpStatus.get()} " +
            "millis=${SystemClock.elapsedRealtime() - started}")
        return ok
    }

    private fun checkLifecycle(workingIndex: Int) {
        assertTrue("Previously working stream must remain playable", probe(workingIndex))
        onPlayer { it.player.pause() }
        onPlayer { assertTrue("Pause must clear play intent", !it.player.playWhenReady) }
        onPlayer { it.player.play() }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        onPlayer { assertTrue("Hidden local playback must pause", !it.player.playWhenReady) }
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onPlayer { assertTrue("Foreground must resume playback", it.player.playWhenReady) }
        report("lifecycle pause-resume=PASS background=PASS")
        var before: PlayerViewModel? = null
        onPlayer { before = it }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        onPlayer { assertTrue("Recreation must retain the player", before === it) }
        onPlayer { report("lifecycle recreate=PASS state=${it.player.playbackState} wants=${it.player.playWhenReady}") }
        val fullscreen = hasContentDescription(rule.activity.getString(R.string.player_fullscreen))
        rule.waitUntil(10_000) { rule.onAllNodes(fullscreen).fetchSemanticsNodes().isNotEmpty() }
        report("lifecycle recreate-video-surface=PASS")
        repeat(20) { index -> onPlayer { it.start(channels, index % channels.size) } }
        onPlayer {
            assertTrue("Last rapid switch must win", it.uiState.value.currentChannel == channels[19 % channels.size])
        }
        assertEquals("One player must remain alive", 1, PlayerViewModel.liveInstanceCountForTest())
        report("lifecycle switches20 last-channel=PASS instances=1")
        assertTrue("Playback must recover after rapid switching", probe(workingIndex))
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.common_back)).performClick()
        rule.waitForIdle()
        onPlayer {
            assertTrue("Closing player must stop playback", !it.player.isPlaying)
            assertEquals("Closing player must return it to idle", Player.STATE_IDLE, it.player.playbackState)
            assertEquals("Closing player must clear its media", 0, it.player.mediaItemCount)
        }
        report("lifecycle pause-resume=PASS background=PASS recreate=PASS switches20=PASS close=PASS")
    }

    private fun onPlayer(action: (PlayerViewModel) -> Unit) {
        rule.activityRule.scenario.onActivity { action(ViewModelProvider(it)[PlayerViewModel::class.java]) }
    }

    private fun report(message: String) {
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "TEMP_PLAYLIST $message\n") })
    }
}
