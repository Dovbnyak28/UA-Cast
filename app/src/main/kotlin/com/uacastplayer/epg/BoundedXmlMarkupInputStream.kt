package com.uacastplayer.epg

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream

/**
 * Rejects oversized XML markup before SAX can materialize an untrusted attribute value.
 *
 * SAX attribute callbacks happen after the parser has built the attribute String, which is too
 * late to protect small heaps from one enormous value. This byte-level guard counts start/end tags,
 * processing instructions and declarations while leaving text, comments and CDATA streaming.
 * XML delimiters are ASCII in UTF-8/ASCII-compatible encodings; UTF-16/32 byte order is sniffed
 * using the XML encoding signatures so those documents receive the same protection.
 */
internal class BoundedXmlMarkupInputStream private constructor(
    input: InputStream,
    private val maxMarkupBytes: Int,
    private val encoding: Encoding,
    private val checkCancellation: () -> Unit,
) : FilterInputStream(input) {

    private enum class Mode {
        TEXT,
        AFTER_LESS_THAN,
        START_TAG,
        END_TAG,
        PROCESSING_INSTRUCTION,
        DECLARATION_PREFIX,
        DECLARATION,
        COMMENT,
        CDATA,
    }

    private var mode = Mode.TEXT
    private var markupBytes = 0
    private var quote = NO_QUOTE
    private var declarationBracketDepth = 0
    private var declarationQuote = NO_QUOTE
    private var declarationProbe = mutableListOf<Int>()
    private var commentTrailingDashes = 0
    private var cdataTrailingBrackets = 0
    private var processingInstructionQuestionSeen = false

    private val codeUnitBytes = ByteArray(encoding.bytesPerCodeUnit)
    private var codeUnitBytesRead = 0
    private var bytesUntilCancellationCheck = CANCELLATION_CHECK_INTERVAL_BYTES

    override fun read(): Int {
        val value = `in`.read()
        if (value >= 0) acceptByte(value)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = `in`.read(buffer, offset, length)
        if (count > 0) {
            for (index in offset until offset + count) acceptByte(buffer[index].toInt() and BYTE_MASK)
        }
        return count
    }

    override fun skip(count: Long): Long {
        if (count <= 0L) return 0L
        val buffer = ByteArray(minOf(count, SKIP_BUFFER_SIZE.toLong()).toInt())
        var skipped = 0L
        var finished = false
        while (skipped < count && !finished) {
            val requested = minOf(buffer.size.toLong(), count - skipped).toInt()
            val read = read(buffer, 0, requested)
            when {
                read < 0 -> finished = true
                read == 0 -> {
                    if (read() < 0) {
                        finished = true
                    } else {
                        skipped++
                    }
                }
                else -> skipped += read
            }
        }
        return skipped
    }

    private fun acceptByte(value: Int) {
        bytesUntilCancellationCheck--
        if (bytesUntilCancellationCheck == 0) {
            checkCancellation()
            bytesUntilCancellationCheck = CANCELLATION_CHECK_INTERVAL_BYTES
        }
        codeUnitBytes[codeUnitBytesRead++] = value.toByte()
        if (codeUnitBytesRead == encoding.bytesPerCodeUnit) {
            codeUnitBytesRead = 0
            processCodePoint(decodeCodeUnit())
        }
    }

    private fun decodeCodeUnit(): Int = when (encoding.bytesPerCodeUnit) {
        1 -> codeUnitBytes[0].toInt() and BYTE_MASK
        2 -> if (encoding.littleEndian) {
            (codeUnitBytes[0].toInt() and BYTE_MASK) or
                ((codeUnitBytes[1].toInt() and BYTE_MASK) shl Byte.SIZE_BITS)
        } else {
            ((codeUnitBytes[0].toInt() and BYTE_MASK) shl Byte.SIZE_BITS) or
                (codeUnitBytes[1].toInt() and BYTE_MASK)
        }
        else -> {
            var value = 0
            val indices = if (encoding.littleEndian) codeUnitBytes.indices.reversed() else codeUnitBytes.indices
            for (index in indices) {
                value = (value shl Byte.SIZE_BITS) or (codeUnitBytes[index].toInt() and BYTE_MASK)
            }
            value
        }
    }

    private fun processCodePoint(codePoint: Int) {
        when (mode) {
            Mode.TEXT -> if (codePoint == LESS_THAN) {
                mode = Mode.AFTER_LESS_THAN
                markupBytes = encoding.bytesPerCodeUnit
            }
            Mode.AFTER_LESS_THAN -> processAfterLessThan(codePoint)
            Mode.START_TAG -> processStartTag(codePoint)
            Mode.END_TAG -> processEndTag(codePoint)
            Mode.PROCESSING_INSTRUCTION -> processProcessingInstruction(codePoint)
            Mode.DECLARATION_PREFIX -> processDeclarationPrefix(codePoint)
            Mode.DECLARATION -> processDeclaration(codePoint)
            Mode.COMMENT -> processComment(codePoint)
            Mode.CDATA -> processCdata(codePoint)
        }
    }

    private fun processAfterLessThan(codePoint: Int) {
        when (codePoint) {
            SLASH -> {
                addMarkupBytes(encoding.bytesPerCodeUnit)
                mode = Mode.END_TAG
            }
            QUESTION -> {
                addMarkupBytes(encoding.bytesPerCodeUnit)
                mode = Mode.PROCESSING_INSTRUCTION
            }
            EXCLAMATION -> {
                addMarkupBytes(encoding.bytesPerCodeUnit)
                mode = Mode.DECLARATION_PREFIX
                declarationProbe = mutableListOf()
            }
            else -> if (isXmlNameStart(codePoint)) {
                mode = Mode.START_TAG
                quote = NO_QUOTE
                addMarkupBytes(encoding.bytesPerCodeUnit)
            } else {
                mode = Mode.TEXT
            }
        }
    }

    private fun processStartTag(codePoint: Int) {
        addMarkupBytes(encoding.bytesPerCodeUnit)
        if (quote != NO_QUOTE) {
            if (codePoint == quote) quote = NO_QUOTE
            return
        }

        when (codePoint) {
            SINGLE_QUOTE, DOUBLE_QUOTE -> quote = codePoint
            GREATER_THAN -> finishMarkup()
        }
    }

    private fun processEndTag(codePoint: Int) {
        addMarkupBytes(encoding.bytesPerCodeUnit)
        if (codePoint == GREATER_THAN) finishMarkup()
    }

    private fun processProcessingInstruction(codePoint: Int) {
        addMarkupBytes(encoding.bytesPerCodeUnit)
        if (codePoint == GREATER_THAN && processingInstructionQuestionSeen) {
            finishMarkup()
        } else {
            processingInstructionQuestionSeen = codePoint == QUESTION
        }
    }

    private fun processDeclarationPrefix(codePoint: Int) {
        addMarkupBytes(encoding.bytesPerCodeUnit)
        declarationProbe += codePoint
        val probe = declarationProbe.toIntArray()
        when {
            probe.contentEquals(COMMENT_OPEN) -> {
                mode = Mode.COMMENT
                commentTrailingDashes = 0
            }
            probe.contentEquals(CDATA_OPEN) -> {
                mode = Mode.CDATA
                cdataTrailingBrackets = 0
            }
            isPrefixOf(probe, COMMENT_OPEN) || isPrefixOf(probe, CDATA_OPEN) -> Unit
            else -> {
                mode = Mode.DECLARATION
                declarationQuote = NO_QUOTE
                declarationBracketDepth = 0
                for (part in probe) processDeclarationSyntax(part)
                declarationProbe.clear()
            }
        }
    }

    private fun processDeclaration(codePoint: Int) {
        addMarkupBytes(encoding.bytesPerCodeUnit)
        processDeclarationSyntax(codePoint)
    }

    private fun processDeclarationSyntax(codePoint: Int) {
        if (declarationQuote != NO_QUOTE) {
            if (codePoint == declarationQuote) declarationQuote = NO_QUOTE
            return
        }

        when (codePoint) {
            SINGLE_QUOTE, DOUBLE_QUOTE -> declarationQuote = codePoint
            LEFT_BRACKET -> declarationBracketDepth++
            RIGHT_BRACKET -> if (declarationBracketDepth > 0) declarationBracketDepth--
            GREATER_THAN -> if (declarationBracketDepth == 0) finishMarkup()
        }
    }

    private fun processComment(codePoint: Int) {
        commentTrailingDashes = when {
            codePoint == GREATER_THAN && commentTrailingDashes >= COMMENT_END_DASHES -> {
                finishMarkup()
                0
            }
            codePoint == HYPHEN -> (commentTrailingDashes + 1).coerceAtMost(COMMENT_END_DASHES)
            else -> 0
        }
    }

    private fun processCdata(codePoint: Int) {
        cdataTrailingBrackets = when {
            codePoint == GREATER_THAN && cdataTrailingBrackets >= CDATA_END_BRACKETS -> {
                finishMarkup()
                0
            }
            codePoint == RIGHT_BRACKET -> (cdataTrailingBrackets + 1).coerceAtMost(CDATA_END_BRACKETS)
            else -> 0
        }
    }

    private fun addMarkupBytes(count: Int) {
        markupBytes += count
        if (markupBytes > maxMarkupBytes) {
            throw IOException("XML markup exceeds the $maxMarkupBytes-byte safety limit")
        }
    }

    private fun finishMarkup() {
        mode = Mode.TEXT
        markupBytes = 0
        quote = NO_QUOTE
        declarationQuote = NO_QUOTE
        declarationBracketDepth = 0
        declarationProbe.clear()
        processingInstructionQuestionSeen = false
    }

    private fun isXmlNameStart(codePoint: Int): Boolean =
        codePoint >= NON_ASCII_START ||
            codePoint == UNDERSCORE ||
            codePoint == COLON ||
            codePoint in 'A'.code..'Z'.code ||
            codePoint in 'a'.code..'z'.code

    private fun isPrefixOf(value: IntArray, expected: IntArray): Boolean =
        value.size < expected.size && value.indices.all { value[it] == expected[it] }

    private data class Encoding(val bytesPerCodeUnit: Int, val littleEndian: Boolean)

    companion object {
        private const val PREFIX_SIZE = 4
        private const val SKIP_BUFFER_SIZE = 8 * 1024
        private const val CANCELLATION_CHECK_INTERVAL_BYTES = 4 * 1024
        private const val BYTE_MASK = 0xff
        private const val NULL_BYTE = 0x00
        private const val BOM_FE = 0xfe
        private const val BOM_FF = 0xff
        private const val ANY_BYTE = -1
        private const val UTF16_CODE_UNIT_BYTES = 2
        private const val UTF32_CODE_UNIT_BYTES = 4
        private const val NO_QUOTE = 0
        private const val NON_ASCII_START = 0x80
        private const val LESS_THAN = '<'.code
        private const val GREATER_THAN = '>'.code
        private const val SLASH = '/'.code
        private const val QUESTION = '?'.code
        private const val EXCLAMATION = '!'.code
        private const val SINGLE_QUOTE = '\''.code
        private const val DOUBLE_QUOTE = '"'.code
        private const val LEFT_BRACKET = '['.code
        private const val RIGHT_BRACKET = ']'.code
        private const val HYPHEN = '-'.code
        private const val UNDERSCORE = '_'.code
        private const val COLON = ':'.code
        private const val COMMENT_END_DASHES = 2
        private const val CDATA_END_BRACKETS = 2
        private val COMMENT_OPEN = intArrayOf(HYPHEN, HYPHEN)
        private val CDATA_OPEN = "[CDATA[".map { it.code }.toIntArray()
        private val DEFAULT_ENCODING = Encoding(bytesPerCodeUnit = 1, littleEndian = false)
        private val ENCODING_SIGNATURES = listOf(
            EncodingSignature(
                intArrayOf(NULL_BYTE, NULL_BYTE, BOM_FE, BOM_FF),
                Encoding(UTF32_CODE_UNIT_BYTES, littleEndian = false),
            ),
            EncodingSignature(
                intArrayOf(BOM_FF, BOM_FE, NULL_BYTE, NULL_BYTE),
                Encoding(UTF32_CODE_UNIT_BYTES, littleEndian = true),
            ),
            EncodingSignature(
                intArrayOf(NULL_BYTE, NULL_BYTE, NULL_BYTE, LESS_THAN),
                Encoding(UTF32_CODE_UNIT_BYTES, littleEndian = false),
            ),
            EncodingSignature(
                intArrayOf(LESS_THAN, NULL_BYTE, NULL_BYTE, NULL_BYTE),
                Encoding(UTF32_CODE_UNIT_BYTES, littleEndian = true),
            ),
            EncodingSignature(
                intArrayOf(NULL_BYTE, LESS_THAN, NULL_BYTE, ANY_BYTE),
                Encoding(UTF16_CODE_UNIT_BYTES, littleEndian = false),
            ),
            EncodingSignature(
                intArrayOf(LESS_THAN, NULL_BYTE, ANY_BYTE, NULL_BYTE),
                Encoding(UTF16_CODE_UNIT_BYTES, littleEndian = true),
            ),
            EncodingSignature(
                intArrayOf(BOM_FE, BOM_FF),
                Encoding(UTF16_CODE_UNIT_BYTES, littleEndian = false),
            ),
            EncodingSignature(
                intArrayOf(BOM_FF, BOM_FE),
                Encoding(UTF16_CODE_UNIT_BYTES, littleEndian = true),
            ),
        )

        /** Sniffs BOM/signatures without consuming the prefix from the parser input. */
        fun create(
            input: InputStream,
            maxMarkupBytes: Int,
            checkCancellation: () -> Unit = {},
        ): BoundedXmlMarkupInputStream {
            require(maxMarkupBytes > 0)
            val replay = PushbackInputStream(input, PREFIX_SIZE)
            val prefix = ByteArray(PREFIX_SIZE)
            var count = 0
            var finished = false
            while (count < prefix.size && !finished) {
                val read = replay.read(prefix, count, prefix.size - count)
                when {
                    read < 0 -> finished = true
                    read == 0 -> {
                        val value = replay.read()
                        if (value < 0) finished = true else prefix[count++] = value.toByte()
                    }
                    else -> count += read
                }
            }
            if (count > 0) replay.unread(prefix, 0, count)
            return BoundedXmlMarkupInputStream(
                replay,
                maxMarkupBytes,
                detectEncoding(prefix, count),
                checkCancellation,
            )
        }

        private fun detectEncoding(prefix: ByteArray, count: Int): Encoding =
            ENCODING_SIGNATURES.firstOrNull { signature -> signature.matches(prefix, count) }?.encoding
                ?: DEFAULT_ENCODING

        private data class EncodingSignature(val bytes: IntArray, val encoding: Encoding) {
            fun matches(prefix: ByteArray, count: Int): Boolean =
                count >= bytes.size && bytes.indices.all { index ->
                    val expected = bytes[index]
                    expected == ANY_BYTE || (prefix[index].toInt() and BYTE_MASK) == expected
                }
        }
    }
}
