package com.uacastplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.ui.components.ParentalControlPinDialog
import kotlinx.coroutines.launch

/**
 * Returns a function that runs its argument immediately if the parental-control PIN was already
 * entered this app session, or stashes it and shows the PIN dialog otherwise - the argument then
 * runs once the PIN checks out. Self-contained: also renders the dialog itself, so a caller just
 * wraps whatever needs gating (opening a locked channel, unlocking one permanently, Settings'
 * locked-channel management/PIN-change rows) in the returned function and nothing else. See
 * `app/ParentalControlController`'s doc for why unlocking (unlike locking) always needs this.
 */
@Composable
internal fun rememberParentalControlGate(viewModel: AppViewModel): (() -> Unit) -> Unit {
    var pendingUnlockAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }
    val unlocked by viewModel.parentalControlUnlocked.collectAsStateWithLifecycle()
    // Read through a holder rather than captured directly, so the returned gate below can be
    // remembered once instead of being reallocated whenever `unlocked` flips.
    val unlockedNow = rememberUpdatedState(unlocked)
    val pinScope = rememberCoroutineScope()

    if (showDialog) {
        ParentalControlPinDialog(
            title = stringResource(R.string.parental_control_enter_pin),
            isError = pinError,
            // Launched rather than called inline: verifying runs PBKDF2 off the main thread now
            // (see ParentalControlController.verifyPin), so the result arrives a frame or two later.
            onSubmit = { pin ->
                pinScope.launch {
                    if (viewModel.verifyParentalControlPin(pin)) {
                        showDialog = false
                        pinError = false
                        pendingUnlockAction?.invoke()
                        pendingUnlockAction = null
                    } else {
                        pinError = true
                    }
                }
            },
            onDismiss = {
                showDialog = false
                pendingUnlockAction = null
            },
        )
    }

    // Remembered, not rebuilt per composition: this is passed all the way down into RootScaffold's
    // ~50-parameter call, and an identity that changed on every recomposition meant that call could
    // never be skipped - one EPG minute tick recomposed the entire tab scaffold.
    return remember {
        { action: () -> Unit ->
            if (unlockedNow.value) {
                action()
            } else {
                pendingUnlockAction = action
                pinError = false
                showDialog = true
            }
        }
    }
}

