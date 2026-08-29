package com.uacastplayer

/** Called by the UI after language and terms gates have completed. */
internal fun AppViewModel.offerGuidedTourOnLaunch() = guidedTourController.offerOnLaunch()

internal fun AppViewModel.startGuidedTour() = guidedTourController.startFromSettings()

internal fun AppViewModel.guidedTourNext() = guidedTourController.next()

internal fun AppViewModel.guidedTourBack() = guidedTourController.back()

internal fun AppViewModel.guidedTourSkip() = guidedTourController.skip()

internal fun AppViewModel.guidedTourComplete() = guidedTourController.complete()
