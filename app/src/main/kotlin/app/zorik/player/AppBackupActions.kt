package app.zorik.player

import app.zorik.player.backup.BackupEpgPolicy
import app.zorik.player.backup.BackupSettings
import app.zorik.player.core.concurrent.runCatchingNonFatal
import app.zorik.player.core.settings.BufferSize
import app.zorik.player.core.settings.IconDisplayMode
import app.zorik.player.core.settings.ListDensity

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
