package com.uacastplayer

import android.app.Application
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import com.uacastplayer.data.cache.CachePaths
import java.io.File

private const val COIL_DISK_CACHE_MAX_BYTES = 128L * 1024 * 1024

class UaCastPlayerApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Debug-only: flags a Closeable (Response body, InputStream, etc.) that got garbage
        // collected without ever being close()'d, logged with the allocation stack trace so the
        // leak site is identifiable - not just penaltyDeath, since a false positive here would crash
        // every debug build outright rather than just being noisy in logcat. Never enabled in
        // release: the stack-trace capture this needs has real overhead, and there's no UI-facing
        // way to surface it in production anyway - a release build that leaks a Closeable still
        // leaks memory the same as debug, just silently, which is what release monitoring
        // (or a future crash-reporting integration) would need to catch instead.
        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build(),
            )
        }
    }

    // Without this, IconDiskCache/ImageFormatDetector happily validate and store SVG icon bytes,
    // but AsyncImage's default ImageLoader has no decoder for them - it just silently renders
    // nothing instead of erroring, which is why some channels' logos looked "missing" when the
    // provider's icon happened to be an SVG.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
        .diskCache {
            DiskCache.Builder()
                .directory(File(filesDir, CachePaths.COIL_CACHE_DIR))
                .maxSizeBytes(COIL_DISK_CACHE_MAX_BYTES)
                .build()
        }
        .build()
}
