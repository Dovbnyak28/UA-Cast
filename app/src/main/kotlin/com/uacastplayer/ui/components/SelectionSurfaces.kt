package com.uacastplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppSpacing
import com.uacastplayer.ui.theme.TouchTargetMin
import com.uacastplayer.ui.tv.tvFocus

/** Tonal card, with no per-item shadow or extra interaction owner. Content can grow with font scale. */
@Composable
fun UaCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val interaction = if (onClick == null) Modifier else Modifier.tvFocus(shape, enabled)
        .selectable(selected = selected, enabled = enabled, role = Role.Button, onClick = onClick)
    Surface(
        modifier = modifier.heightIn(min = TouchTargetMin).then(interaction),
        shape = shape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainer,
        contentColor = if (enabled) colors.onSurface else colors.onSurfaceVariant,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
    ) {
        Column(Modifier.padding(AppSpacing.medium), content = content)
    }
}

/** Reusable multiline row. Selection has a check mark as well as a tonal background. */
@Composable
fun UaListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    ListItem(
        headlineContent = { Text(headline, style = MaterialTheme.typography.titleMedium) },
        supportingContent = supportingText?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        leadingContent = leadingIcon?.let { { Icon(it, null, Modifier.size(24.dp)) } },
        trailingContent = if (selected) { { Icon(AppIcons.Check, null, Modifier.size(24.dp)) } } else null,
        colors = ListItemDefaults.colors(
            containerColor = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
            headlineColor = if (enabled) colors.onSurface else colors.onSurfaceVariant,
            supportingColor = colors.onSurfaceVariant,
            leadingIconColor = colors.primary, trailingIconColor = colors.primary,
        ),
        modifier = modifier.heightIn(min = TouchTargetMin).clip(shape).tvFocus(shape, enabled)
            .selectable(selected = selected, enabled = enabled, role = Role.Button, onClick = onClick),
    )
}
