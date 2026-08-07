package com.uacastplayer.update

/**
 * The update check's state together with the three things the UI can do about it, bundled the way
 * [com.uacastplayer.ui.home.HomeSourceState] is - the banner in the top bar and the section in
 * Settings are two views of one thing, and threading four separate parameters through
 * `RootScaffold` for it would only make an already long signature longer.
 */
data class UpdateSectionState(
    val state: UpdateUiState,
    /** The Settings button: check regardless of when the last check ran. */
    val onCheckNow: () -> Unit,
    /** Hands the release page to a browser. The app never downloads or installs anything itself. */
    val onOpenRelease: (String) -> Unit,
    /** Closes the banner for this release only. */
    val onDismissBanner: () -> Unit,
    /** Clears the one-shot result line under the Settings button once it has been read. */
    val onOutcomeShown: () -> Unit,
)
