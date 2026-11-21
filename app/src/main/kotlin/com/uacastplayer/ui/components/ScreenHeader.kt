package com.uacastplayer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.TouchTargetMin
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.tv.tvFocus

/** One contextual heading; the surrounding screen owns system insets and status banners. */
@Composable
internal fun ScreenHeader(
    title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, labelRemote: Boolean = false,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 4.dp)
            .testTag(UiTestTags.ROOT_TOP_BAR_TITLE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(TouchTargetMin).tvFocus()) {
            Icon(AppIcons.ArrowBack, stringResource(R.string.settings_back_to_overview),
                tint = UaTheme.palette.labelPrimary)
        }
        Text(title, style = Title, color = UaTheme.palette.labelPrimary,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        com.uacastplayer.ui.remote.RemoteHeaderAction(labelRemote)
    }
}
