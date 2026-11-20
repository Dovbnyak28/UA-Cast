package com.uacastplayer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.uacastplayer.R
import com.uacastplayer.epg.EpgUrlPolicy
import com.uacastplayer.premium.Feature
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.premium.LocalFeatureGate
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.tv.tvTextFieldNavigation

@Composable
internal fun EpgSourceEditor(currentUrl: String?, hasError: Boolean, onApply: (String) -> Unit) {
    var draft by rememberSaveable(currentUrl) { mutableStateOf(currentUrl.orEmpty()) }
    val invalid = draft.isNotBlank() && EpgUrlPolicy.normalize(draft) == null
    val changed = draft.trim() != currentUrl.orEmpty()
    val retry = hasError && !changed && !currentUrl.isNullOrBlank()
    val gate = LocalFeatureGate.current
    Column {
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= EpgUrlPolicy.MAX_URL_LENGTH) draft = it },
            label = { Text(stringResource(R.string.settings_epg_source_label)) },
            modifier = Modifier.fillMaxWidth().tvTextFieldNavigation(),
            singleLine = true,
            isError = invalid,
            supportingText = { if (invalid) Text(stringResource(R.string.add_playlist_url_error)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = uaTextFieldColors(),
        )
        SecondaryButton(
            text = stringResource(if (retry) R.string.common_retry else R.string.settings_epg_apply),
            onClick = when {
                retry -> ({ onApply(draft.trim()) })
                draft.isBlank() -> ({ onApply("") })
                else -> gate.guard(Feature.CUSTOM_EPG_SOURCE) { onApply(draft.trim()) }
            },
            enabled = (changed || retry) && !invalid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
