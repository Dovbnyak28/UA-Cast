package com.uacastplayer.premium

/**
 * Every capability whose availability can depend on what the user has paid for.
 *
 * Two kinds of entry live here, and the difference matters when reading [FeaturePolicy]:
 *
 * - **Built and shipping.** [CHROMECAST], [DLNA], [PIP], [MULTI_PLAYLIST], [THEMES], [BACKUP],
 *   [PARENTAL_CONTROL], [XTREAM], [CUSTOM_EPG_SOURCE], [CUSTOM_ICON_SOURCES], [RAW_TS_REMUX].
 *   A flag on one of these decides whether existing, working code is reachable.
 * - **Reserved.** [CLOUD_SYNC], [SMART_SEARCH], [RECORDING], [NAS], [SMB], [WEBDAV], [PLEX],
 *   [JELLYFIN], [EMBY] have no implementation anywhere in this project yet. Their entries cost one
 *   line each and exist so the name is fixed now rather than argued about later; until something
 *   calls [FeatureManager.isUnlocked] with one, it does nothing at all.
 *
 * Nothing in `cast/`, `dlna/`, `player/` or `playlist/` imports this enum. A feature does not know
 * whether it is paid for - only the place that *offers* it does, which is what keeps `if (premium)`
 * from spreading through the codebase.
 */
enum class Feature {
    // --- Casting and playback ---
    CHROMECAST,
    DLNA,
    PIP,

    /** Sending a stream the receiver cannot play directly through the local remuxing relay. */
    RAW_TS_REMUX,

    // --- Content sources ---
    MULTI_PLAYLIST,
    XTREAM,
    CUSTOM_EPG_SOURCE,
    CUSTOM_ICON_SOURCES,

    // --- Personalisation and data ---
    THEMES,
    BACKUP,
    PARENTAL_CONTROL,

    // --- Reserved: nothing implements these yet ---
    CLOUD_SYNC,
    SMART_SEARCH,
    RECORDING,
    NAS,
    SMB,
    WEBDAV,
    PLEX,
    JELLYFIN,
    EMBY,
}
