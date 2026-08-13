package com.uacastplayer.app

import com.uacastplayer.update.AppVersion
import com.uacastplayer.update.ReleaseLookup
import com.uacastplayer.update.ReleaseSource
import com.uacastplayer.update.UpdateCheckOutcome
import com.uacastplayer.update.UpdateCheckSchedule
import com.uacastplayer.update.UpdateCheckStorage
import com.uacastplayer.update.UpdateUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Finds out whether a newer release exists, on two schedules that behave differently on purpose.
 *
 * The **automatic** check runs when the app is opened and at most once every seven days
 * ([UpdateCheckSchedule]). It is silent: it can only ever add a banner, never an error message. A
 * user who did not ask a question should not be shown the answer's failure.
 *
 * The **manual** check runs when the user taps the button in Settings. It ignores the weekly
 * throttle - a user who taps "check now" means now - and it does report failure, because there is
 * someone waiting for a reply.
 *
 * Neither downloads anything. [UpdateUiState.availableRelease] carries the release page URL, and
 * the UI opens it in a browser.
 */
class UpdateController(
    private val releaseSource: ReleaseSource,
    private val storage: UpdateCheckStorage,
    private val scope: CoroutineScope,
    private val installedVersionName: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /**
     * The weekly check. Does nothing at all when it is not due yet, when a check is already running,
     * or when this build's own version cannot be parsed - there is nothing to compare against then,
     * and offering an "update" on that basis would be guesswork.
     */
    fun checkOnLaunch() {
        if (_state.value.isChecking) return
        if (!UpdateCheckSchedule.isDue(storage.lastUpdateCheckAtMillis, now())) return
        runCheck(manual = false)
    }

    /** The Settings button. Runs regardless of when the last check was. */
    fun checkNow() {
        if (_state.value.isChecking) return
        runCheck(manual = true)
    }

    /**
     * Closes the banner for this version only. The tag is remembered rather than a flag, so the
     * next release announces itself again without anything having to reset the flag.
     */
    fun dismissAvailableUpdate() {
        val tag = _state.value.availableRelease?.tagName ?: return
        storage.dismissedUpdateTag = tag
        _state.value = _state.value.copy(availableRelease = null)
    }

    /** Clears the one-shot result shown next to the Settings button, so it does not sit there
     * forever after the user has read it. */
    fun clearLastOutcome() {
        if (_state.value.lastOutcome == null) return
        _state.value = _state.value.copy(lastOutcome = null)
    }

    private fun runCheck(manual: Boolean) {
        val installed = AppVersion.parse(installedVersionName)
        if (installed == null) {
            // Only reachable if the build's own versionName is malformed, which is a build-config
            // bug rather than a runtime condition - report it to whoever asked, stay silent if
            // nobody did.
            if (manual) _state.value = _state.value.copy(lastOutcome = UpdateCheckOutcome.FAILED)
            return
        }

        _state.value = _state.value.copy(isChecking = true)
        scope.launch {
            val lookup = releaseSource.fetchLatestRelease()
            // Recorded even when the request failed. Otherwise a device that is offline every time
            // the app opens would retry on every single launch, which is the one case where an
            // update check could become a battery and data cost worth noticing.
            storage.lastUpdateCheckAtMillis = now()

            val release = (lookup as? ReleaseLookup.Found)?.release?.takeIf { it.version > installed }
            _state.value = when {
                lookup is ReleaseLookup.Failed -> _state.value.copy(
                    isChecking = false,
                    lastOutcome = if (manual) UpdateCheckOutcome.FAILED else null,
                )

                release != null -> _state.value.copy(
                    isChecking = false,
                    // A manual check overrides an earlier dismissal: asking "is there an update"
                    // and being told nothing because you once closed that banner would be a lie.
                    availableRelease = if (manual || release.tagName != storage.dismissedUpdateTag) {
                        release
                    } else {
                        null
                    },
                    lastOutcome = if (manual) UpdateCheckOutcome.UPDATE_AVAILABLE else null,
                )

                // Everything left is "nothing newer exists": a release that is not ahead of this
                // build, and a repository that has published none at all. The second one is not a
                // failure - see ReleaseLookup.NonePublished - and telling a user to try again later
                // about it would be advice that cannot come true.
                else -> _state.value.copy(
                    isChecking = false,
                    availableRelease = null,
                    lastOutcome = if (manual) UpdateCheckOutcome.UP_TO_DATE else null,
                )
            }
        }
    }
}
