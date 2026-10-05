package com.uacastplayer.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.UaCastTheme

private const val CONTENT_SIZE_ANIMATION_MILLIS = 220

/** Full-screen centered icon/title/subtitle - for a screen (or tab) with no other content. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        IconHeader(
            icon = icon,
            title = title,
            subtitle = subtitle,
            primaryActionLabel = primaryActionLabel,
            onPrimaryAction = onPrimaryAction,
        )
    }
}

/** The content-sized icon/title block for layouts where a full-screen [EmptyState] would conflict
 * with a scrolling sibling, such as import controls. */
@Composable
fun IconHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.animateContentSize(tween(CONTENT_SIZE_ANIMATION_MILLIS)).padding(32.dp),
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(UaTheme.palette.surface1),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = UaTheme.palette.azure,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = title,
            style = BodyText,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.padding(top = 20.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = BodyRegular,
                color = UaTheme.palette.labelSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (primaryActionLabel != null && onPrimaryAction != null) {
            PrimaryButton(
                text = primaryActionLabel,
                onClick = onPrimaryAction,
                modifier = Modifier.padding(top = 20.dp).widthIn(max = 360.dp),
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
