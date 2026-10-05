package com.uacastplayer.epg

import com.uacastplayer.core.io.GzipSniffer
import com.uacastplayer.performance.HeapBudget
import java.io.IOException
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
        val read = readPrefix(pushback, magic)
        if (read > 0) pushback.unread(magic, 0, read)
        val decodedStream = if (read == GZIP_MAGIC_SIZE && GzipSniffer.isGzip(magic)) {
            GZIPInputStream(pushback)
        } else {
            pushback
        }
        val decompressedStream = LimitedInputStream(decodedStream, MAX_DECOMPRESSED_BYTES)
        val guardedStream = BoundedXmlMarkupInputStream.create(
            input = decompressedStream,
            maxMarkupBytes = MAX_XML_MARKUP_BYTES,
            checkCancellation = checkCancellation,
        )
        val parsed = guardedStream.use {
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
    /** Stops a single XML tag/attribute from consuming a small device's heap before SAX callbacks. */
    const val MAX_XML_MARKUP_BYTES = 16 * 1024
    /** Limits decompressed XML as well as the downloader's compressed-body limit. */
    const val MAX_DECOMPRESSED_BYTES = 64L * 1024 * 1024

    /** InputStream is allowed to return fewer bytes than requested, even before EOF. A single
     * read therefore cannot reliably distinguish a two-byte gzip signature from a one-byte
     * partial read; keep reading until the signature is complete or the document really ends. */
    private fun readPrefix(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        var finished = false
        while (offset < buffer.size && !finished) {
            val count = input.read(buffer, offset, buffer.size - offset)
            when {
                count < 0 -> finished = true
                count == 0 -> {
                    // Defensive progress for unusual streams whose bulk read returns zero.
                    val value = input.read()
                    if (value < 0) {
                        finished = true
                    } else {
                        buffer[offset++] = value.toByte()
                    }
                }
                else -> offset += count
            }
        }
        return offset
    }

    private class LimitedInputStream(
        private val delegate: InputStream,
        private val maxBytes: Long,
    ) : InputStream() {
        private var count = 0L

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) checkLimit(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = delegate.read(buffer, offset, length)
            if (read > 0) checkLimit(read.toLong())
            return read
        }

        override fun close() = delegate.close()

        private fun checkLimit(read: Long) {
            count += read
            if (count > maxBytes) throw IOException("EPG decompressed size limit exceeded")
        }
    }
}
