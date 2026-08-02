package com.uacastplayer.playlist

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Picks a [Charset] to decode a playlist's raw bytes with. Real-world Ukrainian/Russian IPTV
 * providers routinely serve M3U in Windows-1251 rather than UTF-8: the channels still play fine
 * either way (only the text is affected), which is exactly why this class of bug survives
 * unnoticed - channel and group names just render as mojibake instead of failing loudly.
 *
 * Detection order: (1) a BOM is an explicit, unambiguous declaration - trust it outright; (2) valid
 * UTF-8 is checked next, since a plausible non-UTF-8 byte sequence essentially never validates as
 * UTF-8 by chance (multi-byte UTF-8 has a strict, self-checking structure); (3) anything left over
 * is assumed Windows-1251 - the overwhelmingly likely single-byte legacy encoding for Cyrillic text
 * on the IPTV providers this app targets. [decode] never throws - the fallback tier is unconditional.
 *
 * A server-declared charset is a *hint*, not an authority - see the [detect] overload that takes
 * one.
 */
object CharsetDetector {

    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val UTF16LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val UTF16BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    private val WINDOWS_1251: Charset = Charset.forName("windows-1251")

    /** Only the first chunk needs inspecting - a document that's valid UTF-8 for its first 64KB is
     * for all practical purposes valid UTF-8 throughout, and this keeps detection cheap even for
     * the largest playlists this app accepts (see PlaylistUrlLoader.MAX_PLAYLIST_BYTES). */
    private const val SAMPLE_SIZE_BYTES = 64 * 1024

    fun detect(bytes: ByteArray): Charset {
        val bomCharset = charsetForBom(bytes)
        if (bomCharset != null) return bomCharset
        return if (isValidUtf8(sampleOf(bytes))) Charsets.UTF_8 else WINDOWS_1251
    }

    private fun sampleOf(bytes: ByteArray): ByteArray =
        if (bytes.size > SAMPLE_SIZE_BYTES) bytes.copyOf(SAMPLE_SIZE_BYTES) else bytes

    /**
     * As [detect], but with a charset the source claimed for itself (an HTTP `Content-Type`
     * charset). The claim is a hint that loses to the bytes, in this order:
     *
     * 1. **A BOM wins outright.** It is a declaration made by whoever wrote the actual bytes,
     *    rather than by whatever is serving them.
     * 2. **Valid UTF-8 wins over [declared].** This is the case that matters in practice: IPTV
     *    panels very commonly send `charset=windows-1251` in front of a body that is really UTF-8,
     *    and honouring that turned every Cyrillic channel name into `Liberty РЎРїРѕСЂС‚` - each
     *    Cyrillic letter's two UTF-8 bytes rendered as two separate Windows-1251 characters. Bytes
     *    that decode cleanly as UTF-8 are overwhelming evidence: genuine Windows-1251 Cyrillic
     *    almost never validates, because its letters sit in 0xC0-0xFF and UTF-8 requires every byte
     *    after a lead byte to be 0x80-0xBF.
     * 3. **Otherwise [declared] wins** - unless it says UTF-8, which step 2 has just disproved. Only
     *    now does the declaration carry information: the bytes are definitely not UTF-8, and a
     *    server naming e.g. KOI8-R knows better than this object's Windows-1251 guess. A server
     *    still insisting on UTF-8 at this point is simply wrong, and honouring it would produce a
     *    string full of U+FFFD, so it falls through to [detect] instead.
     * 4. Failing all that, the same Windows-1251 fallback as [detect].
     *
     * So the rule in one line: the declaration is consulted only where the bytes are ambiguous, and
     * it is never allowed to contradict them.
     */
    fun detect(bytes: ByteArray, declared: Charset?): Charset {
        val bom = charsetForBom(bytes)
        return when {
            bom != null -> bom
            declared == null || declared == Charsets.UTF_8 -> detect(bytes)
            isValidUtf8(sampleOf(bytes)) -> Charsets.UTF_8
            else -> declared
        }
    }

    fun decode(bytes: ByteArray): String = String(bytes, detect(bytes))

    private fun charsetForBom(bytes: ByteArray): Charset? = when {
        bytes.startsWith(UTF8_BOM) -> Charsets.UTF_8
        bytes.startsWith(UTF16LE_BOM) -> Charsets.UTF_16LE
        bytes.startsWith(UTF16BE_BOM) -> Charsets.UTF_16BE
        else -> null
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    // The exception itself is expected, not exceptional - decode() throwing CharacterCodingException
    // IS how "not valid UTF-8" is detected, there's nothing about it worth surfacing beyond that.
    @Suppress("SwallowedException")
    private fun isValidUtf8(bytes: ByteArray): Boolean {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes))
            true
        } catch (e: CharacterCodingException) {
            false
        }
    }
}
