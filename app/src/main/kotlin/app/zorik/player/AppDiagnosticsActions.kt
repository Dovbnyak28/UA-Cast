package app.zorik.player

import android.app.Application
import android.os.Build
import app.zorik.player.diagnostics.DiagnosticsSnapshot
import app.zorik.player.diagnostics.RemuxEffectivenessCounts
import app.zorik.player.log.CrashLog
import app.zorik.player.log.LogBuffer

internal fun AppViewModel.remuxEffectivenessSnapshot(): RemuxEffectivenessCounts =
    remuxEffectivenessStore.snapshot()

/**
 * Builds the "Send diagnostics" report text (see HelpScreen) from whatever is already known
 * synchronously - current settings, device tier, and [LogBuffer]'s recent entries - without
 * touching anything that isn't already read elsewhere in this ViewModel.
 *
 * On the UsableSpace suppression. Lint suggests `StorageManager.getAllocatableBytes`, which
 * counts space the system *could* free by clearing other apps' caches. That is the right number
 * when deciding whether a write will fit; it is the wrong one here. This line exists to answer
 * "why did my playlist not save", and the honest answer is how much the app can write right
 * now - a figure that includes reclaimable cache would report plenty of room on a phone that
 * has none.
 */
@Suppress("UsableSpace")
internal fun AppViewModel.diagnosticsSnapshot(): DiagnosticsSnapshot {
    val runtime = Runtime.getRuntime()
    return DiagnosticsSnapshot(
        appVersionName = BuildConfig.VERSION_NAME,
        deviceModel = Build.MODEL,
        androidApiLevel = Build.VERSION.SDK_INT,
        deviceTier = settingsState.value.deviceTier,
        bufferSize = settingsState.value.bufferSize,
        iconDisplayMode = settingsState.value.iconDisplayMode,
        // Read from preferences rather than from PlayerViewModel's ui state, and it has to
        // be: this report is most often made from Settings or Help, with no player open at
        // all, and the setting is global and persisted precisely so it outlives one.
        playerResizeMode = preferences.playerResizeMode,
        appThemeId = uiState.value.appTheme.name,
        usedMemoryBytes = runtime.totalMemory() - runtime.freeMemory(),
        totalMemoryBytes = runtime.totalMemory(),
        maxMemoryBytes = runtime.maxMemory(),
        logEntries = LogBuffer.snapshot(),
        remuxEffectiveness = remuxEffectivenessStore.snapshot(),
        lastCrash = CrashLog.read(),
        generatedAtMillis = System.currentTimeMillis(),
        uptimeMillis = android.os.SystemClock.elapsedRealtime() - processStartedAtMillis,
        language = uiState.value.language.code,
        channelCount = playlistState.value.groups.sumOf { it.channels.size },
        groupCount = playlistState.value.groups.size,
        epgChannelCount = epgState.value.data?.index?.channels?.size,
        epgProgrammeCount = epgState.value.data?.programmesByChannelId?.values?.sumOf { it.size },
        epgTruncated = epgState.value.data?.truncation?.any == true,
        epgSource = if (epgState.value.customUrl != null) "custom" else "none",
        epgFailure = epgState.value.lastFailure,
        network = AppNetworkInfo.describe(getApplication<Application>()),
        freeStorageBytes = getApplication<Application>().filesDir.usableSpace,
        casting = castState.value.isSessionConnected,
    )
}
