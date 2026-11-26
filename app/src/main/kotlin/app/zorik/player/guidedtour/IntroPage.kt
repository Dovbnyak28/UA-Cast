package app.zorik.player.guidedtour

import androidx.annotation.StringRes
import app.zorik.player.R

/**
 * The first-launch intro: three full-screen slides, as data. Each slide's picture is drawn by
 * `IntroIllustration` from the entry itself, so the model carries no drawables.
 *
 * Three, and about what the app is *for* - get channels in, watch, put them on the TV - rather than
 * a tour of its screens. Where things are is taught later, by [GuidedTip]s, at the moment the thing
 * exists.
 */
enum class IntroPage(@param:StringRes val titleRes: Int, @param:StringRes val bodyRes: Int) {
    PLAYLIST(R.string.intro_playlist_title, R.string.intro_playlist_body),
    WATCH(R.string.intro_watch_title, R.string.intro_watch_body),
    CAST(R.string.intro_cast_title, R.string.intro_cast_body),
}

/**
 * Which edition of the intro a device has seen.
 *
 * Stored next to the completion flag so a later release can offer a new intro once to someone who
 * already saw the old one, without that also meaning "show it on every launch forever" - see
 * [GuidedTourAvailability]. 2 is the intro-and-tips edition; 1 was the seven-step tour.
 */
object GuidedTourVersion {
    const val CURRENT = 2
}
