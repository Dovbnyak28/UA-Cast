package app.zorik.player.guidedtour

/**
 * What the guidance layer is showing: nothing, the intro, or one tip.
 *
 * Never both at once - a tip waits until the intro is closed, and only one tip is ever up.
 */
enum class GuidedTourPhase { IDLE, INTRO, TIP }

/**
 * The whole of the guidance state, and the only thing the UI reads.
 *
 * [introPage] is always a valid index into [pages] while [phase] is [GuidedTourPhase.INTRO], and
 * [tip] is non-null exactly while it is [GuidedTourPhase.TIP] -
 * [app.zorik.player.app.GuidedTourController] is what guarantees both.
 */
data class GuidedTourState(
    val phase: GuidedTourPhase = GuidedTourPhase.IDLE,
    val introPage: Int = 0,
    val tip: GuidedTip? = null,
    val pages: List<IntroPage> = IntroPage.entries,
) {
    val isVisible: Boolean get() = phase != GuidedTourPhase.IDLE

    val isLastIntroPage: Boolean get() = introPage >= pages.lastIndex
}
