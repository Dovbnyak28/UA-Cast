package com.uacastplayer.data.cache

import java.io.File

/** Mirrors the literal path segments each cache store already uses under `filesDir`, kept in one place for cache-management UI. */
object CachePaths {

    /**
     * The single fixed-name snapshot from before multi-playlist support, kept only so the cache
     * screen can still account for an install that has one - see
     * `PlaylistRepository.migrateLegacySnapshotIfNeeded`, which retires it on the first launch
     * after the upgrade. Everything written since lives under [PLAYLIST_SNAPSHOT_PREFIX].
     */
    const val LEGACY_PLAYLIST_SNAPSHOT = "playlist_snapshot.bin"

    /**
     * What every playlist snapshot written today is named: `playlist_snapshot_<sourceId>.bin`, one
     * per saved source (see `PlaylistSnapshotStore`), plus the `.bak` copies `AtomicFile` keeps
     * beside them.
     *
     * The cache screen used to point at [LEGACY_PLAYLIST_SNAPSHOT] alone, so on every install
     * created since multi-playlist support it reported the playlist cache as 0 B and its Clear
     * button deleted a file that was not there - while several megabytes of real snapshots sat
     * next to it, unreachable from the only UI that offers to remove them.
     */
    const val PLAYLIST_SNAPSHOT_PREFIX = "playlist_snapshot"

    const val EPG_SNAPSHOT = "epg_snapshot.bin"
    const val ICON_CACHE_DIR = "icon_cache"
    const val COIL_CACHE_DIR = "coil_cache"

    /**
     * Every playlist snapshot file in [filesDir], current and legacy, including `AtomicFile`'s
     * backups.
     *
     * Matched by name rather than rebuilt from the saved source list on purpose: a snapshot whose
     * source has since been removed is exactly the kind of file the cache screen exists to find,
     * and asking the source list would be the one place guaranteed not to mention it.
     */
    fun playlistSnapshots(filesDir: File): List<File> =
        filesDir.listFiles { file ->
            file.isFile && file.name.startsWith(PLAYLIST_SNAPSHOT_PREFIX) &&
                (file.name.endsWith(".bin") || file.name.endsWith(".bin.bak"))
        }?.toList().orEmpty()
}
