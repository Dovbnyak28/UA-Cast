package com.uacastplayer

import android.app.Application
import android.os.Build
import android.os.StrictMode
import com.uacastplayer.log.CrashLog
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.svg.SvgDecoder
import okio.Path.Companion.toOkioPath
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

/** `open` for one reason: the debug variant's `DebugUaCastPlayerApp` extends it to install the
 * developer license menu (see `premium/DeveloperMode.kt`). That subclass exists only in
 * `src/debug`, so a release build both keeps this class as its Application and contains no code
 * capable of granting a license. */
open class UaCastPlayerApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // First thing after super: a crash during the rest of this method is exactly the kind that
        // is hardest to diagnose without a record, and installing the handler is one file
        // reference and no work.
        CrashLog.install(
            filesDir = filesDir,
            appVersionName = BuildConfig.VERSION_NAME,
            deviceDescription = "${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})",
        )
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
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(SvgDecoder.Factory()) }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, COIL_MEMORY_CACHE_PERCENT)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(filesDir, CachePaths.COIL_CACHE_DIR).toOkioPath())
                .maxSizeBytes(COIL_DISK_CACHE_MAX_BYTES)
                .build()
        }
        .build()
}
