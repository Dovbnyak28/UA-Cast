package com.uacastplayer.backup

import com.uacastplayer.epg.EpgUrlPolicy

/** Null keeps the target's selection; empty disables it; a normalized URL selects a custom guide. */
internal object BackupEpgPolicy {
    fun urlToApply(settings: BackupSettings): String? =
        if (settings.epgDisabled) "" else settings.epgCustomUrl?.let(EpgUrlPolicy::normalize)

    fun changedSetting(imported: BackupSettings, current: BackupSettings): String? {
        val incoming = urlToApply(imported) ?: return null
        return if (incoming == urlToApply(current)) null
        else if (imported.epgDisabled) "epgDisabled" else "epgCustomUrl"
    }
}
