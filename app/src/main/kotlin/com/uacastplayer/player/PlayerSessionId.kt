package com.uacastplayer.player

private const val SESSION_ID_PREFIX = "uacast_player_"

/**
 * Media3 requires a MediaSession's ID to be unique among sessions still alive in the process -
 * the default (empty) ID collides if a new PlayerViewModel is constructed before the previous
 * one's session has released, e.g. during a NavHost exit-animation overlap on a fast reopen.
 * Deriving the ID from a monotonically increasing counter (see PlayerViewModel's companion
 * object) guarantees uniqueness regardless of teardown timing.
 */
fun playerSessionId(counter: Long): String = "$SESSION_ID_PREFIX$counter"
