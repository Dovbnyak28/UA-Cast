package com.uacastplayer

import com.uacastplayer.diagnostics.RemuxEffectivenessCounts
import com.uacastplayer.log.CrashLog

internal fun AppViewModel.hasRecordedCrash(): Boolean = CrashLog.read() != null

internal fun AppViewModel.clearRecordedCrash() = CrashLog.clear()

internal fun AppViewModel.remuxEffectivenessSnapshot(): RemuxEffectivenessCounts =
    remuxEffectivenessStore.snapshot()
