package com.uacastplayer.ui.tv

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager

/** Single-line editing keeps left/right cursor motion; up/down leave the field on TV. */
@Composable
fun Modifier.tvTextFieldNavigation(): Modifier {
    if (!LocalTvMode.current) return this
    val focus = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        val direction = when (event.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
            else -> null
        }
        if (direction != null && event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) focus.moveFocus(direction)
        direction != null
    }
}
