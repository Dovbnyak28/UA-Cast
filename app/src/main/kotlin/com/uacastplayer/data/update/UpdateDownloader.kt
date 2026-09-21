package com.uacastplayer.data.update

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.core.net.executeCancellable
import com.uacastplayer.core.security.FileDigest
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.core.security.Hex
import com.uacastplayer.log.AppLog
import com.uacastplayer.update.ReleaseApk
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "UpdateDownloader"

/** What came back from trying to fetch an update's APK. */
sealed interface UpdateDownload {

    /** The file is on disk and matches whatever the release published about it. Still unsigned as
     * far as this app is concerned - see [ApkSignatureGate], which is the check that decides
     * whether it may be installed. */
    data class Ready(val file: File) : UpdateDownload

    /** Bigger than [UpdateDownloader.MAX_APK_BYTES]. Not this app's APK, whatever it is. */
    data object TooLarge : UpdateDownload

    /** The bytes are not the bytes the release says they are. */
    data object Corrupt : UpdateDownload

    /** Offline, a timeout, a 5xx, a full disk. Ordinary, and the user's answer is to try again. */
    data object Failed : UpdateDownload
}

/**
 * Fetches a release's APK into the cache directory.
 *
 * **Cache, not files.** The APK is wanted for the minutes between "download" and "install" and is
 * worthless afterwards, so it belongs where Android is allowed to reclaim it - unlike the playlist
 * and EPG snapshots, which live in `filesDir` precisely because they must survive. Reclaimed early,
 * the worst case is a download that has to be repeated.
 *
 * The stale sweep is here for the lesson the EPG downloader already paid for: every path deletes
 * its own file, and none of that runs when the process dies mid-download. Those leftovers are in
 * the cache rather than `filesDir` this time, so the system would eventually take them - but
 * "eventually" is under memory pressure, and a stranded 40MB is worth sweeping on the way past.
 *
 * The copy loop is written out rather than delegated to
 * [com.uacastplayer.core.io.BoundedByteReader.copyToFile], and it is worth saying why: this one
 * hashes and reports progress as it goes. Reusing that helper would mean a second pass over tens of
 * megabytes to hash the result, and no way to move a progress bar during a download that takes
 * minutes on a phone connection - which is the difference between a UI that is working and one that
 * looks hung.
 */
