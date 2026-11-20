package com.uacastplayer.ui.components
import com.uacastplayer.ui.theme.raisedSurface
import com.uacastplayer.ui.theme.sunkenSurface
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.AppThemePreviewParameter
import com.uacastplayer.ui.theme.UaCastTheme


// The app is dark-only (see UaCastTheme's KDoc) - previews below use this instead of Studio's
// default white canvas so raisedSurface/sunkenSurface depth cues are visible at a glance.
private const val PREVIEW_BACKGROUND = 0xFF0B0B12L

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun GradientPlayButtonPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        GradientPlayButton(icon = AppIcons.Play, onClick = {})
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun RoundIconButtonPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIconButton(icon = AppIcons.SkipPrevious, onClick = {})
            RoundIconButton(icon = AppIcons.SkipNext, onClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun SmallRoundIconButtonPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        SmallRoundIconButton(icon = AppIcons.Settings, onClick = {})
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun StatusPillPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(text = "Direct", variant = StatusPillVariant.Good)
            StatusPill(text = "Proxy", variant = StatusPillVariant.Proxy)
            StatusPill(text = "Failed", variant = StatusPillVariant.Bad)
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun GlowStatusDotPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlowStatusDot(variant = StatusPillVariant.Good)
            GlowStatusDot(variant = StatusPillVariant.Proxy)
            GlowStatusDot(variant = StatusPillVariant.Bad)
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND, widthDp = 300)
@Composable
private fun SegmentedControlPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        SegmentedControl(options = listOf("Small", "Medium", "Large"), selectedIndex = 1, onSelected = {})
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND, widthDp = 300)
@Composable
private fun TrackProgressPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrackProgress(progress = 0.35f)
            TrackProgress(progress = 0.7f, bold = true)
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun SecondaryButtonPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(text = "Export", onClick = {})
            SecondaryButton(text = "Disabled", onClick = {}, enabled = false)
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Composable
private fun TabBarLabelPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TabBarLabel(text = "Channels", selected = true)
            TabBarLabel(text = "Favorites", selected = false)
        }
    }
}
