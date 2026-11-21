package com.uacastplayer

import com.uacastplayer.backup.BackupEpgPolicy
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.core.settings.BufferSize
import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.core.settings.ListDensity

/** One-shot backup notices are owned by BackupController; these keep the UI-facing API stable
 * without adding forwarding functions back to the already broad AppViewModel class. */
fun AppViewModel.dismissBackupImportSummary() = backupController.dismissImportSummary()

fun AppViewModel.dismissBackupExportResult() = backupController.dismissExportResult()

/** Reuses the same setters a manual change in Settings would call, so importing settings can't
 * drift from what those paths already do (persisting to [preferences], updating state, and for
 * EPG, actually reloading). */
internal fun AppViewModel.applyImportedSettings(settings: BackupSettings) {
    settings.iconDisplayMode
        ?.let { name -> runCatchingNonFatal { IconDisplayMode.valueOf(name) }.getOrNull() }
        ?.let(::setIconDisplayMode)
    settings.listDensity
        ?.let { name -> runCatchingNonFatal { ListDensity.valueOf(name) }.getOrNull() }
        ?.let(::setListDensity)
    settings.bufferSize
        ?.let { name -> runCatchingNonFatal { BufferSize.valueOf(name) }.getOrNull() }
        ?.let(::setBufferSize)
    // Legacy built-in EPG IDs never reactivate a publisher-provided feed.
    BackupEpgPolicy.urlToApply(settings)?.let(epgController::setUserEpgUrl)
}
