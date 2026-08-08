package com.uacastplayer.ui.premium

import androidx.annotation.StringRes
import com.uacastplayer.R
import com.uacastplayer.premium.Feature

/**
 * What each paid feature is called on screen, and which of them are worth listing.
 *
 * [Feature] deliberately carries entries for things nothing implements yet ([Feature.CLOUD_SYNC],
 * [Feature.NAS] and the rest) so their names are fixed before an argument about them. Those must
 * not appear anywhere in the UI: advertising a feature that does not exist is the fastest way to
 * make a paywall feel dishonest. [SOLD] is therefore an explicit list rather than "everything not
 * free" - adding a Feature does not put it on the premium screen, shipping it does.
 */
object PremiumLabels {

    /** The paid features that actually exist, in the order the premium screen lists them. */
    val SOLD: List<Feature> = listOf(
        Feature.MULTI_PLAYLIST,
        Feature.DLNA,
        Feature.PARENTAL_CONTROL,
        Feature.BACKUP,
        Feature.XTREAM,
        Feature.CUSTOM_EPG_SOURCE,
        Feature.CUSTOM_ICON_SOURCES,
        Feature.RAW_TS_REMUX,
    )

    /** Null for anything not in [SOLD] - a caller with one of those has nothing to show a user. */
    @StringRes
    fun nameRes(feature: Feature): Int? = when (feature) {
        Feature.MULTI_PLAYLIST -> R.string.feature_multi_playlist
        Feature.DLNA -> R.string.feature_dlna
        Feature.PARENTAL_CONTROL -> R.string.feature_parental_control
        Feature.BACKUP -> R.string.feature_backup
        Feature.XTREAM -> R.string.feature_xtream
        Feature.CUSTOM_EPG_SOURCE -> R.string.feature_custom_epg
        Feature.CUSTOM_ICON_SOURCES -> R.string.feature_custom_icons
        Feature.RAW_TS_REMUX -> R.string.feature_raw_ts_remux
        else -> null
    }
}
