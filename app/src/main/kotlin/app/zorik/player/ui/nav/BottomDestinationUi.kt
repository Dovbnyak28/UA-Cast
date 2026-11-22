package app.zorik.player.ui.nav
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import app.zorik.player.ui.theme.AppIcons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.zorik.player.R
import app.zorik.player.core.nav.BottomDestination
import app.zorik.player.guidedtour.GuidedTourKeys
import app.zorik.player.ui.components.animationsAllowed
import app.zorik.player.ui.theme.DUR_NAV
import app.zorik.player.ui.theme.EaseSpring


internal fun BottomDestination.labelRes(): Int = when (this) {
    BottomDestination.HOME -> R.string.nav_home
    BottomDestination.CHANNELS -> R.string.nav_channels
    BottomDestination.FAVORITES -> R.string.nav_favorites
    BottomDestination.SETTINGS -> R.string.nav_settings
}

/** Two tabs are tour targets; the other steps point at things inside a screen rather than at the
 * tab that reaches it. Null elsewhere rather than a key nothing uses. */
internal fun BottomDestination.tourKey(): String? = when (this) {
    BottomDestination.FAVORITES -> GuidedTourKeys.FAVORITE_BUTTON
    BottomDestination.SETTINGS -> GuidedTourKeys.SETTINGS_BUTTON
    else -> null
}

internal fun BottomDestination.icon() = when (this) {
    BottomDestination.HOME -> app.zorik.player.ui.theme.AppIcons.Home
    BottomDestination.CHANNELS -> app.zorik.player.ui.theme.AppIcons.Channels
    BottomDestination.FAVORITES -> app.zorik.player.ui.theme.AppIcons.Favorites
    BottomDestination.SETTINGS -> app.zorik.player.ui.theme.AppIcons.Settings
}

internal fun AnimatedContentTransitionScope<BottomDestination>.navigationTransition(
    animationsAllowed: Boolean,
): ContentTransform {
    if (!animationsAllowed) return EnterTransition.None togetherWith ExitTransition.None

    val movingForward = targetState.ordinal > initialState.ordinal
    val enterOffset = if (movingForward) {
        { width: Int -> width / 10 }
    } else {
        { width: Int -> -width / 10 }
    }
    val exitOffset = if (movingForward) {
        { width: Int -> -width / 10 }
    } else {
        { width: Int -> width / 10 }
    }
    return (slideInHorizontally(
        initialOffsetX = enterOffset,
        animationSpec = tween(DUR_NAV, easing = EaseSpring),
    ) + fadeIn(tween(DUR_NAV, easing = EaseSpring))) togetherWith
        (slideOutHorizontally(
            targetOffsetX = exitOffset,
            animationSpec = tween(DUR_NAV, easing = EaseSpring),
        ) + fadeOut(tween(DUR_NAV, easing = EaseSpring)))
}
