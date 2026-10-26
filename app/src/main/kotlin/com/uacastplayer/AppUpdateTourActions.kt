package com.uacastplayer

import com.uacastplayer.update.ReleaseApk

/** Manual update actions and the foreground-triggered automatic check. */
internal fun AppViewModel.checkForUpdatesOnForeground() = updateController.checkOnLaunch()

internal fun AppViewModel.checkForUpdatesNow() = updateController.checkNow()

internal fun AppViewModel.downloadAndInstallUpdate(apk: ReleaseApk) =
    updateInstallController.downloadAndInstall(apk)

internal fun AppViewModel.clearUpdateInstallOutcome() = updateInstallController.clearOutcome()

internal fun AppViewModel.dismissUpdateBanner() = updateController.dismissAvailableUpdate()

internal fun AppViewModel.acknowledgeUpdatePrompt() = updateController.acknowledgeUpdatePrompt()

internal fun AppViewModel.clearUpdateCheckOutcome() = updateController.clearLastOutcome()
