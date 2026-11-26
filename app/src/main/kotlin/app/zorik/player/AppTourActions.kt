package app.zorik.player

import app.zorik.player.guidedtour.GuidedTip

/** Called by the UI after language and terms gates have completed. */
internal fun AppViewModel.offerGuidedTourOnLaunch() = guidedTourController.offerOnLaunch()

internal fun AppViewModel.startGuidedTour() = guidedTourController.startFromSettings()

internal fun AppViewModel.introNext() = guidedTourController.introNext()

internal fun AppViewModel.introBack() = guidedTourController.introBack()

internal fun AppViewModel.goToIntroPage(index: Int) = guidedTourController.goToIntroPage(index)

internal fun AppViewModel.finishIntro() = guidedTourController.finishIntro()

internal fun AppViewModel.showGuidedTip(tip: GuidedTip) = guidedTourController.showTip(tip)

internal fun AppViewModel.dismissGuidedTip() = guidedTourController.dismissTip()

internal fun AppViewModel.interruptGuidedTour() = guidedTourController.interrupt()

/** Intro and every tip marked seen - for test set-up, where an overlay would swallow taps. */
internal fun AppViewModel.dismissAllGuidance() = guidedTourController.dismissAll()
