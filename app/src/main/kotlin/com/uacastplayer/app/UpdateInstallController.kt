package com.uacastplayer.app

import com.uacastplayer.data.update.InstallLaunch
import com.uacastplayer.data.update.InstallOutcomeBus
import com.uacastplayer.data.update.UpdateDownload
import com.uacastplayer.log.AppLog
import com.uacastplayer.update.InstallSessionOutcome
import com.uacastplayer.update.ReleaseApk
import com.uacastplayer.update.UpdateInstallState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "UpdateInstallController"

/**
 * Downloads a release's APK and hands it to the installer, as one action with one state.
 *
 * Separate from [UpdateController] rather than bolted onto it, because the two have different
 * lifetimes and different failure vocabularies: a check is silent, weekly and disposable, while
 * this is something the user pressed a button for and waits on for minutes. Keeping them apart also
 * keeps [UpdateController] free of a downloader and an installer it would never use.
 *
 * The downloader and installer arrive as functions rather than as objects. Both are pure Android
 * underneath - a socket, a `PackageInstaller` session, a `PackageManager` - and passing them in
 * this shape is what lets everything above them be driven by a fake, which is the same reason
 * [com.uacastplayer.update.ReleaseSource] and [com.uacastplayer.update.UpdateCheckStorage] are
 * interfaces.
 */
class UpdateInstallController(
    private val scope: CoroutineScope,
    private val download: suspend (ReleaseApk, (Long, Long) -> Unit) -> UpdateDownload,
    private val install: (File) -> InstallLaunch,
    /** How the system's verdict on a committed session gets back here - see [InstallOutcomeBus].
     * Injected so this can be driven without a `BroadcastReceiver` or a device. */
    outcomes: Flow<InstallSessionOutcome> = InstallOutcomeBus.outcomes,
) {
    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    val state: StateFlow<UpdateInstallState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        scope.launch { outcomes.collect(::onSessionOutcome) }
    }

    /**
     * A committed session finally answered, so stop telling the user to confirm something.
     *
     * Only while [UpdateInstallState.Launching], and that guard is the whole of the correctness
     * here: a stale verdict from a previous session must not overwrite a download the user has
     * since started, and a success needs nothing done because this process is about to be replaced
     * by the version it just installed.
     *
     * [InstallSessionOutcome.AwaitingUser] deliberately changes nothing. The session is alive and
     * the system is showing its dialog, which is exactly what `Launching` already says.
     */
    private fun onSessionOutcome(outcome: InstallSessionOutcome) {
        if (_state.value != UpdateInstallState.Launching) return
        when (outcome) {
            InstallSessionOutcome.Failed -> _state.value = UpdateInstallState.Failed
            InstallSessionOutcome.Installed, InstallSessionOutcome.AwaitingUser -> Unit
        }
    }

    /**
     * A second press while the first download is running is ignored rather than queued or
     * restarted. Restarting would throw away progress the user is watching; queueing would mean two
     * downloads of the same tens of megabytes. This is the same reasoning
     * [UpdateController.checkNow]'s `isChecking` guard is written from.
     */
    fun downloadAndInstall(apk: ReleaseApk) {
        if (job?.isActive == true) return
        _state.value = UpdateInstallState.Downloading(bytesSoFar = 0, totalBytes = apk.sizeBytes)
        job = scope.launch {
            val downloaded = download(apk) { soFar, total ->
                // Only while still downloading: a progress callback that arrives after the state
                // has moved on - the last chunk racing the result - must not drag it back.
                if (_state.value is UpdateInstallState.Downloading) {
                    _state.value = UpdateInstallState.Downloading(soFar, total)
                }
            }
            _state.value = when (downloaded) {
                is UpdateDownload.Ready -> launchInstall(downloaded.file)
                UpdateDownload.Corrupt -> UpdateInstallState.Corrupt
                // Reported as a plain failure rather than a case of its own. An APK bigger than the
                // cap is not this app's, and there is nothing the user could do differently about
                // it - unlike a truncated download, which is worth retrying.
                UpdateDownload.TooLarge -> UpdateInstallState.Failed
                UpdateDownload.Failed -> UpdateInstallState.Failed
            }
        }
    }

    private fun launchInstall(file: File): UpdateInstallState {
        val launched = install(file)
        AppLog.d(TAG) { "Update install launch: $launched" }
        return when (launched) {
            InstallLaunch.Started -> UpdateInstallState.Launching
            InstallLaunch.NeedsPermission -> UpdateInstallState.NeedsPermission
            InstallLaunch.Untrusted -> UpdateInstallState.Untrusted
            InstallLaunch.Failed -> UpdateInstallState.Failed
        }
    }

    /**
     * Puts the row back to its resting state once a failure has been read, or after the user has
     * been sent to grant the install permission - which is the case that matters, since coming back
     * to a row still saying "permission needed" would give them nothing to press.
     *
     * Does not stop a download in flight, and must not: this is called when leaving Settings, and a
     * user who starts a 40MB download and then goes to watch something should come back to a
     * finished one rather than to nothing.
     */
    fun clearOutcome() {
        if (_state.value is UpdateInstallState.Downloading) return
        _state.value = UpdateInstallState.Idle
    }
}
