package com.uacastplayer.update

/**
 * What the last update check produced, for the row in Settings that reports it.
 *
 * [FAILED] deliberately does not distinguish "no network" from "GitHub answered 500": the user's
 * next action is the same in both cases - tap again later - and a check the app started on its own
 * must never surface an error at all (see [UpdateUiState.lastOutcome]).
 */
enum class UpdateCheckOutcome { UP_TO_DATE, UPDATE_AVAILABLE, FAILED }

/**
 * @param isChecking a check is in flight; the Settings button shows a spinner and cannot be tapped
 *   again, which is what keeps six taps from becoming six requests.
 * @param availableRelease the newest release, only when it is actually newer than the installed
 *   build. Null both before any check and when the app is up to date - the banner has no other
 *   condition to evaluate.
 * @param lastOutcome result of the most recent **manual** check only. An automatic weekly check
 *   that fails leaves this null, because a user who never asked anything should not be shown an
 *   error about it.
 */
data class UpdateUiState(
    val isChecking: Boolean = false,
    val availableRelease: GitHubRelease? = null,
    val lastOutcome: UpdateCheckOutcome? = null,
)
