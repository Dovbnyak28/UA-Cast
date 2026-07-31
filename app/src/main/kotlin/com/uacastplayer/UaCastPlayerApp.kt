package com.uacastplayer

import android.app.Application
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.uacastplayer.data.cache.CachePaths
import java.io.File

private const val COIL_DISK_CACHE_MAX_BYTES = 128L * 1024 * 1024

// Coil's own default is 25% of available app memory, sized for image-browsing apps. Everything this
// app asks Coil to decode is a channel logo rendered at 44-64dp, so that budget is far more than
// the working set needs - and it is competing for the same heap as ExoPlayer's media buffers (up to
// 24MB held at the LARGE buffer setting, see PlayerViewModel.buildLoadControl) on devices the
// DevicePerformanceClassifier already treats as low-end. Bitmaps evicted from here are still on
// disk in IconDiskCache, so the cost of a miss is a decode, not a refetch.
private const val COIL_MEMORY_CACHE_PERCENT = 0.10

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
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(COIL_MEMORY_CACHE_PERCENT)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(filesDir, CachePaths.COIL_CACHE_DIR))
                .maxSizeBytes(COIL_DISK_CACHE_MAX_BYTES)
                .build()
        }
        .build()
}
