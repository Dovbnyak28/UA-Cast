package com.uacastplayer.data.epg

import android.content.Context
import com.uacastplayer.core.io.GzipSniffer
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgIndex
import com.uacastplayer.epg.EpgSnapshot
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.epg.XmlTvParseResult
import com.uacastplayer.epg.XmlTvParser
import com.uacastplayer.log.AppLog
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

sealed class EpgOutcome {
    data class Loaded(val data: EpgData) : EpgOutcome()
    data object SizeLimitExceeded : EpgOutcome()
    data class HttpError(val code: Int) : EpgOutcome()
    data class ReadError(val message: String?) : EpgOutcome()
}

private const val TAG = "EpgRepository"

class EpgRepository(context: Context) {

    private val appContext = context.applicationContext
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val downloader = EpgDownloader(httpClient)
    private val snapshotStore = EpgSnapshotStore(appContext)

    suspend fun loadFromSource(source: EpgSource): EpgOutcome {
        return when (val result = downloader.download(source.url)) {
            is EpgDownloadResult.Success -> {
                val data = withContext(Dispatchers.Default) { buildEpgData(parseDocument(result.documentBytes)) }
                persist(source, result.documentBytes)
                EpgOutcome.Loaded(data)
            }
            EpgDownloadResult.SizeLimitExceeded -> EpgOutcome.SizeLimitExceeded
            is EpgDownloadResult.HttpError -> EpgOutcome.HttpError(result.code)
            is EpgDownloadResult.ReadError -> EpgOutcome.ReadError(result.message)
        }
    }

    suspend fun restoreSnapshot(): EpgOutcome? {
        val snapshot = snapshotStore.load() ?: return null
        return try {
            val data = withContext(Dispatchers.Default) { buildEpgData(parseDocument(snapshot.documentBytes)) }
            EpgOutcome.Loaded(data)
        } catch (e: Exception) {
            AppLog.w(TAG) { "Failed to parse cached EPG snapshot: ${e.javaClass.simpleName}" }
            null
        }
    }

    private suspend fun persist(source: EpgSource, documentBytes: ByteArray) {
        try {
            snapshotStore.save(
                EpgSnapshot(
                    sourceFingerprint = Fingerprint.of(source.url),
                    savedAtEpochMillis = System.currentTimeMillis(),
                    documentBytes = documentBytes,
                )
            )
        } catch (e: Exception) {
            AppLog.w(TAG) { "Failed to persist EPG snapshot: ${e.javaClass.simpleName}" }
        }
    }

    /** Some sources serve gzip, others already-plain XML; sniff the magic bytes rather than trust the URL. */
    private fun parseDocument(documentBytes: ByteArray): XmlTvParseResult {
        val rawStream: InputStream = ByteArrayInputStream(documentBytes)
        val stream = if (GzipSniffer.isGzip(documentBytes)) GZIPInputStream(rawStream) else rawStream
        return stream.use(XmlTvParser::parse)
    }

    private fun buildEpgData(parsed: XmlTvParseResult): EpgData {
        val programmesByChannel = parsed.programmes
            .groupBy { it.channelId }
            .mapValues { (_, programmes) -> programmes.sortedBy { it.startMillis } }
        return EpgData(EpgIndex(parsed.channels), programmesByChannel)
    }
}
