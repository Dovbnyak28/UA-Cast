package app.zorik.player.data.cast

import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val MAX_HEADER_BYTES = 16 * 1024
private const val MAX_REQUEST_LINE_BYTES = 4 * 1024
private const val MAX_HEADER_LINE_BYTES = 8 * 1024
private const val MAX_HEADER_COUNT = 64
private const val REQUEST_LINE_PART_COUNT = 3
private const val HTTP_VERSION_1_0 = "HTTP/1.0"
private const val HTTP_VERSION_1_1 = "HTTP/1.1"
private const val ASCII_SPACE = 32
private const val ASCII_VISIBLE_FIRST = 33
private const val ASCII_VISIBLE_LAST = 126
private const val ASCII_DELETE = 127
private val HTTP_HEADER_NAME_SEPARATORS = "()<>@,;:\\\"/[]?={}".toSet()

/** Bounded, stateless HTTP/1.x request-head reader used by [ProxyHttpServer]. */
internal object ProxyHttpRequestReader {
    fun readRequest(input: InputStream): ParsedRequest? {
        val lines = readRequestLines(input) ?: return null
        return parseRequestLines(lines)
    }

    private fun readRequestLines(input: InputStream): List<String>? {
        val lines = mutableListOf<String>()
        val budget = RequestReadBudget()
        val lineBuffer = ByteArrayOutputStream()
        var valid = true
        var complete = false
        while (valid && !complete) {
            val lineLimit = if (lines.isEmpty()) MAX_REQUEST_LINE_BYTES else MAX_HEADER_LINE_BYTES
            val line = readHttpLine(input, lineBuffer, budget, lineLimit)
            when {
                line == null -> valid = false
                line.isEmpty() -> complete = true
                lines.size > MAX_HEADER_COUNT -> valid = false
                else -> lines += line
            }
        }
        return lines.takeIf { valid && complete && it.isNotEmpty() }
    }

    private fun parseRequestLines(lines: List<String>): ParsedRequest? {
        val requestLine = lines[0].trim().split(Regex("\\s+"), limit = REQUEST_LINE_PART_COUNT)
        if (requestLine.size != REQUEST_LINE_PART_COUNT || !isSupportedHttpVersion(requestLine[2])) return null
        return parseHeaders(lines.drop(1))?.let { headers ->
            ParsedRequest(requestLine[0].uppercase(), requestLine[1], headers)
        }
    }

    private fun isSupportedHttpVersion(version: String): Boolean =
        version == HTTP_VERSION_1_0 || version == HTTP_VERSION_1_1

    private fun parseHeaders(lines: List<String>): Map<String, String>? = buildMap {
        for (line in lines) {
            val separator = line.indexOf(':')
            if (separator <= 0) return null
            val name = line.substring(0, separator)
            val value = line.substring(separator + 1).trim()
            if (!isValidHeaderName(name) || !isValidHeaderValue(value)) return null
            put(name.lowercase(), value)
        }
    }

    private fun isValidHeaderName(name: String): Boolean = name.isNotEmpty() && name.all { character ->
        character.code in ASCII_VISIBLE_FIRST..ASCII_VISIBLE_LAST && character !in HTTP_HEADER_NAME_SEPARATORS
    }

    private fun isValidHeaderValue(value: String): Boolean = value.all { character ->
        character == '\t' || character.code >= ASCII_SPACE && character.code != ASCII_DELETE
    }

    private fun readHttpLine(
        input: InputStream,
        lineBuffer: ByteArrayOutputStream,
        budget: RequestReadBudget,
        lineLimit: Int,
    ): String? {
        lineBuffer.reset()
        while (true) {
            when (consumeHttpByte(input.read(), lineBuffer, budget, lineLimit)) {
                LineReadStatus.CONTINUE -> Unit
                LineReadStatus.COMPLETE -> return lineBuffer.toString(Charsets.ISO_8859_1.name())
                LineReadStatus.INVALID -> return null
            }
        }
    }

    private fun consumeHttpByte(
        byte: Int,
        lineBuffer: ByteArrayOutputStream,
        budget: RequestReadBudget,
        lineLimit: Int,
    ): LineReadStatus {
        if (byte == -1) return LineReadStatus.INVALID
        budget.totalBytes++
        return when {
            budget.totalBytes > MAX_HEADER_BYTES -> LineReadStatus.INVALID
            byte == '\n'.code -> LineReadStatus.COMPLETE
            byte == '\r'.code -> LineReadStatus.CONTINUE
            else -> {
                lineBuffer.write(byte)
                if (lineBuffer.size() > lineLimit) LineReadStatus.INVALID else LineReadStatus.CONTINUE
            }
        }
    }

    private data class RequestReadBudget(var totalBytes: Int = 0)
    private enum class LineReadStatus { CONTINUE, COMPLETE, INVALID }
}
