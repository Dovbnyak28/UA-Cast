package app.zorik.player.ui.components

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
import app.zorik.player.R
import app.zorik.player.ui.UiTestTags
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.ScreenHPadding
import app.zorik.player.ui.theme.Title
import app.zorik.player.ui.theme.TouchTargetMin
import app.zorik.player.ui.theme.UaTheme
import app.zorik.player.ui.tv.tvFocus

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
        app.zorik.player.ui.remote.RemoteHeaderAction(labelRemote)
    }
}
