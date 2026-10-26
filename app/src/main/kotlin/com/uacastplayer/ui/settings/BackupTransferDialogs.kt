package com.uacastplayer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.uacastplayer.R
import com.uacastplayer.backup.BackupPreview
import com.uacastplayer.core.security.BackupCipher
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.tv.TvDialogInputRegistration
import com.uacastplayer.ui.tv.tvFocus
import com.uacastplayer.ui.tv.tvTextFieldNavigation

@Composable
internal fun BackupPasswordDialog(
    exporting: Boolean,
    failed: Boolean,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    // Deliberately not rememberSaveable: secrets must not enter the Activity state bundle.
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val valid = password.isNotBlank() &&
        password.length in BackupCipher.MIN_PASSWORD_LENGTH..BackupCipher.MAX_PASSWORD_LENGTH &&
        (!exporting || password == repeated)
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        containerColor = UaTheme.palette.surface2,
        title = { Text(stringResource(R.string.backup_password_title)) },
        text = {
            TvDialogInputRegistration()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(
                    if (exporting) R.string.backup_password_export_hint else R.string.backup_password_import_hint,
                ))
                SecretField(password, { password = it }, R.string.backup_password_label)
                if (exporting) SecretField(repeated, { repeated = it }, R.string.backup_password_repeat)
                if (failed) Text(stringResource(R.string.backup_password_failed), color = UaTheme.palette.routeRed)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, modifier = Modifier.tvFocus(enabled = valid), onClick = {
                val chars = password.toCharArray()
                password = ""
                repeated = ""
                try { onConfirm(chars) } finally { chars.fill('\u0000') }
            }) {
                Text(stringResource(
                    if (exporting) R.string.settings_data_export_confirm else R.string.backup_password_continue,
                ))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.tvFocus()) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: Int) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= BackupCipher.MAX_PASSWORD_LENGTH) onChange(it) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        visualTransformation = PasswordVisualTransformation(),
        colors = uaTextFieldColors(),
        modifier = Modifier.tvTextFieldNavigation(),
    )
}

@Composable
internal fun BackupPreviewDialog(preview: BackupPreview, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UaTheme.palette.surface2,
        title = { Text(stringResource(R.string.backup_preview_title)) },
        text = {
            TvDialogInputRegistration()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(
                    R.string.backup_preview_counts,
                    preview.sourceCount, preview.favoriteCount, preview.localPlaylistCount,
                ))
                Text(stringResource(R.string.backup_preview_merge_hint))
                if (preview.skippedSourceCount > 0) {
                    Text(stringResource(R.string.settings_data_import_limit, preview.skippedSourceCount))
                }
                Text(stringResource(R.string.backup_preview_settings, preview.changedSettings.size))
                preview.changedSettings.forEach { Text(stringResource(settingLabel(it))) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.tvFocus()) {
                Text(stringResource(R.string.backup_preview_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.tvFocus()) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

private fun settingLabel(key: String): Int = when (key) {
    "iconDisplayMode" -> R.string.backup_setting_icons
    "listDensity" -> R.string.backup_setting_density
    "bufferSize" -> R.string.settings_buffer_size_label
    else -> R.string.settings_epg_source_label
}

@Composable
internal fun BackupProgressDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UaTheme.palette.surface2,
        title = { Text(stringResource(R.string.backup_working)) },
        text = { TvDialogInputRegistration(); CircularProgressIndicator(color = UaTheme.palette.azure) },
        confirmButton = {},
    )
}
