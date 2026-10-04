package com.uacastplayer.ui.tv

import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.window.DialogWindowProvider

val LocalTvInputRegistry = staticCompositionLocalOf<TvInputRegistry?> { null }

/** Activity-owned, Main-only registry. A Compose dialog has its own window, not the Activity window. */
class TvInputRegistry {
    private val dialogs = linkedMapOf<View, Int>()

    fun register(view: View): () -> Unit {
        if (view.parent !is DialogWindowProvider) return {}
        val root = view.rootView
        dialogs[root] = (dialogs[root] ?: 0) + 1
        return {
            val count = (dialogs[root] ?: 1) - 1
            if (count == 0) dialogs.remove(root) else dialogs[root] = count
        }
    }

    /** null means there is no dialog; false must NOT fall through to the obscured Activity. */
    fun dispatchToDialog(event: KeyEvent): Boolean? =
        dialogs.keys.lastOrNull { it.isAttachedToWindow }?.dispatchKeyEvent(event)
}

@Composable
fun TvDialogInputRegistration() {
    if (!LocalTvMode.current) return
    // Every dialog has its own AndroidComposeView/input manager, independent of the Activity.
    val inputMode = LocalInputModeManager.current
    SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
    val registry = LocalTvInputRegistry.current
    val view = LocalView.current
    DisposableEffect(registry, view) {
        val unregister = registry?.register(view)
        onDispose { unregister?.invoke() }
    }
}
