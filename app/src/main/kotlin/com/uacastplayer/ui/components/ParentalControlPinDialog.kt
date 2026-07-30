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
import androidx.compose.runtime.LaunchedEffect
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
 * Prompts for the already-set parental-control PIN (see `app/ParentalControlController.verifyPin`)
 * - used both to unlock a locked channel for playback and to unlock Settings' own parental-control
 * management rows. [isError] shows an inline "incorrect PIN" message and clears the field, but
 * doesn't dismiss - the caller decides success (dismiss + proceed) from [onSubmit]'s result.
 */
@Composable
fun ParentalControlPinDialog(
    title: String,
    isError: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(isError) { if (isError) pin = "" }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UaTheme.palette.surface2,
        titleContentColor = UaTheme.palette.labelPrimary,
        textContentColor = UaTheme.palette.labelSecondary,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= ParentalControlPinPolicy.PIN_LENGTH && it.all(Char::isDigit)) pin = it
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = uaTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isError) {
                    Text(
                        text = stringResource(R.string.parental_control_pin_incorrect),
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
                enabled = ParentalControlPinPolicy.isValidFormat(pin),
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B12L)
@Composable
private fun ParentalControlPinDialogPreview(@PreviewParameter(AppThemePreviewParameter::class) theme: AppTheme) {
    UaCastTheme(theme) {
        ParentalControlPinDialog(
            title = stringResource(R.string.parental_control_enter_pin),
            isError = true,
            onSubmit = {},
            onDismiss = {},
        )
    }
}
