package app.zorik.player.guidedtour

import androidx.annotation.StringRes
import app.zorik.player.R

/**
 * The picture in front of one line of a tip. Named for what the line is about rather than for an
 * icon, so the model stays free of UI types - `GuidedTipCard` maps each to a glyph.
 */
enum class TipGlyph { SEARCH, FAVORITE, TAP, SWITCH_CHANNEL, LEVELS, CAST, OTHER_TV }

/** One line of a tip: a glyph and a short sentence. */
data class TipPoint(val glyph: TipGlyph, @param:StringRes val textRes: Int)

/**
 * A one-time hint, shown over the live app the first time the thing it explains appears.
 *
 * Replaces the seven-step tour, which walked a new user through screens they could not use yet - no
 * playlist meant no channels, no player and nothing to cast, so most of its steps were text pointing
 * at nothing. A tip waits until its element is actually on screen, says two or three lines about it,
 * and is never shown again once dismissed.
 *
 * The order of the entries is the priority: when two tips could both show, the earlier one wins.
 *
 * @param id stable and non-localised; what [GuidedTourStorage.guidedTipsSeen] stores.
 * @param targetKey the [GuidedTourKeys] entry the tip waits for and highlights.
 * @param inPlayer whether the target lives on the player page. A tip is only shown on the surface
 *   its target belongs to, so a search field still composed under an opened player cannot pull a
 *   spotlight onto a screen the user is not looking at.
 */
enum class GuidedTip(
    val id: String,
    @param:StringRes val titleRes: Int,
    val targetKey: String,
    val inPlayer: Boolean,
    val points: List<TipPoint>,
) {
    SEARCH(
        id = "search",
        titleRes = R.string.guided_tip_search_title,
        targetKey = GuidedTourKeys.CHANNEL_SEARCH,
        inPlayer = false,
        points = listOf(
            TipPoint(TipGlyph.SEARCH, R.string.guided_tip_search_find),
            TipPoint(TipGlyph.FAVORITE, R.string.guided_tip_search_hold),
        ),
    ),
    PLAYER(
        id = "player",
        titleRes = R.string.guided_tip_player_title,
        targetKey = GuidedTourKeys.PLAYER_VIDEO,
        inPlayer = true,
        points = listOf(
            TipPoint(TipGlyph.TAP, R.string.guided_tip_player_tap),
            TipPoint(TipGlyph.SWITCH_CHANNEL, R.string.guided_tip_player_swipe),
            TipPoint(TipGlyph.LEVELS, R.string.guided_tip_player_levels),
        ),
    ),
    CAST(
        id = "cast",
        titleRes = R.string.guided_tip_cast_title,
        targetKey = GuidedTourKeys.CAST_BUTTON,
        inPlayer = true,
        points = listOf(
            TipPoint(TipGlyph.CAST, R.string.guided_tip_cast_chromecast),
            TipPoint(TipGlyph.OTHER_TV, R.string.guided_tip_cast_other),
        ),
    ),
}
