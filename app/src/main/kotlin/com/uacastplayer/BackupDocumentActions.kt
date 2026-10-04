package com.uacastplayer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.app.BackupRestoreState
import com.uacastplayer.premium.Feature
import com.uacastplayer.ui.platform.launchBackupPicker
import com.uacastplayer.ui.settings.BackupPasswordDialog
import com.uacastplayer.ui.settings.BackupPreviewDialog
import com.uacastplayer.ui.settings.BackupProgressDialog
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.tv.TvDialogInputRegistration
import com.uacastplayer.ui.tv.tvFocus
import androidx.compose.ui.Modifier
import java.time.LocalDate

internal data class BackupDocumentActions(val export: () -> Unit, val restore: () -> Unit)

/** Composition root wires SAF and UI to the existing ViewModel-owned persistence workflow. */
@Composable
internal fun rememberBackupDocumentActions(viewModel: AppViewModel): BackupDocumentActions {
    val context = LocalContext.current
    val access by viewModel.entitlements.collectAsStateWithLifecycle()
    val state by viewModel.backupRestoreWorkflow.state.collectAsStateWithLifecycle()
    val exportBusy by viewModel.backupController.exportInProgress.collectAsStateWithLifecycle()
    var exportUri by rememberSaveable { mutableStateOf<String?>(null) }
    val allowed = Feature.BACKUP in access.unlocked
    LaunchedEffect(allowed) {
        if (!allowed) {
            exportUri = null
            viewModel.backupRestoreWorkflow.cancel()
        }
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        exportUri = it?.toString()
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && Feature.BACKUP in viewModel.entitlements.value.unlocked) {
            viewModel.backupRestoreWorkflow.open(uri)
        }
    }
    exportUri?.takeIf { allowed }?.let { uri ->
        BackupPasswordDialog(true, false, onConfirm = { password ->
            if (Feature.BACKUP in viewModel.entitlements.value.unlocked) viewModel.exportBackupTo(uri.toUri(), password)
            exportUri = null
        }, onDismiss = { exportUri = null })
    }
    if (allowed) RestoreDialogs(state, viewModel)
    if (exportBusy) BackupProgressDialog(onDismiss = {})
    return BackupDocumentActions(
        export = {
            if (allowed && !exportBusy) create.launchBackupPicker(
                "ua-cast-backup-${LocalDate.now()}.uacast", "export a backup", context,
            )
        },
        restore = {
            if (allowed && state != BackupRestoreState.Applying) open.launchBackupPicker(
                arrayOf("application/octet-stream", "application/json", "*/*"), "import a backup", context,
            )
        },
    )
}

@Composable
private fun RestoreDialogs(state: BackupRestoreState, viewModel: AppViewModel) {
    val workflow = viewModel.backupRestoreWorkflow
    when (state) {
        is BackupRestoreState.PasswordRequired -> BackupPasswordDialog(
            false, state.failed, workflow::unlock, workflow::cancel,
        )
        is BackupRestoreState.Preview -> BackupPreviewDialog(state.summary, onConfirm = {
            if (Feature.BACKUP in viewModel.entitlements.value.unlocked) workflow.confirm() else workflow.cancel()
        }, onDismiss = workflow::cancel)
        BackupRestoreState.Reading -> BackupProgressDialog(workflow::cancel)
        BackupRestoreState.Applying -> BackupProgressDialog(onDismiss = {})
        BackupRestoreState.Failed -> AlertDialog(
            onDismissRequest = workflow::cancel,
            containerColor = UaTheme.palette.surface2,
            text = { TvDialogInputRegistration(); Text(stringResource(R.string.settings_data_import_failure)) },
            confirmButton = {
                TextButton(onClick = workflow::cancel, modifier = Modifier.tvFocus()) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
        BackupRestoreState.Idle -> Unit
    }
}
