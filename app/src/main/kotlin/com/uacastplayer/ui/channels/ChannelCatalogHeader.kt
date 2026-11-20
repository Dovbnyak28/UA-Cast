package com.uacastplayer.ui.channels

import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.guidedtour.GuidedTourKeys
import com.uacastplayer.ui.guidedtour.guidedTourTarget
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.RadiusField

/** Keep back and layout available while search/categories scroll out of the constrained viewport. */
@Composable
internal fun ChannelCatalogToolbar(
    grouped: GroupedChannels,
    layout: ChannelLayout,
    onLayoutChange: (ChannelLayout) -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 48.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                AppIcons.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = UaTheme.palette.labelPrimary,
            )
        }
        Text(
            text = groupLabel(grouped.group),
            style = com.uacastplayer.ui.theme.CardTitle,
            color = UaTheme.palette.labelPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
        )
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            ChannelLayoutMenu(selected = layout, onSelect = onLayoutChange)
        }
    }

}

@Composable
internal fun ChannelCatalogHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    categoryStrip: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        categoryStrip()

        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it) },
            placeholder = { Text(stringResource(R.string.channels_search_hint)) },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = UaTheme.palette.labelSecondary) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.channels_clear_search),
                            tint = UaTheme.palette.labelSecondary)
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(RadiusField),
            colors = uaTextFieldColors(),
            // Same tour key as the groups screen's own search field. The two are never composed
            // together - this one exists only inside an opened group - so whichever the user is
            // looking at is the one the tour highlights.
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GapM)
                .guidedTourTarget(GuidedTourKeys.CHANNEL_SEARCH),
        )

    }
}
