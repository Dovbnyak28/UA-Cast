package com.uacastplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.uacastplayer.ui.theme.DUR_ENTER
import com.uacastplayer.ui.theme.EaseSpring
import com.uacastplayer.ui.theme.EntryLift
import com.uacastplayer.ui.theme.STAGGER_MS
import kotlinx.coroutines.delay

/** How many items get a delay before the wave flattens out - see [EntryStagger]. */
private const val MAX_STAGGERED_ITEMS = 10

/**
 * Remembers which items have already played their entry animation.
 *
 * This is the whole reason the stagger needs a state object rather than a `remember` inside each
 * item: a lazy list **disposes** items that scroll out of view and composes them again on the way
 * back, which takes any per-item `remember` with it. Without a record that outlives the item, every
 * scroll up would replay the fade, and a list would appear to be loading over and over.
 *
 * [resetKey] is what the wave is a reaction to - normally the list's contents. Passing a new one
 * (a different playlist, a different group) lets the animation play again, which is correct: that
 * genuinely is new content arriving, not the same content scrolling past.
 */
@Composable
fun rememberEntryStagger(resetKey: Any?): EntryStagger = remember(resetKey) { EntryStagger() }

@Stable
class EntryStagger internal constructor() {
    private val played = HashSet<Any>()

    internal fun hasPlayed(key: Any): Boolean = key in played

    internal fun markPlayed(key: Any) {
        played += key
    }
}

/**
 * Fades and lifts an item in, delayed by its position so a screenful arrives as a wave rather than
 * all at once.
 *
 * The delay is capped at [MAX_STAGGERED_ITEMS] deliberately. A stagger that keeps growing with the
 * index is the standard way this effect turns into a defect: on a 2863-channel playlist item 400
 * would wait half a minute, and even on one screenful an uncapped wave makes the last row feel like
 * it is lagging rather than arriving. Past the cap items simply fade with no delay.
 *
 * [key] must be the same key the lazy list itself uses, so "already played" survives recycling.
 */
@Composable
fun Modifier.staggeredEntry(stagger: EntryStagger, key: Any, index: Int): Modifier {
    val animate = animationsAllowed()
    // Read once per composition of this item: if it has played before (a scroll-back), the item
    // starts fully visible and no animation is scheduled at all.
    val alreadyPlayed = remember(stagger, key) { stagger.hasPlayed(key) }
    val progress = remember(stagger, key, animate) {
        Animatable(if (alreadyPlayed || !animate) 1f else 0f)
    }
    val lift = with(LocalDensity.current) { EntryLift.toPx() }

    LaunchedEffect(stagger, key) {
        if (alreadyPlayed || !animate) return@LaunchedEffect
        stagger.markPlayed(key)
        delay(minOf(index, MAX_STAGGERED_ITEMS).toLong() * STAGGER_MS)
        progress.animateTo(1f, tween(DUR_ENTER, easing = EaseSpring))
    }

    // graphicsLayer's lambda form: the animated value is read at draw time, so each frame of this
    // costs a redraw of one item and no recomposition.
    return this.graphicsLayer {
        val value = progress.value
        alpha = value
        translationY = (1f - value) * lift
    }
}
