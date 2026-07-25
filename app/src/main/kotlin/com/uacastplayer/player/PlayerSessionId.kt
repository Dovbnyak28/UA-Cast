package com.uacastplayer.player

/**
 * The fixed MediaSession id for this app's single player session.
 *
 * It is deliberately NOT made unique per instance. [PlayerViewModel] is Activity-scoped and only
 * ever one is alive at a time (see PlayerHost and the liveInstances guard), so a constant id is
 * correct - and it doubles as a loud backstop: Media3 rejects a second MediaSession sharing an id
 * with one still alive in the process, so if that single-instance invariant ever breaks again, the
 * breakage surfaces immediately instead of quietly leaking an ExoPlayer.
 */
const val PLAYER_SESSION_ID = "uacast_player"
