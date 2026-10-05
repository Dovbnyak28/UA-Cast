package com.uacastplayer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.icons.CustomIconSourcePolicy
import com.uacastplayer.settings.IconSourceAddError
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.tv.tvFocus
import com.uacastplayer.ui.tv.tvTextFieldNavigation

/** User-managed channel-logo packs, with no predefined source. */
@Composable
internal fun IconSourcesSection(
    customSources: List<String>,
    addError: IconSourceAddError?,
    onAddSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var newSourceUrl by rememberSaveable { mutableStateOf("") }
    var pendingSource by remember { mutableStateOf<String?>(null) }
    // Invoking an action is not success: validation, source limits or the feature gate can reject it.
    // Use the authoritative source list as acknowledgement, without changing the action contract.
    LaunchedEffect(customSources, pendingSource) {
        val submitted = pendingSource
        if (submitted != null && CustomIconSourcePolicy.canonicalize(submitted) in customSources) {
            if (newSourceUrl == submitted) newSourceUrl = ""
            pendingSource = null
        }
    }
    Column(
        modifier = Modifier.settingsSearchTarget(stringResource(R.string.settings_icon_sources_title))
            .padding(top = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_icon_sources_title),
            style = BodyRegular,
            color = UaTheme.palette.labelPrimary,
        )
        Text(
            text = stringResource(R.string.settings_icon_sources_hint),
            style = Caption,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        if (customSources.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_icon_sources_empty),
                style = Caption,
                color = UaTheme.palette.labelSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        customSources.forEach { source ->
            IconSourceRow(urlText = source, onRemoveClick = { onRemoveSource(source) })
        }
        SourceInput(
            value = newSourceUrl,
            hasError = addError != null,
            onValueChange = { value ->
                newSourceUrl = value
                pendingSource = null
                if (addError != null) onDismissError()
            },
            onAdd = {
                val submitted = newSourceUrl
                if (submitted.isNotBlank()) {
                    val canonical = CustomIconSourcePolicy.canonicalize(submitted)
                    // An existing source must not falsely acknowledge a rejected duplicate attempt.
                    pendingSource = submitted.takeIf { canonical != null && canonical !in customSources }
                    onAddSource(submitted)
                }
            },
        )
        addError?.let { error ->
            Text(
                text = stringResource(error.messageRes()),
                style = Caption,
                color = UaTheme.palette.routeRed,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SourceInput(
    value: String,
    hasError: Boolean,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).tvTextFieldNavigation(),
            placeholder = { Text(stringResource(R.string.settings_icon_sources_placeholder)) },
            singleLine = true,
            isError = hasError,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                capitalization = KeyboardCapitalization.None,
            ),
            colors = uaTextFieldColors(),
        )
        IconButton(onClick = onAdd, enabled = value.isNotBlank(),
            modifier = Modifier.tvFocus(enabled = value.isNotBlank())) {
            Icon(
                AppIcons.Plus,
                contentDescription = stringResource(R.string.settings_icon_sources_add),
                tint = UaTheme.palette.azure,
            )
        }
    }
}

@Composable
private fun IconSourceRow(urlText: String, onRemoveClick: () -> Unit) {
    val check = LocalIconPackCheck.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = urlText,
            style = Caption,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemoveClick, modifier = Modifier.tvFocus()) {
            Icon(
                AppIcons.Delete,
                contentDescription = stringResource(R.string.settings_icon_sources_remove),
                tint = UaTheme.palette.labelSecondary,
            )
        }
        if (check != null) TextButton(onClick = { check(urlText) }, modifier = Modifier.tvFocus()) {
            Text(stringResource(R.string.icon_pack_check_action))
        }
    }
}

private fun IconSourceAddError.messageRes(): Int = when (this) {
    IconSourceAddError.INVALID_URL -> R.string.settings_icon_sources_error_invalid
    IconSourceAddError.ALREADY_ADDED -> R.string.settings_icon_sources_error_duplicate
    IconSourceAddError.LIMIT_REACHED -> R.string.settings_icon_sources_error_limit
}
