package com.uacastplayer.data.epg

import android.content.Context
import com.uacastplayer.core.io.GzipSniffer
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.epg.DecodedEpgSnapshot
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgIndex
import com.uacastplayer.epg.EpgRetentionPolicy
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.epg.EpgTruncation
import com.uacastplayer.epg.XmlTvParseResult
import com.uacastplayer.epg.XmlTvParser
import com.uacastplayer.log.AppLog
import java.io.InputStream
import java.io.PushbackInputStream
import java.time.ZoneId
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class EpgOutcome {
    data class Loaded(val data: EpgData) : EpgOutcome()
    data object SizeLimitExceeded : EpgOutcome()
    data class HttpError(val code: Int) : EpgOutcome()
    /** @param cause an exception class name, never a message - see [EpgDownloadResult.ReadError]. */
    data class ReadError(val cause: String?) : EpgOutcome()
}

/**
 * A guide read back from disk, with the day it was written.
 *
 * The timestamp is the point: it was already in every snapshot's header and nothing read it, which
 * is how the cached guide came to have no expiry at all. See [EpgRefreshPolicy].
 */
data class RestoredEpg(val outcome: EpgOutcome, val savedAtMillis: Long)

private const val TAG = "EpgRepository"

class EpgRepository(context: Context) {

    private val appContext = context.applicationContext
    private val httpClient = AppHttp.client(connectTimeoutSeconds = 15, readTimeoutSeconds = 60)
    private val downloader = EpgDownloader(httpClient, appContext.filesDir)
    private val snapshotStore = EpgSnapshotStore(appContext)

    suspend fun loadFromSource(source: EpgSource): EpgOutcome = load(source.url)

    /** For a playlist/Xtream-provided EPG URL (see [com.uacastplayer.epg.EpgSourceAutoDetect]) -
     * same pipeline as [loadFromSource], just without a fixed [EpgSource] to key off of. */
    suspend fun loadFromUrl(url: String): EpgOutcome = load(url)

    private suspend fun load(url: String): EpgOutcome {
        return when (val result = downloader.download(url)) {
            is EpgDownloadResult.Success -> {
                try {
                    val data = withContext(Dispatchers.Default) {
                        result.documentFile.inputStream().use { buildEpgData(parseDocument(it)) }
                    }
                    persist(Fingerprint.of(url), data)
                    EpgOutcome.Loaded(data)
                } finally {
                    result.documentFile.delete()
                }
            }
            EpgDownloadResult.SizeLimitExceeded -> EpgOutcome.SizeLimitExceeded
            is EpgDownloadResult.HttpError -> EpgOutcome.HttpError(result.code)
            is EpgDownloadResult.ReadError -> EpgOutcome.ReadError(result.cause)
        }
    }

    /**
     * Reclaims temp files stranded by a previous process death - see
     * [EpgDownloader.deleteStaleDownloads] for how they accumulate and why nothing else removes them.
     *
     * Called at startup rather than only before a download, because the common case is that no
     * download happens at all: the EPG is restored from its snapshot, so a device carrying half a
     * gigabyte of orphans would keep carrying it until the cache happened to expire.
     */
    suspend fun deleteStaleDownloads() = withContext(Dispatchers.IO) { downloader.deleteStaleDownloads() }

    // A corrupt/truncated cached snapshot (any parse failure, not one specific type) should just
    // fall back to null - the caller re-fetches live - not crash startup over stale disk state.
    @Suppress("TooGenericExceptionCaught")
    suspend fun restoreSnapshot(): RestoredEpg? {
        val snapshot = snapshotStore.open() ?: return null
        return try {
            val data = when (snapshot) {
                is DecodedEpgSnapshot.Parsed -> snapshot.data
                is DecodedEpgSnapshot.Document -> upgradeFromDocument(snapshot)
            }
            RestoredEpg(EpgOutcome.Loaded(data), snapshot.header.savedAtEpochMillis)
        } catch (e: CancellationException) {
            // Not a failure and not this catch's business: the upgrade below suspends for as long
            // as a real feed takes to parse, so the scope ending mid-restore used to arrive here
            // and be reported as an absent snapshot, leaving the caller running inside a cancelled
            // scope. Same rule as DlnaSessionRepository.discoverDevices.
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG) { "Failed to read cached EPG snapshot: ${e.javaClass.simpleName}" }
            null
        }
    }

    /**
     * Reads a snapshot written by an older version, which stored the raw XMLTV document, and
     * rewrites it in the parsed form straight away.
     *
     * This is the slow path the format change exists to eliminate - on a real device against a real
     * feed it took 53 seconds of background CPU, against 6.6s for the parsed format. Paying it once,
     * on the first launch after the upgrade, is still far better than discarding a guide the user
     * already has, which would leave them with no EPG at all until a fresh download finished -
     * offline or not.
     */
    private suspend fun upgradeFromDocument(snapshot: DecodedEpgSnapshot.Document): EpgData {
        val data = snapshot.documentStream.use { stream ->
            withContext(Dispatchers.Default) { buildEpgData(parseDocument(stream)) }
        }
        AppLog.w(TAG) { "Upgraded EPG snapshot from the document format; this happens once" }
        persist(snapshot.header.sourceFingerprint, data)
        return data
    }

    // Best-effort cache write: the EPG data this session already loaded is usable either way,
    // so any persist failure (disk full, I/O error) should just skip the cache, not fail the load.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun persist(sourceFingerprint: String, data: EpgData) {
        try {
            snapshotStore.save(sourceFingerprint, System.currentTimeMillis(), data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG) { "Failed to persist EPG snapshot: ${e.javaClass.simpleName}" }
        }
    }

    /** Sniffs the gzip magic number from the first 2 bytes rather than requiring the whole
     * document in memory - some feeds are gzip-compressed, others already-plain XML (see [EpgSource]). */
    private fun parseDocument(rawStream: InputStream): XmlTvParseResult {
        val pushback = PushbackInputStream(rawStream, 2)
        val magic = ByteArray(2)
        val read = pushback.read(magic)
        if (read > 0) pushback.unread(magic, 0, read)
        val stream = if (read == 2 && GzipSniffer.isGzip(magic)) GZIPInputStream(pushback) else pushback
        // Both ends of the retention window are applied as the document streams past, rather than
        // held and then counted against the cap - see EpgRetentionPolicy for both measurements.
        // Read once, so a parse that runs across midnight cannot use two different days.
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val keepFrom = EpgRetentionPolicy.keepFrom(now, zone)
        val keepUntil = EpgRetentionPolicy.keepUntil(now, zone)
        return stream.use { XmlTvParser.parse(it, keepFrom, keepUntil) }
    }

    private fun buildEpgData(parsed: XmlTvParseResult): EpgData {
        val programmesByChannel = parsed.programmes
            .groupBy { it.channelId }
            .mapValues { (_, programmes) -> programmes.sortedBy { it.startMillis } }
        val truncation = EpgTruncation(
            channelsDropped = parsed.channelLimitExceeded,
            programmesDropped = parsed.programmeLimitExceeded,
        )
        if (truncation.any) {
            AppLog.w(TAG) {
                "EPG feed exceeded the parser's caps and was cut short " +
                    "(channels=${parsed.channelLimitExceeded}, programmes=${parsed.programmeLimitExceeded})"
            }
        }
        return EpgData(EpgIndex(parsed.channels), programmesByChannel, truncation)
    }
}
