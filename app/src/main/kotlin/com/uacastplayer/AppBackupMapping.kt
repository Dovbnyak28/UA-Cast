package com.uacastplayer

import com.uacastplayer.backup.BackupData
import com.uacastplayer.backup.BackupFavorite
import com.uacastplayer.backup.BackupPlaylistSource
import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.settings.SettingsUiState

/** Maps the app's current state onto the portable [BackupData] shape; holds no state. */
internal object AppBackupMapping {

    fun backupData(
        playlistSources: List<PlaylistSource>,
        favoriteChannels: List<FavoriteChannel>,
        currentSettings: SettingsUiState,
        preferences: AppPreferences,
    ): BackupData {
        val sources = playlistSources.map {
            BackupPlaylistSource(it.id, it.type.name, it.location, it.displayName, it.addedAtEpochMillis)
        }
        val favoritesBackup = favoriteChannels.map {
            BackupFavorite(
                it.key, it.displayName, it.streamUrl, it.tvgId, it.groupTitle, it.addedAtMillis,
                it.tvgName, it.tvgLogo, it.userAgent, it.referrer,
            )
        }
        val settings = BackupSettings(
            // Only what the user actually chose - the same rule the EPG fields below already
            // follow. These two are exactly the settings DeviceTierDefaults computes per device, so
            // settingsState carries a value for them whether anybody picked one or not, and
            // exporting that shipped one phone's tier default to another as a decision. The
            // receiving end could not undo it either: applyImportedSettings goes through
            // setIconDisplayMode/setListDensity, which write the preference and so make
            // hasChosen... true forever - so a backup taken on a flagship pinned full icon
            // rendering on a low-end phone the tier logic exists to keep light, and a backup taken
            // on a low-end phone pinned placeholders on a flagship.
            //
            // bufferSize joined them: it used to be the same for every device and so was exported
            // unconditionally, and it is now computed from this app's own heap limit (see
            // HeapBudget). Left unconditional it would do the identical damage in the identical
            // way - a backup from a phone with room pinning a 16MB media buffer on the 128MB device
            // whose crash is the reason that default exists.
            iconDisplayMode = currentSettings.iconDisplayMode.name
                .takeIf { preferences.hasChosenIconDisplayMode },
            listDensity = currentSettings.listDensity.name
                .takeIf { preferences.hasChosenListDensity },
            bufferSize = currentSettings.bufferSize.name
                .takeIf { preferences.hasChosenBufferSize },
            epgCustomUrl = if (preferences.hasChosenEpgSource) preferences.customEpgUrl else null,
            epgDisabled = preferences.hasChosenEpgSource && preferences.customEpgUrl.isNullOrBlank(),
        )
        return BackupData(sources, favoritesBackup, settings)
    }
}
