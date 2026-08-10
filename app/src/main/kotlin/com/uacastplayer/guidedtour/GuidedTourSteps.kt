package com.uacastplayer.guidedtour

import com.uacastplayer.R
import com.uacastplayer.core.nav.BottomDestination

/**
 * The tour itself: seven steps across the app's main flow, as data.
 *
 * Bumped through [GuidedTourVersion] whenever this list changes in a way an existing user should see
 * again - a new step, or a step that now describes something different. Reordering or a wording fix
 * is not that.
 */
object GuidedTourSteps {

    val DEFAULT: List<GuidedTourStep> = listOf(
        GuidedTourStep(
            id = "playlist",
            titleRes = R.string.guided_tour_playlist_title,
            descriptionRes = R.string.guided_tour_playlist_body,
            target = GuidedTourTarget.Element(GuidedTourKeys.PLAYLIST_ADD),
            destination = BottomDestination.HOME,
        ),
        GuidedTourStep(
            id = "channels",
            titleRes = R.string.guided_tour_channels_title,
            descriptionRes = R.string.guided_tour_channels_body,
            target = GuidedTourTarget.Element(GuidedTourKeys.CHANNEL_SEARCH),
            destination = BottomDestination.CHANNELS,
        ),
        // The player exists only while something is playing, and a user being shown the tour
        // usually has no playlist yet - so this step is text more often than not. That is the
        // designed outcome, not a gap: see GuidedTourTarget.
        GuidedTourStep(
            id = "player",
            titleRes = R.string.guided_tour_player_title,
            descriptionRes = R.string.guided_tour_player_body,
            target = GuidedTourTarget.Element(GuidedTourKeys.PLAYER_FULLSCREEN),
            destination = BottomDestination.CHANNELS,
        ),
        GuidedTourStep(
            id = "favorites",
            titleRes = R.string.guided_tour_favorites_title,
            descriptionRes = R.string.guided_tour_favorites_body,
            target = GuidedTourTarget.Element(GuidedTourKeys.FAVORITE_BUTTON),
            destination = BottomDestination.FAVORITES,
        ),
        // Same as the player step: the guide opens from a channel's own actions, so there is no
        // button in the app chrome to point at until the user has a channel to long-press.
        GuidedTourStep(
            id = "epg",
            titleRes = R.string.guided_tour_epg_title,
            descriptionRes = R.string.guided_tour_epg_body,
            target = GuidedTourTarget.Element(GuidedTourKeys.EPG_BUTTON),
            destination = BottomDestination.CHANNELS,
        ),
        GuidedTourStep(
            id = "cast",
            titleRes = R.string.guided_tour_cast_title,
            descriptionRes = R.string.guided_tour_cast_body,
            target = GuidedTourTarget.Element(GuidedTourKeys.CAST_BUTTON),
            destination = BottomDestination.CHANNELS,
        ),
        GuidedTourStep(
            id = "settings",
            titleRes = R.string.guided_tour_settings_title,
            descriptionRes = R.string.guided_tour_settings_body,
            target = GuidedTourTarget.Element(GuidedTourKeys.SETTINGS_BUTTON),
            destination = BottomDestination.SETTINGS,
        ),
    )
}

/**
 * Which edition of the tour a device has seen.
 *
 * Stored next to the completion flag so a later release that adds steps can offer the tour again to
 * someone who already finished the old one, without that also meaning "show it on every launch
 * forever" - see [GuidedTourAvailability].
 */
object GuidedTourVersion {
    const val CURRENT = 1
}
