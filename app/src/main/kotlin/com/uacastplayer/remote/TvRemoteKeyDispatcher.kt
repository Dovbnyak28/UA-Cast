package com.uacastplayer.remote

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.OnBackPressedDispatcherOwner
import com.uacastplayer.core.remote.RemoteCommand

/** Dispatch only through this Activity's window; no accessibility service or privileged system input injection. */
fun Activity.dispatchTvRemote(command: RemoteCommand, dialogTarget: (KeyEvent) -> Boolean? = { null }) {
    if (command == RemoteCommand.VOLUME_UP || command == RemoteCommand.VOLUME_DOWN) {
        adjustRemoteVolume(command)
        return
    }
    val keyCode = command.keyCode()
    val time = SystemClock.uptimeMillis()
    val down = KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0)
    val up = KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode, 0)
    if (dialogTarget(down) != null) {
        dialogTarget(up)
    } else if (command == RemoteCommand.BACK && this is OnBackPressedDispatcherOwner) {
        onBackPressedDispatcher.onBackPressed()
    } else {
        dispatchKeyEvent(down)
        dispatchKeyEvent(up)
    }
}

private fun Activity.adjustRemoteVolume(command: RemoteCommand) {
    val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    runCatching {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
            if (command == RemoteCommand.VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI)
    }
}

private fun RemoteCommand.keyCode(): Int = when (this) {
        RemoteCommand.UP -> KeyEvent.KEYCODE_DPAD_UP
        RemoteCommand.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        RemoteCommand.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        RemoteCommand.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        RemoteCommand.SELECT -> KeyEvent.KEYCODE_DPAD_CENTER
        RemoteCommand.BACK -> KeyEvent.KEYCODE_BACK
        RemoteCommand.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        RemoteCommand.NEXT -> KeyEvent.KEYCODE_CHANNEL_UP
        RemoteCommand.PREVIOUS -> KeyEvent.KEYCODE_CHANNEL_DOWN
        RemoteCommand.VOLUME_UP, RemoteCommand.VOLUME_DOWN -> KeyEvent.KEYCODE_UNKNOWN
}
