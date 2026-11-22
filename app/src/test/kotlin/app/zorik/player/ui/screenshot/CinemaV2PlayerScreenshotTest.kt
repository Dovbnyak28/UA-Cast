package app.zorik.player.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import app.zorik.player.epg.EpgProgramme
import app.zorik.player.epg.DayScheduleBuilder
import app.zorik.player.media.LocalVideo
import app.zorik.player.player.PlayerUiState
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.epg.EpgGuideSheet
import app.zorik.player.ui.epg.ScheduleList
import app.zorik.player.ui.player.FilePlaybackProgress
import app.zorik.player.ui.player.FilePlaybackTimeline
import app.zorik.player.ui.player.PlayerControlsOverlay
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import app.zorik.player.ui.theme.UaPlayerTheme
import app.zorik.player.ui.theme.UaTheme
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w640dp-h360dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class CinemaV2PlayerScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val previousZone = TimeZone.getDefault()

    /** The overlay and the empty guide format programme times in the device zone, and the guide prints
     * the zone id. The references were recorded in Europe/Paris; without pinning it, a UTC CI runner
     * draws every hour one earlier and a different zone name, and all of these images differ. */
    @Before fun pinRecordedZone() { TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris")) }

    @After fun restoreDeviceZone() { TimeZone.setDefault(previousZone) }
    private val timer = mutableStateOf<Long?>(null)
    private val start = Instant.parse("2026-11-14T09:00:00Z").toEpochMilli()
    private val programme = EpgProgramme("cinema", start, start + 3_600_000, "Карпати: тиха подорож")
    private val channel = M3uChannel("Мандрівки HD — дуже довга назва телеканалу", "https://example.test/live")

    @Test fun liveLandscape() = player("cinema_v2_player_live", AppTheme.CINEMA)
    @Test fun liveAzure() = player("cinema_v2_player_azure", AppTheme.AZURE)
    @Test fun liveMidnight() = player("cinema_v2_player_midnight", AppTheme.MIDNIGHT)
    @Test fun liveDaylight() = player("cinema_v2_player_daylight", AppTheme.DAYLIGHT)
    @Test fun liveSystem() = player("cinema_v2_player_system", AppTheme.SYSTEM)
    @Test @Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
    fun portraitLargeText() = player("cinema_v2_player_portrait_200", AppTheme.CINEMA, 2f)
    @Test @Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
    fun portraitLargeTextWithActiveTimer() {
        timer.value = 3_600_000L
        player("cinema_v2_player_portrait_timer_200", AppTheme.CINEMA, 2f)
    }
    @Test @Config(qualifiers = "uk-w640dp-h320dp-xhdpi")
    fun landscapeLargeText() = player("cinema_v2_player_landscape_200", AppTheme.CINEMA, 2f)

    @Test fun localFileTimeline() {
        val video = LocalVideo("content://media/video/1", "Сімейна подорож.mp4", "video/mp4")
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            PlayerControlsOverlay(
                PlayerUiState(currentChannel = M3uChannel(video.displayName, video.uri, localVideo = video),
                    canControlPlayback = true, canSeek = true), true, timer,
                {}, {}, {}, {}, {}, {}, {}, false, {}, {},
                fileProgress = { FilePlaybackProgress(FilePlaybackTimeline(65_000, 3_661_000), true, {}) },
            )
        } }
        rule.onRoot().captureRoboImage("src/test/screenshots/cinema_v2_player_file.png")
    }

    @Test @Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
    fun programmeGuideLargeText() {
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
            ZorikTheme(AppTheme.CINEMA) {
                val programmes = (0..4).map { programme.copy(startMillis = start + it * 3_600_000,
                    stopMillis = start + (it + 1) * 3_600_000, title = "${programme.title} ${it + 1}") }
                Box(Modifier.fillMaxSize().background(UaTheme.palette.void)) {
                    ScheduleList(DayScheduleBuilder.build(programmes, start + 1_800_000, ZoneOffset.UTC),
                        start + 1_800_000, ZoneOffset.UTC)
                }
            }
        } }
        rule.onRoot().captureRoboImage("src/test/screenshots/cinema_v2_epg_200.png")
    }

    @Test @Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
    fun loadingGuide() = emptyGuide("loading", loading = true)
    @Test @Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
    fun failedGuide() = emptyGuide("error", error = true)
    @Test @Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
    fun unconfiguredGuide() = emptyGuide("empty")

    private fun player(name: String, theme: AppTheme, scale: Float = 1f) {
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
            ZorikTheme(theme) { UaPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    PlayerControlsOverlay(PlayerUiState(currentChannel = channel, wantsToPlay = true,
                        canControlPlayback = true, canGoNext = true, canGoPrevious = true), true, timer,
                        {}, {}, {}, {}, {}, {}, {}, false, {}, {}, programme = programme,
                        programmeNowMillis = start + 1_800_000, onOpenGuide = {})
                }
            } }
        } }
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun emptyGuide(name: String, loading: Boolean = false, error: Boolean = false) {
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            EpgGuideSheet(channel, null, start, {}, isLoading = loading, hasError = error)
        } }
        rule.onAllNodes(isRoot()).onLast().captureRoboImage("src/test/screenshots/cinema_v2_epg_$name.png")
    }
}
