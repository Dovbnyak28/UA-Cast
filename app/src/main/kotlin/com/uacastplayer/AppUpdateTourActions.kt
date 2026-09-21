package com.uacastplayer

import com.uacastplayer.update.ReleaseApk

/** Manual update actions; launch-time throttling remains owned by AppViewModel initialization. */
internal fun AppViewModel.checkForUpdatesNow() = updateController.checkNow()

internal fun AppViewModel.downloadAndInstallUpdate(apk: ReleaseApk) =
    updateInstallController.downloadAndInstall(apk)

internal fun AppViewModel.clearUpdateInstallOutcome() = updateInstallController.clearOutcome()

internal fun AppViewModel.dismissUpdateBanner() = updateController.dismissAvailableUpdate()

internal fun AppViewModel.clearUpdateCheckOutcome() = updateController.clearLastOutcome()
