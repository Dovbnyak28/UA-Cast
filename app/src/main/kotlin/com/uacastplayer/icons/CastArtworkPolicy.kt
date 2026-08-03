package com.uacastplayer.icons

/**
 * Which of a channel's icon [candidates][IconCandidate] may be handed to a Cast receiver as
 * artwork - the first [IconCandidate.Fetchable] one, in the order [IconResolver.candidates]
 * produced them.
 *
 * Reusing that chain rather than reading `tvg-logo` directly is the whole point: the receiver ends
 * up showing the same picture the phone does, resolved the same way, so the two cannot disagree
 * about which logo belongs to a channel. Before this, only `tvg-logo` reached the receiver, and a
 * playlist entry carrying just a `tvg-id` showed a logo in the channel list and a bare title on a
 * black screen on the TV.
 *
 * **[IconCandidate.CacheOnly] is excluded, and that is the rule worth stating.** The CDN
 * fallback-by-`tvg-id` is a guess - see [IconCandidate]'s own doc for why it is never fetched
 * speculatively. On this side of the app a wrong guess is free (nothing is on disk, nothing is
 * shown), but a receiver has no cache to check the URL against: it fetches whatever it is given and,
 * per [com.uacastplayer.cast.CastMediaLoader], reports a broken image as an *error* rather than
 * falling back to its no-artwork layout. Passing one would trade a clean title-only screen for a
 * failure message.
 *
 * A `CacheOnly` URL that already sits in the disk cache *has* been proven to resolve and would be
 * safe to send. It is left out anyway: confirming it is a suspending disk-cache lookup, and the
 * caller (`PlayerViewModel.switchToIndexImmediate`) resolves artwork synchronously while switching
 * channels.
 */
object CastArtworkPolicy {

    fun artworkUrl(candidates: List<IconCandidate>): String? =
        candidates.filterIsInstance<IconCandidate.Fetchable>().firstOrNull()?.url
}
