package com.uacastplayer.ui.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.uacastplayer.playlist.PlaylistSourceSaveState
import com.uacastplayer.playlist.PlaylistUiState

/** Navigation follows the owner's durable result, never just a successfully downloaded playlist. */
@Composable
internal fun PlaylistAddCompletion(state: PlaylistUiState, onSubmit: () -> Unit, onSaved: () -> Unit) {
    var requested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (state.isLoading) {
            requested = false
        }
    }
    LaunchedEffect(state.sourceReadyToSave) {
        if (state.sourceReadyToSave) {
            requested = true
            onSubmit()
        }
    }
    LaunchedEffect(requested, state.sourceSaveState) {
        if (requested && state.sourceSaveState == PlaylistSourceSaveState.SAVED) {
            requested = false
            onSaved()
        }
    }
}
