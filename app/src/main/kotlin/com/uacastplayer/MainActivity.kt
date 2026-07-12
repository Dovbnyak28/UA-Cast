package com.uacastplayer

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.ui.language.LanguagePickerScreen
import com.uacastplayer.ui.nav.RootScaffold
import com.uacastplayer.ui.theme.UaCastPlayerTheme

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

            val pickPlaylistFile = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let(viewModel::loadPlaylistFromFile) }

            LaunchedEffect(uiState.language) {
                val previous = activeLanguage
                activeLanguage = uiState.language
                if (previous != null && previous != uiState.language) {
                    recreate()
                }
            }

            UaCastPlayerTheme {
                if (uiState.needsLanguagePicker) {
                    LanguagePickerScreen(onLanguageConfirmed = viewModel::selectLanguage)
                } else {
                    RootScaffold(
                        currentLanguage = uiState.language,
                        onLanguageSelected = viewModel::selectLanguage,
                        onExitApp = { finish() },
                        playlistState = playlistState,
                        onLoadPlaylistUrl = viewModel::loadPlaylistFromUrl,
                        onPickPlaylistFile = { pickPlaylistFile.launch(arrayOf("audio/x-mpegurl", "*/*")) },
                    )
                }
            }
        }
    }
}
