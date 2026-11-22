package app.zorik.player

import app.zorik.player.data.cache.CachePaths
import app.zorik.player.data.cache.CacheSizeUtils
import app.zorik.player.settings.CacheKind
import app.zorik.player.settings.CacheSizes
import java.io.File

/** Which files under `filesDir` make up each user-clearable cache; holds no state. */
internal object AppCacheFiles {

    /** A list, because the playlist cache is not one file: there is a snapshot per saved source
     * (see CachePaths.playlistSnapshots). Reading only the pre-multi-playlist name meant the clear
     * button deleted nothing on every install created since. */
    fun filesFor(kind: CacheKind, filesDir: File): List<File> = when (kind) {
        CacheKind.PLAYLIST -> CachePaths.playlistSnapshots(filesDir)
        CacheKind.EPG -> CachePaths.epgSnapshots(filesDir)
        CacheKind.ICONS -> listOf(File(filesDir, CachePaths.ICON_CACHE_DIR))
        CacheKind.COIL -> listOf(File(filesDir, CachePaths.COIL_CACHE_DIR))
    }

    /** Walks the disk - call off the main thread. */
    fun measure(filesDir: File): CacheSizes = CacheSizes(
        playlistBytes = CacheSizeUtils.sizeOf(CachePaths.playlistSnapshots(filesDir)),
        epgBytes = CacheSizeUtils.sizeOf(CachePaths.epgSnapshots(filesDir)),
        iconCacheBytes = CacheSizeUtils.sizeOf(File(filesDir, CachePaths.ICON_CACHE_DIR)),
        coilCacheBytes = CacheSizeUtils.sizeOf(File(filesDir, CachePaths.COIL_CACHE_DIR)),
    )
}
