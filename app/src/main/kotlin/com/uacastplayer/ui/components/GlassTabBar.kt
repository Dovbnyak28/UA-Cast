package com.uacastplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.uacastplayer.ui.guidedtour.guidedTourTarget
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.AppThemePreviewParameter
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.navigationBarHeight
import com.uacastplayer.ui.theme.LARGE_NAVIGATION_FONT_SCALE
import com.uacastplayer.ui.theme.panelSurface
import com.uacastplayer.ui.theme.navigationSelectionColor

/** Tonal dock; large text keeps two rows with complete, readable destination names. */
@Composable
fun GlassTabBar(items: List<TabBarItem>, modifier: Modifier = Modifier) {
    val largeText = LocalDensity.current.fontScale >= LARGE_NAVIGATION_FONT_SCALE
    Column(
        modifier.fillMaxWidth().panelSurface(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(navigationBarHeight(LocalDensity.current.fontScale))
            .selectableGroup().testTag(UiTestTags.NAVIGATION_DOCK),
    ) {
        val rows = if (largeText) items.chunked(2) else listOf(items)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                row.forEach { item ->
                    TabBarButton(item, largeText, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun TabBarButton(item: TabBarItem, largeText: Boolean, modifier: Modifier) {
    val selectedColor = if (item.selected) UaTheme.palette.azure else UaTheme.palette.labelSecondary
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.then(item.tourKey?.let { Modifier.guidedTourTarget(it) } ?: Modifier)
            .selectable(selected = item.selected, role = Role.Tab, onClick = item.onClick)
            .semantics(mergeDescendants = true) { contentDescription = item.contentDescription }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val highlight = if (item.selected) {
            Modifier.background(navigationSelectionColor(UaTheme.palette), shape)
        } else Modifier
        if (largeText) {
            Row(highlight.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(item.icon, null, tint = selectedColor, modifier = Modifier.size(24.dp))
                TabBarLabel(item.largeTextLabel, item.selected)
            }
        } else {
            Column(Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Box(highlight.padding(horizontal = 16.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
                    Icon(item.icon, null, tint = selectedColor, modifier = Modifier.size(24.dp))
                }
                TabBarLabel(item.label, item.selected)
            }
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
