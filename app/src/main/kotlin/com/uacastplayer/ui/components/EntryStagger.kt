package com.uacastplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.uacastplayer.ui.theme.DUR_ENTER
import com.uacastplayer.ui.theme.EaseSpring
import com.uacastplayer.ui.theme.EntryLift
import com.uacastplayer.ui.theme.STAGGER_MS
import kotlinx.coroutines.delay

/** Only the first screenful gets an entry animation - see [EntryStagger]. */
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
 * it is lagging rather than arriving. Past the cap items appear immediately, without a fade.
 *
 * [key] must be the same key the lazy list itself uses, so "already played" survives recycling.
 * Lazy containers can pass their already-observed [animationsEnabled] policy rather than
 * registering a settings observer separately for every visible row.
 */
@Composable
fun Modifier.staggeredEntry(
    stagger: EntryStagger,
    key: Any,
    index: Int,
    animationsEnabled: Boolean? = null,
): Modifier {
    // Static/recycled rows need neither an animation clock nor a persistent graphics layer.
    if (index !in 0 until MAX_STAGGERED_ITEMS) return this
    val enabled = animationsEnabled ?: animationsAllowed()
    // Completion belongs to the row, not the disposable animated branch. A policy change must
    // not hide already-visible content; statically displayed rows also stay seen after recycling.
    var complete by remember(stagger, key) { mutableStateOf(!enabled || stagger.hasPlayed(key)) }
    LaunchedEffect(stagger, key, enabled) {
        if (!enabled) {
            stagger.markPlayed(key)
            complete = true
        }
    }
    return if (complete || !enabled) this else animatedEntry(stagger, key, index) { complete = true }
}

@Composable
private fun Modifier.animatedEntry(stagger: EntryStagger, key: Any, index: Int, onComplete: () -> Unit): Modifier {
    val progress = remember(stagger, key) { Animatable(0f) }
    val lift = with(LocalDensity.current) { EntryLift.toPx() }

    LaunchedEffect(stagger, key) {
        stagger.markPlayed(key)
        delay(index.toLong() * STAGGER_MS)
        progress.animateTo(1f, tween(DUR_ENTER, easing = EaseSpring))
        onComplete()
    }

    // graphicsLayer's lambda form: the animated value is read at draw time, so each frame of this
    // costs a redraw of one item and no recomposition.
    return this.graphicsLayer {
        val value = progress.value
        alpha = value
        translationY = (1f - value) * lift
    }
}
