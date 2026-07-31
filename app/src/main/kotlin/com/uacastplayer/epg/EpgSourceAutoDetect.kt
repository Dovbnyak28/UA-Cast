package com.uacastplayer.epg

/**
 * Decides what to do with EPG URL(s) found in a freshly (re)loaded playlist - either from its own
 * `#EXTM3U` header (see [com.uacastplayer.playlist.M3uParser]) or synthesized for an Xtream source
 * (see `com.uacastplayer.playlist.XtreamUrlBuilder`). Deliberately only consulted on an actual
 * load, never on a startup cache restore, so this can't refire every time the app opens.
 */
object EpgSourceAutoDetect {
    sealed class Action {
        /** No EPG source has been chosen manually yet - safe to switch to the playlist's own EPG
         * without asking, since a device with no explicit preference has nothing to lose. */
        data class Apply(val url: String) : Action()

        /** The user already chose an EPG source manually - don't override it, just let them know
         * one was found in case they'd rather switch. */
        data class Suggest(val url: String) : Action()
        data object Ignore : Action()
    }

    fun decide(epgUrls: List<String>, hasChosenEpgSource: Boolean, currentUrl: String?): Action {
        val url = epgUrls.firstOrNull { it.isNotBlank() }
        return when {
            url == null || url == currentUrl -> Action.Ignore
            hasChosenEpgSource -> Action.Suggest(url)
            else -> Action.Apply(url)
        }
    }
}
