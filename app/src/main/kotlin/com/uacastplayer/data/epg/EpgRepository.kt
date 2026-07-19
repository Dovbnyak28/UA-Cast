package com.uacastplayer.data.epg

import android.content.Context
import com.uacastplayer.core.io.GzipSniffer
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgIndex
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.epg.XmlTvParseResult
import com.uacastplayer.epg.XmlTvParser
import com.uacastplayer.log.AppLog
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class EpgOutcome {
    data class Loaded(val data: EpgData) : EpgOutcome()
    data object SizeLimitExceeded : EpgOutcome()
    data class HttpError(val code: Int) : EpgOutcome()
    data class ReadError(val message: String?) : EpgOutcome()
}

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
                    persist(url, result.documentFile)
                    EpgOutcome.Loaded(data)
                } finally {
                    result.documentFile.delete()
                }
            }
            EpgDownloadResult.SizeLimitExceeded -> EpgOutcome.SizeLimitExceeded
            is EpgDownloadResult.HttpError -> EpgOutcome.HttpError(result.code)
            is EpgDownloadResult.ReadError -> EpgOutcome.ReadError(result.message)
        }
    }

    suspend fun restoreSnapshot(): EpgOutcome? {
        val documentStream = snapshotStore.openDocumentStream() ?: return null
        return try {
            documentStream.use {
                val data = withContext(Dispatchers.Default) { buildEpgData(parseDocument(it)) }
                EpgOutcome.Loaded(data)
            }
        } catch (e: Exception) {
            AppLog.w(TAG) { "Failed to parse cached EPG snapshot: ${e.javaClass.simpleName}" }
            null
        }
    }

    private suspend fun persist(url: String, documentFile: File) {
        try {
            snapshotStore.save(Fingerprint.of(url), System.currentTimeMillis(), documentFile)
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
        return stream.use(XmlTvParser::parse)
    }

    private fun buildEpgData(parsed: XmlTvParseResult): EpgData {
        val programmesByChannel = parsed.programmes
            .groupBy { it.channelId }
            .mapValues { (_, programmes) -> programmes.sortedBy { it.startMillis } }
        return EpgData(EpgIndex(parsed.channels), programmesByChannel)
    }
}
