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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.icons.IconResolver
import com.uacastplayer.settings.IconSourceAddError
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyRegular
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.UaTheme

/** User-managed icon CDN sources, isolated from the playback settings composition. */
@Composable
internal fun IconSourcesSection(
    customSources: List<String>,
    addError: IconSourceAddError?,
    onAddSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var newSourceUrl by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.padding(top = 16.dp)) {
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
        IconSourceRow(
            urlText = stringResource(
                R.string.settings_icon_sources_builtin,
                IconResolver.BUILT_IN_ICON_SOURCE_BASE_URL,
            ),
            onRemoveClick = null,
        )
        customSources.forEach { source ->
            IconSourceRow(urlText = source, onRemoveClick = { onRemoveSource(source) })
        }
        SourceInput(
            value = newSourceUrl,
            hasError = addError != null,
            onValueChange = { value ->
                newSourceUrl = value
                if (addError != null) onDismissError()
            },
            onAdd = {
                if (newSourceUrl.isNotBlank()) {
                    onAddSource(newSourceUrl)
                    newSourceUrl = ""
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
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.settings_icon_sources_placeholder)) },
            singleLine = true,
            isError = hasError,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                capitalization = KeyboardCapitalization.None,
            ),
            colors = uaTextFieldColors(),
        )
        IconButton(onClick = onAdd) {
            Icon(
                AppIcons.Plus,
                contentDescription = stringResource(R.string.settings_icon_sources_add),
                tint = UaTheme.palette.azure,
            )
        }
    }
}

@Composable
private fun IconSourceRow(urlText: String, onRemoveClick: (() -> Unit)?) {
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
        if (onRemoveClick != null) {
            IconButton(onClick = onRemoveClick) {
                Icon(
                    AppIcons.Delete,
                    contentDescription = stringResource(R.string.settings_icon_sources_remove),
                    tint = UaTheme.palette.labelSecondary,
                )
            }
        }
    }
}

private fun IconSourceAddError.messageRes(): Int = when (this) {
    IconSourceAddError.INVALID_URL -> R.string.settings_icon_sources_error_invalid
    IconSourceAddError.ALREADY_ADDED -> R.string.settings_icon_sources_error_duplicate
    IconSourceAddError.LIMIT_REACHED -> R.string.settings_icon_sources_error_limit
}
