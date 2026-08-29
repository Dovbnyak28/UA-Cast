package com.uacastplayer.epg

import com.uacastplayer.core.io.GzipSniffer
import com.uacastplayer.performance.HeapBudget
import java.io.InputStream
import java.io.PushbackInputStream
import java.time.ZoneId
import java.util.zip.GZIPInputStream

/**
 * The complete CPU-side XMLTV pipeline: compression sniffing, retention/budgeted SAX parsing,
 * schedule grouping/sorting and index construction.
 *
 * Download and cache lifecycle stay in `data.epg.EpgRepository`. Keeping this part Android-free
 * makes the production workload directly measurable by the device benchmark instead of recreating
 * an approximation beside it.
 */
object EpgDocumentPipeline {

    fun parse(
        rawInput: InputStream,
        nowMillis: Long,
        zoneId: ZoneId,
        maxHeapBytes: Long,
        checkCancellation: () -> Unit = {},
    ): EpgData {
        val pushback = PushbackInputStream(rawInput, GZIP_MAGIC_SIZE)
        val magic = ByteArray(GZIP_MAGIC_SIZE)
        val read = pushback.read(magic)
        if (read > 0) pushback.unread(magic, 0, read)
        val stream = if (read == GZIP_MAGIC_SIZE && GzipSniffer.isGzip(magic)) {
            GZIPInputStream(pushback)
        } else {
            pushback
        }
        val parsed = stream.use {
            XmlTvParser.parse(
                input = it,
                keepFromMillis = EpgRetentionPolicy.keepFrom(nowMillis, zoneId),
                keepUntilMillis = EpgRetentionPolicy.keepUntil(nowMillis, zoneId),
                maxProgrammes = HeapBudget.maxProgrammes(maxHeapBytes),
                checkCancellation = checkCancellation,
            )
        }
        return EpgDataBuilder.build(parsed, checkCancellation)
    }

    private const val GZIP_MAGIC_SIZE = 2
}
