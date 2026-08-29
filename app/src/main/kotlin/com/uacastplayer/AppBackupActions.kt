package com.uacastplayer

/** One-shot backup notices are owned by BackupController; these keep the UI-facing API stable
 * without adding forwarding functions back to the already broad AppViewModel class. */
fun AppViewModel.dismissBackupImportSummary() = backupController.dismissImportSummary()

fun AppViewModel.dismissBackupExportResult() = backupController.dismissExportResult()
