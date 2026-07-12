package com.uacastplayer

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.language.LanguagePickerScreen
import com.uacastplayer.ui.nav.RootScaffold
import com.uacastplayer.ui.player.PlayerHost
import com.uacastplayer.ui.theme.UaCastPlayerTheme

private data class PlayerRequest(val channels: List<M3uChannel>, val startIndex: Int)

/**
 * A plain ComponentActivity crashes the Cast SDK's MediaRouteButton, so this must stay a
 * FragmentActivity even though the app itself doesn't otherwise use fragments.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private var activeLanguage: AppLanguage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        activeLanguage = viewModel.uiState.value.language

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val playlistState by viewModel.playlistState.collectAsStateWithLifecycle()
            val epgState by viewModel.epgState.collectAsStateWithLifecycle()
            val iconPrefetchState by viewModel.iconPrefetchState.collectAsStateWithLifecycle()
            val castState by viewModel.castState.collectAsStateWithLifecycle()

            val pickPlaylistFile = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let(viewModel::loadPlaylistFromFile) }

            var playerRequest by remember { mutableStateOf<PlayerRequest?>(null) }

            LaunchedEffect(uiState.language) {
                val previous = activeLanguage
                activeLanguage = uiState.language
                if (previous != null && previous != uiState.language) {
                    recreate()
                }
            }

            UaCastPlayerTheme {
                val request = playerRequest
                when {
                    uiState.needsLanguagePicker ->
                        LanguagePickerScreen(onLanguageConfirmed = viewModel::selectLanguage)

                    request != null -> {
                        BackHandler { playerRequest = null }
                        PlayerHost(
                            channels = request.channels,
                            startIndex = request.startIndex,
                            onExit = { playerRequest = null },
                        )
                    }

                    else -> RootScaffold(
                        currentLanguage = uiState.language,
                        onLanguageSelected = viewModel::selectLanguage,
                        onExitApp = { finish() },
                        playlistState = playlistState,
                        onLoadPlaylistUrl = viewModel::loadPlaylistFromUrl,
                        onPickPlaylistFile = { pickPlaylistFile.launch(arrayOf("audio/x-mpegurl", "*/*")) },
                        onChannelSelected = { channels, startIndex ->
                            playerRequest = PlayerRequest(channels, startIndex)
                        },
                        epgState = epgState,
                        onEpgSourceSelected = viewModel::selectEpgSource,
                        iconPrefetchState = iconPrefetchState,
                        onIconWifiOnlyChanged = viewModel::setIconWifiOnly,
                        resolveIcon = viewModel::resolveChannelIcon,
                        castState = castState,
                    )
                }
            }
        }
    }
}
