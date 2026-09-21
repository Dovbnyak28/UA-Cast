package com.uacastplayer.update

/**
 * The update check's state together with the three things the UI can do about it, bundled the way
 * [com.uacastplayer.ui.home.HomeSourceState] is - the banner in the top bar and the section in
 * Settings are two views of one thing, and threading four separate parameters through
 * `RootScaffold` for it would only make an already long signature longer.
 */
data class UpdateSectionState(
    val state: UpdateUiState,
    /** How far the download-and-install action has got, if it was started. */
    val installState: UpdateInstallState,
    /** The Settings button: check regardless of when the last check ran. */
    val onCheckNow: () -> Unit,
    /**
     * Hands the release page to a browser. Still the whole offer whenever a release has no APK this
     * build will install by itself - see [GitHubRelease.apk] - and still offered beside the direct
     * install when it does, because a user who would rather read the notes first should be able to.
     */
    val onOpenRelease: (String) -> Unit,
    /** Fetches [GitHubRelease.apk], checks it, and hands it to the system installer. Only ever
     * shown when there is an APK to fetch. */
    val onDownloadAndInstall: (ReleaseApk) -> Unit,
    /**
     * Opens the system screen where this app can be allowed to install packages. Offered only when
     * an install attempt actually needed it, rather than up front: asking for the right to install
     * things before there is anything to install is how a permission gets refused.
     */
    val onGrantInstallPermission: () -> Unit,
    /** Closes the banner for this release only. */
    val onDismissBanner: () -> Unit,
    /** Clears the one-shot result line under the Settings button once it has been read. */
    val onOutcomeShown: () -> Unit,
)
