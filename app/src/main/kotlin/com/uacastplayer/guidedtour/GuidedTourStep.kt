package com.uacastplayer.guidedtour

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.uacastplayer.core.nav.BottomDestination

/**
 * The stable names UI elements register themselves under, so a step can say *what* it points at
 * without knowing where on the screen that is.
 *
 * Semantic, never coordinates: a layout change moves the pixels and leaves the name alone, which is
 * the whole reason these exist. The values are also deliberately not the same constants as
 * [com.uacastplayer.ui.UiTestTags] - a test tag is something a test may change freely, and a tour
 * that silently stopped highlighting because a test was tidied would be a bad trade.
 */
object GuidedTourKeys {
    const val PLAYLIST_ADD = "playlist_add"

    /** Registered on *both* search fields - the one over all channels on the groups screen, and the
     * one inside an opened group. They are never composed at the same time, and a tour that only
     * knew about the first went text-only for anyone who happened to have a group open. */
    const val CHANNEL_SEARCH = "channel_search"

    /**
     * The Favorites tab, not the star on a channel row.
     *
     * Measured on hardware, and the reason this changed: the star only exists inside an opened
     * group *and* only in the list layout, while the tour's Channels step lands on the group grid.
     * A key that could only light up in a state the tour never reaches is worse than one that
     * points at a slightly broader thing - so it points at the tab the favorites end up in, and the
     * step's text explains the star.
     */
    const val FAVORITE_BUTTON = "favorite_button"
    const val PLAYER_FULLSCREEN = "player_fullscreen"
    const val EPG_BUTTON = "epg_button"
    const val CAST_BUTTON = "cast_button"
    const val SETTINGS_BUTTON = "settings_button"

    /** Every key the app is expected to be able to register. Used by `GuidedTourStepsTest` to catch
     * a step pointing at a name nothing will ever report. */
    val ALL: Set<String> = setOf(
        PLAYLIST_ADD,
        CHANNEL_SEARCH,
        FAVORITE_BUTTON,
        PLAYER_FULLSCREEN,
        EPG_BUTTON,
        CAST_BUTTON,
        SETTINGS_BUTTON,
    )
}

/**
 * What a step points at, and what it falls back to when that is not on screen.
 *
 * The fallback chain is part of the type rather than the overlay's control flow, because "the
 * element is missing" is the *normal* case for several of these, not an error. A tour is most useful
 * to someone who has not set the app up yet - and that user has no playlist, so no channel list; no
 * channel playing, so no player controls; possibly no Chromecast on the network at all. A design
 * that could only run when everything already existed would be a tour for people who no longer need
 * one.
 */
sealed interface GuidedTourTarget {

    /**
     * A live element, highlighted where it actually is. [fallbackScreenshot] is shown instead when
     * nothing has registered [key] - see [GuidedTourTarget]'s own note on why that is expected.
     */
    data class Element(val key: String, @param:DrawableRes val fallbackScreenshot: Int? = null) : GuidedTourTarget

    /** A prepared image of the screen being described, for something that cannot be pointed at
     * live - a player control that only exists while a channel is playing, say. */
    data class Screenshot(@param:DrawableRes val resource: Int) : GuidedTourTarget

    /** Text only. Also where [Element] lands when the element is absent and there is no screenshot. */
    data object None : GuidedTourTarget
}

/**
 * Which side of the highlighted element the card sits on.
 *
 * [AUTO] is the default and what every current step uses: the overlay puts the card in whichever
 * half of the screen the element is *not* in. The explicit values exist for a step that needs to
 * override that - a target near the vertical middle, where the automatic choice is arbitrary.
 */
enum class TooltipPosition { AUTO, ABOVE, BELOW }

/**
 * One step of the tour, as data.
 *
 * Steps are a list of these rather than a list of composables on purpose: the overlay renders any
 * step the same way, so adding, reordering or translating a step is an edit to
 * [GuidedTourSteps.DEFAULT] and nothing else.
 *
 * There is deliberately no per-step `showNext`/`showBack`/`skippable`. Back is "not the first step",
 * Next is always available, and Skip is always available because a tour the user cannot leave is a
 * modal trap. Three flags that every step would set the same way are configuration that only looks
 * like flexibility.
 *
 * @param id stable, non-localised; what a log line names when a target cannot be found.
 * @param destination the tab this step is about, or null to stay wherever the user is. The tour
 *   switches to it through the app's existing bottom-navigation state - see `RootScaffold`.
 */
data class GuidedTourStep(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val target: GuidedTourTarget,
    val destination: BottomDestination?,
    val position: TooltipPosition = TooltipPosition.AUTO,
)
