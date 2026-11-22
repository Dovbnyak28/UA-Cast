package app.zorik.player.player

/** Sender commands use the same port as lifecycle ownership; UI never reaches into the Cast SDK. */
class PlayerCastControls internal constructor(private val port: PlayerCastPort) {
    fun stop() = port.stopPlayback()
}
