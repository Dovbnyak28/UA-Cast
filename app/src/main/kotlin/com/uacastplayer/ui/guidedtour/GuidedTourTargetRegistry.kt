package com.uacastplayer.ui.guidedtour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where the tour's targets are on screen right now.
 *
 * Elements register themselves by name through [guidedTourTarget]; the overlay looks a name up when
 * it needs to cut a hole in its scrim. Nothing anywhere holds a coordinate: a screen that changes
 * its layout keeps working, and a screen that is not composed simply has no entry, which is what
 * the overlay's fallback reads as "not on screen".
 *
 * [isCollecting] is off whenever the tour is not running, and [guidedTourTarget] then adds no
 * modifier at all - so an app that never opens the tour pays nothing for having targets declared in
 * half a dozen places.
 */
@Stable
class GuidedTourTargetRegistry {

    /** Set by [GuidedTourHost] while the tour is visible. */
    var isCollecting: Boolean by mutableStateOf(false)
        internal set

    private val bounds = mutableStateMapOf<String, Rect>()

    fun boundsOf(key: String): Rect? = bounds[key]

    internal fun report(key: String, rect: Rect) {
        // Guarded: onGloballyPositioned fires on every layout pass, and writing an identical Rect
        // into a snapshot map would invalidate the overlay on every scroll frame.
        if (bounds[key] != rect) bounds[key] = rect
    }

    internal fun forget(key: String) {
        bounds.remove(key)
    }
}

/**
 * Static rather than plain: the registry instance is created once per Activity and never swapped,
 * and a `staticCompositionLocalOf` that never changes costs nothing to read. The default is an
 * inert registry, so a composable used in a preview or a test without a host still works.
 */
val LocalGuidedTourTargets = staticCompositionLocalOf { GuidedTourTargetRegistry() }

/**
 * Marks this element as the tour's [key] target.
 *
 * Safe to leave on an element permanently: while the tour is closed this returns the receiver
 * untouched, so there is no extra layout callback and no state to keep. The registration is
 * withdrawn when the element leaves composition, which is how a step aimed at something that is not
 * currently on screen falls back instead of highlighting a stale rectangle.
 */
@Composable
fun Modifier.guidedTourTarget(key: String): Modifier {
    val registry = LocalGuidedTourTargets.current
    if (!registry.isCollecting) return this
    DisposableEffect(registry, key) {
        onDispose { registry.forget(key) }
    }
    return onGloballyPositioned { registry.report(key, it.boundsInRoot()) }
}
