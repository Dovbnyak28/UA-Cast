package com.uacastplayer.ui.player

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.playlist.M3uChannel

/**
 * Wraps the player screen in its own single-destination NavHost purely so its ViewModelStore -
 * and with it [PlayerViewModel] and the single ExoPlayer instance it owns - is torn down the
 * moment this composable leaves composition, instead of living for the app's whole process.
 */
@OptIn(markerClass = [UnstableApi::class])
@Composable
fun PlayerHost(
    channels: List<M3uChannel>,
    startIndex: Int,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "player", modifier = modifier) {
        composable("player") {
            val viewModel: PlayerViewModel = viewModel()

            LaunchedEffect(channels, startIndex) {
                viewModel.start(channels, startIndex)
            }

            PlayerScreen(viewModel = viewModel, onExit = onExit)
        }
    }
}
