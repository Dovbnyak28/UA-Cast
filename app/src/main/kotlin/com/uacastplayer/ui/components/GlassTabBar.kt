package com.uacastplayer.ui.components
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.AppThemePreviewParameter
import com.uacastplayer.ui.theme.DurPress
import com.uacastplayer.ui.theme.EaseSpring
import com.uacastplayer.ui.theme.GlassTabBarHeight
import com.uacastplayer.ui.theme.GlassTabBarVerticalPadding
import com.uacastplayer.ui.guidedtour.guidedTourTarget
import com.uacastplayer.ui.theme.PressScaleIcon
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.raisedSurface

data class TabBarItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
    /** Registers this tab as a guided-tour target under the given name; null for tabs the tour
     * never points at, which is most of them. See [com.uacastplayer.guidedtour.GuidedTourKeys]. */
    val tourKey: String? = null,
)

/** §5.10 - bottom navigation chrome: floating rounded glass bar with a highlight pill on the selected tab. */
@Composable
fun GlassTabBar(items: List<TabBarItem>, modifier: Modifier = Modifier) {
    val glassTone = UaTheme.palette.glassTone

    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = GlassTabBarVerticalPadding)
            .clip(RoundedCornerShape(24.dp))
            .background(glassTone)
            .height(GlassTabBarHeight),
    ) {
        for (item in items) {
            TabBarButton(item = item, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TabBarButton(item: TabBarItem, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressScaleIcon else 1f,
        animationSpec = tween(DurPress, easing = EaseSpring),
        label = "tabScale",
    )
    Column(
        modifier = modifier
            .then(item.tourKey?.let { Modifier.guidedTourTarget(it) } ?: Modifier)
            .scale(scale)
            .selectable(
                selected = item.selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = item.onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (item.selected) {
                        Modifier.raisedSurface(
                            RoundedCornerShape(16.dp),
                            UaTheme.palette.accentGradient,
                            shadow = false,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                // Selected pill is now a full-strength accentGradient (see the Modifier above),
                // not a translucent tint - needs accentOnFill's contrast, same as TabBarLabel.
                tint = if (item.selected) UaTheme.palette.accentOnFill else UaTheme.palette.labelSecondary,
                modifier = Modifier.height(26.dp).padding(bottom = 2.dp),
            )
            TabBarLabel(text = item.label, selected = item.selected)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B12L)
@Composable
private fun GlassTabBarPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        GlassTabBar(
            items = listOf(
                TabBarItem(label = "Channels", icon = AppIcons.Channels, selected = true, onClick = {}),
                TabBarItem(label = "Favorites", icon = AppIcons.Favorites, selected = false, onClick = {}),
                TabBarItem(label = "Settings", icon = AppIcons.Settings, selected = false, onClick = {}),
            ),
        )
    }
}
