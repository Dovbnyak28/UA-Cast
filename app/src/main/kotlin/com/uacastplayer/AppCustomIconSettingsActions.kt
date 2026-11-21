package com.uacastplayer

internal fun AppViewModel.addCustomIconSource(rawUrl: String) = settingsController.customIcons.add(rawUrl)

internal fun AppViewModel.removeCustomIconSource(url: String) = settingsController.customIcons.remove(url)

internal fun AppViewModel.dismissIconSourceError() = settingsController.customIcons.dismissError()
