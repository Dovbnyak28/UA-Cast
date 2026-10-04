package com.uacastplayer.core.remote

/** Only application navigation/playback commands are permitted; this protocol never runs intents or shell commands. */
enum class RemoteCommand {
    UP, DOWN, LEFT, RIGHT, SELECT, BACK, PLAY_PAUSE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN,
}
