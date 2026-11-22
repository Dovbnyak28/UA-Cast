package app.zorik.player.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.AppThemePreviewParameter
import app.zorik.player.ui.theme.BodyRegular
import app.zorik.player.ui.theme.UaTheme
import app.zorik.player.ui.theme.ZorikTheme
import app.zorik.player.ui.theme.Title
import app.zorik.player.ui.theme.CardTitle
import app.zorik.player.ui.theme.panelSurface

private const val CONTENT_SIZE_ANIMATION_MILLIS = 220

/** A top-aligned recovery card; bounded short windows scroll instead of clipping the action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.TopCenter) {
        val scroll = rememberScrollState()
        IconHeader(
            icon = icon,
            title = title,
            subtitle = subtitle,
            primaryActionLabel = primaryActionLabel,
            onPrimaryAction = onPrimaryAction,
            compact = maxHeight < 300.dp,
            modifier = if (constraints.hasBoundedHeight) Modifier.verticalScroll(scroll) else Modifier,
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
    compact: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(max = 440.dp).fillMaxWidth()
            .then(if (animationsAllowed()) {
                Modifier.animateContentSize(tween(CONTENT_SIZE_ANIMATION_MILLIS))
            } else Modifier)
            .panelSurface(RoundedCornerShape(24.dp), featured = true).padding(if (compact) 16.dp else 24.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 44.dp else 64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(UaTheme.palette.azure.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = UaTheme.palette.azure,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = title,
            style = if (compact) CardTitle else Title,
            textAlign = TextAlign.Center,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.padding(top = if (compact) 12.dp else 20.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = BodyRegular,
                textAlign = TextAlign.Center,
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
    ZorikTheme(theme) {
        EmptyState(icon = AppIcons.Favorites, title = "No favorites yet", subtitle = "Star a channel to add it here")
    }
}
