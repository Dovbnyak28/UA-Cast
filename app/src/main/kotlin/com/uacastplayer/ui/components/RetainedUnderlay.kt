package com.uacastplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent

/** Preserve navigation/views while avoiding overdraw behind a fully opaque player. */
@Composable
internal fun RetainedUnderlay(
    covered: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // Read coverage during drawing: a player's opacity callback can commit after composition
    // but before the first frame. It must not cause one extra paint or a root recomposition.
    Box(modifier.drawWithContent { if (!covered()) drawContent() }, content = content)
}