class UpdateDownloader(
    context: Context,
    private val httpClient: OkHttpClient = AppHttp.client(
        connectTimeoutSeconds = CONNECT_TIMEOUT_SECONDS,
        readTimeoutSeconds = READ_TIMEOUT_SECONDS,
    ),
    /** Injected only so a test can exercise the cap against a body it can afford to generate -
     * proving the real 300MB one would measure the disk rather than the rule. Every caller in the
     * app takes the default. */
    private val maxBytes: Long = MAX_APK_BYTES,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
) {
    private val appContext = context.applicationContext

    private val directory: File get() = File(appContext.cacheDir, DIRECTORY_NAME)

    // Held on the instance only so publish() can read the digest the copy accumulated; reset at the
    // start of every copy so a downloader reused for a second attempt never carries the first one's
    // bytes into it.
    private val hasher: MessageDigest = MessageDigest.getInstance("SHA-256")

    /**
     * @param onProgress bytes so far and the total the release declared, on the caller's thread.
     *   The total is 0 when the release did not declare one, which the UI shows as indeterminate
     *   rather than as a percentage of nothing.
     */
    suspend fun download(apk: ReleaseApk, onProgress: (Long, Long) -> Unit = { _, _ -> }): UpdateDownload =
        withContext(ioDispatcher) {
            deleteStaleDownloads()
            if (!directory.isDirectory && !directory.mkdirs()) {
                AppLog.w(TAG) { "Cannot create the update cache directory" }
                return@withContext UpdateDownload.Failed
            }
            val destination = File(directory, "${Fingerprint.of(apk.downloadUrl)}$APK_SUFFIX")
            // An APK already here with the right hash is the one that was asked for. Skipping the
            // fetch matters most in the case it is most likely to happen: a download that finished
            // and whose install the user dismissed, on a connection slow enough that they would
            // rather not pay for it twice.
            if (apk.sha256 != null && destination.isFile && FileDigest.sha256(destination) == apk.sha256) {
                AppLog.d(TAG) { "Update APK already downloaded and verified; reusing it" }
                return@withContext UpdateDownload.Ready(destination)
            }
            fetch(apk, destination, onProgress)
        }

    @Suppress("ReturnCount")
    private suspend fun fetch(apk: ReleaseApk, destination: File, onProgress: (Long, Long) -> Unit): UpdateDownload {
        val partial = File(destination.parentFile, destination.name + PART_SUFFIX)
        return try {
            httpClient.newCall(Request.Builder().url(apk.downloadUrl).build()).executeCancellable { response ->
                if (!response.isSuccessful) {
                    AppLog.w(TAG) { "Update download refused: HTTP ${response.code}" }
                    UpdateDownload.Failed
                } else {
                    when (val copied = copyHashing(response.body.byteStream(), partial, apk.sizeBytes, onProgress)) {
                        null -> UpdateDownload.TooLarge
                        else -> publish(apk, partial, destination, copied)
                    }
                }
            }
        } catch (e: CancellationException) {
            partial.delete()
            throw e
        } catch (e: IllegalArgumentException) {
            // Release metadata is external input. A malformed asset URL fails while OkHttp builds
            // the request, before a network IOException exists; it is an ordinary failed update,
            // not an uncaught exception in the controller's coroutine.
            AppLog.w(TAG) { "Update download URL rejected: ${e.javaClass.simpleName}" }
            partial.delete()
            UpdateDownload.Failed
        } catch (e: IOException) {
            AppLog.w(TAG) { "Update download failed: ${e.javaClass.simpleName}" }
            partial.delete()
            UpdateDownload.Failed
        } finally {
            // A no-op once the rename below has moved it; the cleanup that matters when it has not.
            partial.delete()
        }
    }

    /**
     * The two things known about the file before anything looks inside it, checked in the order
     * that makes a failure readable.
     *
     * The size is checked even when a hash is available, because a size that disagrees says the
     * download was truncated while a hash mismatch alone does not say why. When no hash was
     * published - assets uploaded before GitHub recorded them have none - the size is the only
     * statement about the content there is, beyond TLS having delivered it intact.
     *
     * A missing hash is deliberately not fatal. TLS to GitHub already covers the transfer, and the
     * check that decides whether any of this gets installed is [ApkSignatureGate]: an APK signed
     * with any other key cannot be an update of this app, hash or no hash.
     */
    private fun publish(apk: ReleaseApk, partial: File, destination: File, copied: Long): UpdateDownload {
        // digest() resets the instance on its way out, so this is read once, before either branch
        // can skip it.
        val actual = Hex.encode(hasher.digest())
        val wrongSize = apk.sizeBytes > 0 && copied != apk.sizeBytes
        val wrongHash = apk.sha256 != null && actual != apk.sha256
        return when {
            wrongSize -> {
                AppLog.w(TAG) { "Update download is $copied bytes, release says ${apk.sizeBytes}" }
                UpdateDownload.Corrupt
            }
            wrongHash -> {
                AppLog.w(TAG) { "Update download does not match the published sha256" }
                UpdateDownload.Corrupt
            }
            else -> {
                if (apk.sha256 == null) {
                    AppLog.d(TAG) { "The release published no sha256; relying on the signature check alone" }
                }
                moveIntoPlace(partial, destination)
            }
        }
    }

    private fun moveIntoPlace(partial: File, destination: File): UpdateDownload {
        destination.delete()
        if (!partial.renameTo(destination)) {
            AppLog.w(TAG) { "Could not publish the downloaded update" }
            return UpdateDownload.Failed
        }
        return UpdateDownload.Ready(destination)
    }

    /** Bytes written, or null once past the cap - at which point the partial file is the caller's
     * to delete, which [fetch]'s `finally` does. */
    private fun copyHashing(
        input: InputStream,
        destination: File,
        declaredTotal: Long,
        onProgress: (Long, Long) -> Unit,
    ): Long? {
        hasher.reset()
        val chunk = ByteArray(CHUNK_SIZE)
        var total = 0L
        destination.outputStream().use { out ->
            while (true) {
                // executeCancellable owns the Call around this loop, so cancellation closes the
                // socket even while this read itself is waiting for the next network packet.
                val read = input.read(chunk)
                if (read == -1) break
                total += read
                if (total > maxBytes) {
                    AppLog.w(TAG) { "Update download exceeds ${maxBytes}B; abandoning it" }
                    return null
                }
                out.write(chunk, 0, read)
                hasher.update(chunk, 0, read)
                onProgress(total, declaredTotal)
            }
        }
        return total
    }

    /**
     * Deletes update files left behind by a previous run, age-gated exactly like
     * [com.uacastplayer.data.epg.EpgDownloader.deleteStaleDownloads] and
     * [com.uacastplayer.data.icons.IconDiskCache]'s sweep, so a file another download is still
     * writing into can never be pulled out from under it.
     */
    @VisibleForTesting
    internal fun deleteStaleDownloads() {
        val cutoff = System.currentTimeMillis() - STALE_AGE_MILLIS
        val stale = directory.listFiles { file ->
            file.isFile && (file.name.endsWith(APK_SUFFIX) || file.name.endsWith(PART_SUFFIX)) &&
                file.lastModified() < cutoff
        } ?: return
        for (file in stale) {
            runCatchingNonFatal { file.delete() }
        }
    }

    companion object {
        /** Generous next to this app's own APK and small enough that a misconfigured endpoint
         * cannot fill the cache partition before anything notices. */
        const val MAX_APK_BYTES = 300L * 1024 * 1024

        private const val CHUNK_SIZE = 64 * 1024
        private const val CONNECT_TIMEOUT_SECONDS = 15L

        /** Per read, not for the whole download: an update is tens of megabytes and a phone
         * connection can take minutes over it legitimately. */
        private const val READ_TIMEOUT_SECONDS = 30L

        private const val DIRECTORY_NAME = "updates"

        @VisibleForTesting
        internal const val APK_SUFFIX = ".apk"

        @VisibleForTesting
        internal const val PART_SUFFIX = ".part"

        /** Long next to the minutes a real download takes, so the sweep can never delete a file
         * another one is still writing into. */
        @VisibleForTesting
        internal const val STALE_AGE_MILLIS = 6L * 60L * 60L * 1000L
    }
}
