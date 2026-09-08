package com.uacastplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uacastplayer.ui.guidedtour.guidedTourTarget
import com.uacastplayer.ui.theme.CaptionSemibold
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.raisedSurface

private val RailWidth = 96.dp
private val RailItemSpacing = 4.dp
private val RailVerticalPadding = 4.dp

/** Medium/expanded counterpart of [GlassTabBar], retaining the same selection semantics. */
@Composable
fun GlassNavigationRail(items: List<TabBarItem>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(RailWidth)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .background(UaTheme.palette.glassTone)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(RailItemSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items.forEach { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(item.tourKey?.let { Modifier.guidedTourTarget(it) } ?: Modifier)
                    .clip(RoundedCornerShape(RadiusItem))
                    .then(
                        if (item.selected) {
                            Modifier.raisedSurface(
                                RoundedCornerShape(RadiusItem),
                                UaTheme.palette.accentGradient,
                                shadow = false,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .selectable(
                        selected = item.selected,
                        role = Role.Tab,
                        onClick = item.onClick,
                    )
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 6.dp, vertical = RailVerticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val contentColor = if (item.selected) {
                    UaTheme.palette.accentOnFill
                } else {
                    UaTheme.palette.labelSecondary
                }
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.contentDescription,
                    tint = contentColor,
                    modifier = Modifier.size(26.dp),
                )
                run {
                    Text(
                        text = item.label,
                        style = CaptionSemibold,
                        color = contentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
