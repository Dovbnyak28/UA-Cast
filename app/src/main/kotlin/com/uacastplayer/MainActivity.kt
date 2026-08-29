package com.uacastplayer

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.Saver
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.data.prefs.withAppLocale
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.language.LanguagePickerScreen
import com.uacastplayer.ui.legal.TermsScreen
import com.uacastplayer.ui.theme.UaCastTheme

/** Key for the one entry in `ScaffoldZone`'s [rememberSaveableStateHolder] - see its `else`
 * branch. There is deliberately only ever one: the Help/Terms/AddPlaylist screens are transient and
 * keep nothing, so only the tab scaffold underneath them has state worth holding on to. */
internal const val ROOT_SCAFFOLD_STATE_KEY = "root-scaffold"

internal data class PlayerRequest(val channels: List<M3uChannel>, val startIndex: Int)

/** The Bundle-safe remnant of a [PlayerRequest] that survives process death - just the playing
 * channel's stable key (see [FavoriteKey]), never the channel list itself, which can be
 * megabytes for large playlists and would risk a TransactionTooLargeException. */
internal data class SavedPlayerRequest(val channelKey: String, val startIndex: Int)

internal val SavedPlayerRequestSaver: Saver<SavedPlayerRequest?, List<Any>> = Saver(
    save = { request -> request?.let { listOf(it.channelKey, it.startIndex) }.orEmpty() },
    restore = { saved ->
        if (saved.isEmpty()) null else SavedPlayerRequest(saved[0] as String, saved[1] as Int)
    },
)

/**
 * A plain ComponentActivity crashes the Cast SDK's MediaRouteButton, so this must stay a
 * FragmentActivity even though the app itself doesn't otherwise use fragments.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private var activeLanguage: AppLanguage? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // Opting in rather than waiting to be opted in. From targetSdk 35 the system forces
        // edge-to-edge and ignores android:statusBarColor/navigationBarColor outright, so on
        // Android 15+ this happens whether the app asks or not - and the difference between an app
        // that handles it and one that doesn't is content sliding under the system bars. Declaring
        // it here means the layout runs the same way on every API level, so a missing
        // windowInsetsPadding shows up on any test device instead of only on Android 15 hardware.
        // Both bars are transparent with no scrim: every screen is drawn on the app's own dark
        // background, and the chrome that meets the bars (RootTopBar, GlassTabBar, the mini player)
        // pads itself out of them and paints its own background behind them.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        activeLanguage = viewModel.uiState.value.language

        setContent {
            // Only the routing/theme-gate fields of AppUiState are read at this top level - every
            // other flow (playlist, EPG, icons, cast, settings, favorites, ...) used to be collected
            // here too, which meant a change to *any* of them recomposed this entire tree, including
            // the player container and every dialog. They're now collected inside whichever zone
            // composable below actually consumes them (see ScaffoldZone/PlayerZone/BatteryHintZone),
            // so e.g. an icon-prefetch progress tick no longer has anything to do with the player.
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.language) {
                val previous = activeLanguage
                activeLanguage = uiState.language
                if (previous != null && previous != uiState.language) {
                    recreate()
                }
            }

            UaCastTheme(theme = uiState.appTheme) {
                when {
                    uiState.needsLanguagePicker ->
                        LanguagePickerScreen(onLanguageConfirmed = viewModel::selectLanguage)

                    uiState.needsTermsAcceptance ->
                        TermsScreen(onAccept = viewModel::acceptTerms, onDecline = { finish() })

                    // No walkthrough gate here any more. The three static cards that used to sit
                    // between Terms and the app were a second explanation of what the guided tour
                    // explains by pointing at the real thing - and back to back they were four
                    // screens of reading before the first useful tap. The tour opens itself on
                    // first launch instead (see MainAppContent), over the app, where the buttons
                    // it describes actually are.
                    else -> MainAppContent(
                        viewModel = viewModel,
                        currentLanguage = uiState.language,
                        currentAppTheme = uiState.appTheme,
                        onFinish = { finish() },
                    )
                }
            }
        }
    }
}
