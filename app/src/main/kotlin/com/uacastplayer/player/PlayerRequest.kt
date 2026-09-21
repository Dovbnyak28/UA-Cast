package com.uacastplayer.player

import com.uacastplayer.playlist.M3uChannel

/** One explicit user request. Its identity distinguishes a reattached UI from another tap. */
class PlayerRequest(val channels: List<M3uChannel>, val startIndex: Int)
