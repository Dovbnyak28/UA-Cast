package com.uacastplayer.ui.diagnostics

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.BuildConfig
import com.uacastplayer.R
import com.uacastplayer.diagnostics.DiagnosticsEmail
import com.uacastplayer.log.AppLog
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.UaTheme

private const val TAG = "DiagnosticsSend"

/**
 * The one "send diagnostics" flow, shared by Help and Settings.
 *
 * It exists as its own file because it is now offered from two screens, and a second copy of a
 * dialog whose entire job is to show the user what is about to leave their phone is exactly the
 * kind of duplicate that drifts - one of them growing a field the other does not preview.
 */

/**
 * The email a report is handed to, pre-addressed and pre-filled.
 *
 * `ACTION_SENDTO` with a `mailto:` URI rather than `ACTION_SEND`, and the difference is what the
 * user is offered: `ACTION_SEND` opens a chooser containing every messenger, note-taking app and
 * cloud drive installed, none of which can deliver this anywhere useful, and each of which is one
 * mis-tap away from posting a device log into a group chat. `mailto:` resolves to mail apps only.
 *
 * The message is composed, not sent. It lands in the user's own mail app with the address, subject
 * and body filled in, and stays there until they press send.
 */
fun diagnosticsEmailIntent(report: String): Intent =
    Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:".toUri()
        putExtra(Intent.EXTRA_EMAIL, arrayOf(DiagnosticsEmail.RECIPIENT))
        putExtra(Intent.EXTRA_SUBJECT, DiagnosticsEmail.subject(BuildConfig.VERSION_NAME, android.os.Build.MODEL))
        putExtra(Intent.EXTRA_TEXT, report)
    }

/**
 * Opens [diagnosticsEmailIntent], or falls back to a plain share sheet.
 *
 * A phone with no mail app at all is unusual but real - a stripped ROM, a work profile, a TV box -
 * and `startActivity` on an unresolvable `ACTION_SENDTO` throws. Falling back to the chooser means
 * such a user can still get the report out through whatever they do have, and the failure that
 * remains after that is one nothing can fix: no app on the device can send anything.
 */
fun sendDiagnostics(context: Context, report: String, chooserTitle: String) {
    try {
        context.startActivity(diagnosticsEmailIntent(report))
    } catch (_: ActivityNotFoundException) {
        AppLog.w(TAG) { "No mail app to send the report with - falling back to a share sheet" }
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(DiagnosticsEmail.RECIPIENT))
            putExtra(Intent.EXTRA_SUBJECT, DiagnosticsEmail.subject(BuildConfig.VERSION_NAME, android.os.Build.MODEL))
            putExtra(Intent.EXTRA_TEXT, report)
        }
        runCatching { context.startActivity(Intent.createChooser(fallback, chooserTitle)) }
            .onFailure { AppLog.w(TAG) { "Nothing on this device can send the report" } }
    }
}

/** Lets the user see exactly what "Send diagnostics" is about to send before any mail app opens -
 * nothing is ever sent automatically, and this is the screen that makes that true rather than
 * merely stated. */
@Composable
fun DiagnosticsPreviewDialog(report: String, onCancel: () -> Unit, onSend: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.diagnostics_preview_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.diagnostics_preview_body_hint),
                    style = BodyText,
                    color = UaTheme.palette.labelSecondary,
                )
                Box(
                    modifier = Modifier
                        .padding(top = GapM)
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(text = report, style = BodyText, color = UaTheme.palette.labelPrimary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSend) { Text(stringResource(R.string.diagnostics_preview_send)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.diagnostics_preview_cancel)) }
        },
    )
}
