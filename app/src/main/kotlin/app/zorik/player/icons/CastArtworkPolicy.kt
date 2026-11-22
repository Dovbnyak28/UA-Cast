package app.zorik.player.icons

/** Chromecast artwork follows the same explicitly selected pack order as local channel logos. */
object CastArtworkPolicy {
    fun artworkUrl(candidates: List<IconCandidate>): String? = candidates.firstOrNull()?.url
}
