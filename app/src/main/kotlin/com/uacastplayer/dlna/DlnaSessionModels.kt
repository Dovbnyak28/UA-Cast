package com.uacastplayer.dlna

import com.uacastplayer.media.LocalVideo

internal data class PreparedDlnaProxy(val token: String, val localUrl: String)
internal data class DlnaConnectRequest(
    val device: DlnaDevice,
    val media: DlnaMedia,
    val isRepoint: Boolean,
    /** Renderers that could still be active when this attempt was requested. */
    val previousDevices: List<DlnaDevice>,
    val generation: Long,
)

internal data class DlnaMedia(
    val url: String, val title: String, val userAgent: String?, val referrer: String?,
    val localVideo: LocalVideo? = null,
)
