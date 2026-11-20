package com.uacastplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.Layout

/** Preserve navigation/views without layout, paint or visible semantics behind an opaque player. */
@Composable
internal fun RetainedUnderlay(
    covered: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // Read coverage after composition: the player's opacity SideEffect commits before layout.
    // Unplaced content retains composition/Android views but does not expose hidden navigation
    // as visible accessibility targets or remeasure it for a fullscreen window resize.
    Layout(
        content = { Box(content = content) },
        modifier = modifier.drawWithContent { if (!covered()) drawContent() },
    ) { measurables, constraints ->
        if (covered()) {
            layout(constraints.minWidth, constraints.minHeight) { }
        } else {
            val placeable = measurables.single().measure(constraints)
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }
    }
}
