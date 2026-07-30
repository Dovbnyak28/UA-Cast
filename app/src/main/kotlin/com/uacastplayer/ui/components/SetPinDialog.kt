package com.uacastplayer.ui.components
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.parentalcontrol.ParentalControlPinPolicy
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.AppThemePreviewParameter
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.UaCastTheme

/**
 * Creates or replaces the parental-control PIN - two fields (new + confirm) so a typo doesn't lock
 * the owner out with a PIN they didn't mean to type. [onSubmit] only ever fires with a 4-digit,
 * matching PIN; format/match failures show inline instead of calling it.
 */
@Composable
fun SetPinDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    val bothEntered = pin.length == ParentalControlPinPolicy.PIN_LENGTH &&
        confirmPin.length == ParentalControlPinPolicy.PIN_LENGTH
    val mismatch = bothEntered && pin != confirmPin

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UaTheme.palette.surface2,
        titleContentColor = UaTheme.palette.labelPrimary,
        textContentColor = UaTheme.palette.labelSecondary,
        title = { Text(stringResource(R.string.parental_control_set_pin_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (isValidPinInput(it)) pin = it },
                    label = { Text(stringResource(R.string.parental_control_new_pin)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = uaTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (isValidPinInput(it)) confirmPin = it },
                    label = { Text(stringResource(R.string.parental_control_confirm_pin)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = uaTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (mismatch) {
                    Text(
                        text = stringResource(R.string.parental_control_pin_mismatch),
                        color = UaTheme.palette.routeRed,
                        style = Caption,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(pin) },
                enabled = bothEntered && !mismatch && ParentalControlPinPolicy.isValidFormat(pin),
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

private fun isValidPinInput(value: String): Boolean =
    value.length <= ParentalControlPinPolicy.PIN_LENGTH && value.all(Char::isDigit)

@Preview(showBackground = true, backgroundColor = 0xFF0B0B12L)
@Composable
private fun SetPinDialogPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        SetPinDialog(onSubmit = {}, onDismiss = {})
    }
}
