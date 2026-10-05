package com.uacastplayer.data.cache

import java.io.File

/** Mirrors the cache stores' literal path segments under `filesDir` for cache-management UI. */
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
     * per saved source (see `PlaylistSnapshotStore`).
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
     * What `androidx.core.util.AtomicFile` writes into while a write is in progress, and leaves
     * behind when the process dies before finishing one.
     *
     * This file used to look for `.bak` instead, on the belief that a backup is what `AtomicFile`
     * keeps beside each file. It is not - and the test that said so was passing only because it
     * created the `.bak` itself. Measured against the real implementation: a write in progress
     * produces `<name>.new` and nothing else, `finishWrite` renames it onto the base name, and a
     * `.bak` is never created at all (it is a name the class only ever *reads*, for compatibility
     * with the framework `AtomicFile` this app has never used).
     *
     * So the suffix the cache screen was matching could not exist, and the one that does was
     * invisible to it - not counted in the size it reports and not removed by its Clear button.
     * `.bak` is still matched, because a file with that name would be stale cache whatever left it
     * there, and matching it costs nothing.
     */
    const val ATOMIC_WRITE_SUFFIX = ".new"

    private val CACHE_SUFFIXES = listOf(".bin", ".bin$ATOMIC_WRITE_SUFFIX", ".bin.bak")

    /**
     * Every playlist snapshot file in [filesDir], current and legacy, including whatever an
     * unfinished write left beside them.
     *
     * Matched by name rather than rebuilt from the saved source list on purpose: a snapshot whose
     * source has since been removed is exactly the kind of file the cache screen exists to find,
     * and asking the source list would be the one place guaranteed not to mention it. That is not
     * hypothetical for the `.new` files - a source's id is never written again once it is removed,
     * so nothing else will ever rename or truncate one, and this is the only mechanism left that
     * can reach it.
     */
    fun playlistSnapshots(filesDir: File): List<File> =
        filesDir.listFiles { file ->
            file.isFile && file.name.startsWith(PLAYLIST_SNAPSHOT_PREFIX) &&
                CACHE_SUFFIXES.any { file.name.endsWith(it) }
        }?.toList().orEmpty()

    /** What `EpgDownloader` names the file it streams a guide into before parsing it - and leaves
     * behind whenever the process dies first. Mirrored here rather than shared, because this is a
     * name to *find* files by; `EpgDownloader` owns creating and sweeping them. */
    private const val EPG_DOWNLOAD_PREFIX = "epg_download_"
    private const val EPG_DOWNLOAD_SUFFIX = ".tmp"

    /**
     * The EPG snapshot, any unfinished write beside it, and any download stranded part-way.
     *
     * A list rather than one fixed name, because the name is not the only file: an EPG parse is
     * where this app has been killed before (see `EpgDownloader.deleteStaleDownloads` and the
     * OutOfMemoryError entry in CHANGELOG 0.9.0), and being killed mid-write is exactly what leaves
     * a `.new` behind.
     *
     * **The downloads are here because a real device said so.** A Mi A2 running this app's own
     * instrumented suite - which starts the app repeatedly and tears each one down mid-download -
     * finished with **747MB in `filesDir`**, twenty-two stranded `epg_download_*.tmp` files, most of
     * them the full 45.8MB guide. The cache screen alongside them reported the EPG cache as
     * **9.5 MB**, and its Clear button removed none of it.
     *
     * `EpgDownloader.deleteStaleDownloads` does reclaim them, and nothing here changes that: it runs
     * at startup and before every download, and it deliberately spares anything younger than an hour
     * so it can never delete the file a download in flight is still writing. That age gate is
     * correct and it is also the whole gap - within that hour the bytes exist, they are the largest
     * thing this app ever writes, and the one screen that exists to say what is on disk was not
     * counting them. A user out of space now cannot wait an hour for an answer.
     *
     * Clearing one that a download is still writing into fails that download and no more; the
     * downloader already treats a vanished temp file as a failed attempt and retries later. That is
     * the right trade for a button the user pressed on purpose.
     */
    fun epgSnapshots(filesDir: File): List<File> =
        listOf(File(filesDir, EPG_SNAPSHOT), File(filesDir, EPG_SNAPSHOT + ATOMIC_WRITE_SUFFIX)) +
            filesDir.listFiles { file ->
                file.isFile && file.name.startsWith(EPG_DOWNLOAD_PREFIX) && file.name.endsWith(EPG_DOWNLOAD_SUFFIX)
            }.orEmpty()
}
