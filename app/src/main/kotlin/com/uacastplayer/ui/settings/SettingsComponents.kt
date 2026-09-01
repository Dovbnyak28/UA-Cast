package com.uacastplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.components.SegmentedControl
import com.uacastplayer.ui.premium.PremiumBadge
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.raisedSurface
import java.util.Locale

private const val CHEVRON_ROTATION_DEGREES = -90f
private const val BYTES_PER_KIB = 1024L
private const val BYTES_PER_MIB = BYTES_PER_KIB * BYTES_PER_KIB

@Composable
internal fun SettingsSection(
    title: String,
    icon: ImageVector,
    locked: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(UaTheme.palette.azure.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = UaTheme.palette.azure, modifier = Modifier.size(18.dp))
            }
            Text(
                text = title,
                style = CardTitle,
                color = UaTheme.palette.labelPrimary,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            if (locked) PremiumBadge()
        }
        Column(modifier = Modifier.padding(top = 16.dp)) { content() }
    }
}

@Composable
internal fun PlaylistActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .raisedSurface(
                RoundedCornerShape(RadiusItem),
                UaTheme.palette.surface1,
                edgeColor = UaTheme.palette.hairline,
                shadow = true,
            )
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = UaTheme.palette.labelSecondary, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        Icon(
            AppIcons.ChevronDown,
            contentDescription = null,
            tint = UaTheme.palette.labelSecondary,
            modifier = Modifier.size(16.dp).rotate(CHEVRON_ROTATION_DEGREES),
        )
    }
}

/** Top-level settings destination: two-line hierarchy instead of another fully expanded section. */
@Composable
internal fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .raisedSurface(
                RoundedCornerShape(RadiusItem),
                UaTheme.palette.surface1,
                edgeColor = UaTheme.palette.hairline,
                shadow = false,
            )
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(UaTheme.palette.azure.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = UaTheme.palette.azure, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                style = CardTitle,
                color = UaTheme.palette.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = Caption,
                color = UaTheme.palette.labelSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            AppIcons.ChevronDown,
            contentDescription = null,
            tint = UaTheme.palette.labelSecondary,
            modifier = Modifier.size(16.dp).rotate(CHEVRON_ROTATION_DEGREES),
        )
    }
}

@Composable
internal fun LabeledRow(label: String, icon: ImageVector, content: @Composable () -> Unit) {
    RowLabel(label, icon)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Label chrome for a single full-width [SegmentedControl]. */
@Composable
internal fun SegmentedRow(label: String, icon: ImageVector, content: @Composable () -> Unit) {
    RowLabel(label, icon)
    content()
}

@Composable
private fun RowLabel(label: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        Icon(
            icon,
            contentDescription = null,
            tint = UaTheme.palette.labelSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            // The label is part of the setting's hit target, not just decoration. A single
            // toggleable parent also gives TalkBack one unambiguous switch semantics.
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = UaTheme.palette.labelPrimary,
                checkedTrackColor = UaTheme.palette.azure,
                checkedBorderColor = UaTheme.palette.azure,
                uncheckedThumbColor = UaTheme.palette.labelSecondary,
                uncheckedTrackColor = UaTheme.palette.surface2,
                uncheckedBorderColor = UaTheme.palette.hairline,
            ),
        )
    }
}

@Composable
internal fun CacheRow(labelRes: Int, sizeBytes: Long, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            AppIcons.Storage,
            contentDescription = null,
            tint = UaTheme.palette.labelSecondary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = stringResource(labelRes),
                style = BodyRegular,
                color = UaTheme.palette.labelPrimary,
            )
            Text(
                text = formatBytes(sizeBytes),
                style = Caption,
                color = UaTheme.palette.labelSecondary,
            )
        }
        SecondaryButton(text = stringResource(R.string.cache_clear_button), onClick = onClear)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= BYTES_PER_MIB -> "%.1f MB".format(Locale.ROOT, bytes.toDouble() / BYTES_PER_MIB)
    bytes >= BYTES_PER_KIB -> "%.1f KB".format(Locale.ROOT, bytes.toDouble() / BYTES_PER_KIB)
    else -> "$bytes B"
}

/** Option row for choices too numerous or long for [SegmentedControl]. */
@Composable
internal fun SettingsChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(RadiusItem))
            .background(if (isSelected) UaTheme.palette.azure else UaTheme.palette.surface2)
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isSelected) {
            Icon(
                AppIcons.Check,
                contentDescription = null,
                tint = UaTheme.palette.labelPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = BodyRegular,
            color = if (isSelected) UaTheme.palette.labelPrimary else UaTheme.palette.labelSecondary,
        )
    }
}
