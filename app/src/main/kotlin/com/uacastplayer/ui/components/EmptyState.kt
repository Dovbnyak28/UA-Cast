package com.uacastplayer.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.AppThemePreviewParameter
import com.uacastplayer.ui.theme.UaCastTheme

/** Full-screen centered icon/title/subtitle - for a screen (or tab) with no other content. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        IconHeader(icon = icon, title = title, subtitle = subtitle)
    }
}

/** The icon-badge/title/subtitle block on its own, sized to its content - for use alongside other composables (e.g. import controls) where a full-screen centered [EmptyState] would conflict with a scrolling sibling. */
@Composable
fun IconHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.animateContentSize(tween(220)).padding(32.dp),
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B12L)
@Composable
private fun EmptyStatePreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        EmptyState(icon = AppIcons.Favorites, title = "No favorites yet", subtitle = "Star a channel to add it here")
    }
}
