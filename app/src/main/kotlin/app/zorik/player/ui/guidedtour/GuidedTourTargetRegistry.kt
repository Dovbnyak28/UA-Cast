package app.zorik.player.ui.guidedtour

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
 * Where the tips' targets are on screen right now, and since when.
 *
 * Elements register themselves by name through [guidedTourTarget]; the host looks a name up to
 * decide whether a tip can show, and the overlay to cut a hole in its scrim. Nothing anywhere holds
 * a coordinate: a screen that changes its layout keeps working, and a screen that is not composed
 * simply has no entry.
 *
 * Each arrival is numbered ([appearanceOf]). That is what makes "one tip per appearance" work: the
 * player page brings the video and the Cast button on screen together, the first tip shows, and the
 * second waits for the *next* time the player opens instead of firing the moment the first is
 * dismissed - see `GuidedTourHost`.
 *
 * [isCollecting] is off once every tip has been seen and nothing is showing, and [guidedTourTarget]
 * then adds no modifier at all - so a user who is past the tips pays nothing for the targets
 * declared in the screens.
 */
@Stable
class GuidedTourTargetRegistry {

    var isCollecting: Boolean by mutableStateOf(false)
        internal set

    private val bounds = mutableStateMapOf<String, Rect>()
    private val appearances = mutableStateMapOf<String, Int>()

    /** The number given to the most recent arrival. Not observable: read only at the moment a tip
     * is dismissed, to remember how far the user has been shown things. */
    var latestAppearance: Int = 0
        private set

    fun boundsOf(key: String): Rect? = bounds[key]

    /** Increases every time [key] arrives on screen; null while it is absent. */
    fun appearanceOf(key: String): Int? = appearances[key]

    internal fun report(key: String, rect: Rect) {
        if (key !in appearances) {
            latestAppearance += 1
            appearances[key] = latestAppearance
        }
        if (bounds[key] != rect) bounds[key] = rect
    }

    internal fun forget(key: String) {
        bounds.remove(key)
        appearances.remove(key)
    }
}

/**
 * The registry the nearest `GuidedTourHost` created. Static: the host never replaces it, so reading
 * it costs nothing and never invalidates a reader.
 */
val LocalGuidedTourTargets = staticCompositionLocalOf { GuidedTourTargetRegistry() }

/**
 * Registers this element under [key] while the registry is collecting.
 *
 * A composable modifier factory rather than a plain one because it reads [LocalGuidedTourTargets]
 * and needs a [DisposableEffect]: an element that leaves the composition must take its entry with
 * it, or a tip would spotlight the empty place where a screen used to be.
 */
@Composable
fun Modifier.guidedTourTarget(key: String): Modifier {
    val registry = LocalGuidedTourTargets.current
    if (!registry.isCollecting) return this
    DisposableEffect(registry, key) { onDispose { registry.forget(key) } }
    return onGloballyPositioned { registry.report(key, it.boundsInRoot()) }
}
