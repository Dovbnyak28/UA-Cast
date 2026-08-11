package com.uacastplayer.epg

import java.io.InputStream
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.XMLReader
import org.xml.sax.helpers.DefaultHandler

data class XmlTvParseResult(
    val channels: List<EpgChannel>,
    val programmes: List<EpgProgramme>,
    val channelLimitExceeded: Boolean,
    val programmeLimitExceeded: Boolean,
)

/**
 * SAX-based XMLTV parser. Hardened against XXE (external entities disabled, entity resolver
 * returns nothing) while still accepting a DOCTYPE declaration, since real-world feeds routinely
 * have one. Feature names are set defensively: Android's Expat-backed parser and the desktop
 * JVM's Xerces-backed one (used by unit tests) don't recognize the same feature set.
 */
object XmlTvParser {

    const val MAX_CHANNELS = 25_000

    /**
     * The ceiling on retained programmes, after [EpgRetentionPolicy] has dropped the ones that
     * already finished.
     *
     * 250,000 was not enough and the shortfall was not small: the shipped feed carries 793,417
     * programmes, so two thirds of every guide was cut - and cut in document order, which meant
     * whole channels at the end of the file had no listings at all rather than everyone losing the
     * far future. That is what the Settings warning about an incomplete guide was reporting.
     *
     * Measured on that feed, dropping finished programmes leaves 346,837. This number is that,
     * rounded up for headroom: enough that the cap stops being reached by an ordinary guide, while
     * still bounding a malicious or broken feed. It costs about 20% more memory than the 250,000
     * arbitrary programmes held before, and every one of them is now a programme somebody can see.
     */
    const val MAX_PROGRAMMES = 400_000

    /**
     * Caps a single `<display-name>` or `<title>`. These are the only text this parser keeps now
     * that `<desc>` is skipped (see [EpgProgramme]), and both are names: 512 characters is far
     * beyond any real one. The old 16KB was sized for descriptions, and with up to
     * [MAX_PROGRAMMES] titles retained it left the worst case at gigabytes.
     */
    const val MAX_TEXT_LENGTH = 512

    /**
     * @param keepFromMillis drops programmes that had already finished by this instant - see
     *   [EpgRetentionPolicy], which is what a caller with a clock should use to compute it. The
     *   default of 0 keeps the whole feed, which is what a caller without one wants.
     */
    fun parse(input: InputStream, keepFromMillis: Long = 0L): XmlTvParseResult {
        val factory = SAXParserFactory.newInstance()
        trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true)

        val reader = factory.newSAXParser().xmlReader
        trySetFeature(reader, "http://xml.org/sax/features/external-general-entities", false)
        trySetFeature(reader, "http://xml.org/sax/features/external-parameter-entities", false)
        reader.entityResolver = EntityResolver { _, _ -> InputSource(StringReader("")) }

        val handler = XmlTvHandler(keepFromMillis)
        reader.contentHandler = handler
        reader.parse(InputSource(input))
        return handler.result()
    }

    private fun trySetFeature(factory: SAXParserFactory, name: String, value: Boolean) {
        try {
            factory.setFeature(name, value)
        } catch (_: Exception) {
            // Feature unsupported by this platform's parser implementation; skip it.
        }
    }

    private fun trySetFeature(reader: XMLReader, name: String, value: Boolean) {
        try {
            reader.setFeature(name, value)
        } catch (_: Exception) {
            // Feature unsupported by this platform's parser implementation; skip it.
        }
    }
}

private class XmlTvHandler(private val keepFromMillis: Long) : DefaultHandler() {

    private val channels = mutableListOf<EpgChannel>()
    private val programmes = mutableListOf<EpgProgramme>()
    private var channelLimitExceeded = false
    private var programmeLimitExceeded = false

    private var currentChannelId: String? = null
    private var currentDisplayNames: MutableList<String>? = null
    private var currentIconUrl: String? = null

    private var currentProgrammeChannelId: String? = null
    private var currentProgrammeStart: Long? = null
    private var currentProgrammeStop: Long? = null
    private var currentProgrammeTitle: StringBuilder? = null

    /**
     * Set for a programme that finished before [keepFromMillis], to drop it without building
     * anything for it first. Decided in `startElement` rather than at the end so the `<title>` of a
     * programme nobody will see is never accumulated into a String at all - on the shipped feed
     * that is 388,863 Strings not built per download, which is most of the parse.
     */
    private var skippingProgramme = false

    private var textTarget: StringBuilder? = null

    // A channel id arrives as a fresh String from every single attribute lookup, and there is one
    // per <programme> - up to MAX_PROGRAMMES of them, all held for as long as the schedule is. The
    // distinct values are bounded by the channel count instead (a few thousand at most), so pooling
    // them collapses ~250k Strings into that. getOrPut keeps the first instance seen and hands it
    // back for every later occurrence of the same id.
    private val channelIdPool = HashMap<String, String>()

    private fun internChannelId(id: String): String = channelIdPool.getOrPut(id) { id }

    override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
        when (qName) {
            "channel" -> {
                currentChannelId = attributes.getValue("id")?.let(::internChannelId)
                currentDisplayNames = mutableListOf()
                currentIconUrl = null
            }
            "display-name" -> textTarget = StringBuilder()
            "icon" -> if (currentChannelId != null) currentIconUrl = attributes.getValue("src")
            "programme" -> {
                currentProgrammeChannelId = attributes.getValue("channel")?.let(::internChannelId)
                currentProgrammeStart = attributes.getValue("start")?.let(XmlTvTimeParser::parse)
                currentProgrammeStop = attributes.getValue("stop")?.let(XmlTvTimeParser::parse)
                currentProgrammeTitle = null
                // A feed with no stop time gets judged on its start: EpgProgramme falls back to the
                // start for its stop too, so the two agree about when this programme ended.
                val effectiveStop = currentProgrammeStop ?: currentProgrammeStart
                skippingProgramme = effectiveStop != null &&
                    !EpgRetentionPolicy.isWorthKeeping(effectiveStop, keepFromMillis)
            }
            "title" -> if (currentProgrammeChannelId != null && !skippingProgramme) {
                textTarget = StringBuilder()
                currentProgrammeTitle = textTarget
            }
            // <desc> is deliberately not accumulated - see EpgProgramme for the measurement. Its
            // text still streams through characters(), but with no target it is dropped there
            // rather than built into a String that nothing reads.
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        val target = textTarget ?: return
        val remaining = XmlTvParser.MAX_TEXT_LENGTH - target.length
        if (remaining <= 0) return
        target.append(ch, start, minOf(length, remaining))
    }

    override fun endElement(uri: String?, localName: String?, qName: String) {
        when (qName) {
            // `textTarget`, not `textTarget.toString()` on the nullable field: that resolves to
            // Any?.toString(), which yields the literal string "null" when the builder is absent -
            // and it can be absent here, because endElement("title"/"desc") clears the target
            // unconditionally, so any <title>/<desc> nested inside a <display-name> left this
            // recording "null" as the channel's name. Blank names are dropped too: they match
            // nothing useful in EpgIndex and only add an empty key to its name map.
            "display-name" -> {
                textTarget?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { currentDisplayNames?.add(it) }
                textTarget = null
            }
            "channel" -> {
                val id = currentChannelId
                if (id != null) {
                    if (channels.size < XmlTvParser.MAX_CHANNELS) {
                        channels += EpgChannel(id, currentDisplayNames.orEmpty(), currentIconUrl)
                    } else {
                        channelLimitExceeded = true
                    }
                }
                currentChannelId = null
                currentDisplayNames = null
                currentIconUrl = null
            }
            // "desc" is still listed even though startElement no longer opens a target for it:
            // a <desc> nested inside a <display-name> must keep clearing that display-name's
            // target, which is what stops the name being recorded (see the note above).
            "title", "desc" -> textTarget = null
            "programme" -> {
                val channelId = currentProgrammeChannelId
                val start = currentProgrammeStart
                // A programme dropped for being over is not truncation and must not raise
                // `programmeLimitExceeded`: nothing was lost that a viewer could have watched, and
                // telling them their guide is incomplete because yesterday is missing would be a
                // warning they can neither act on nor turn off.
                if (channelId != null && start != null && !skippingProgramme) {
                    if (programmes.size < XmlTvParser.MAX_PROGRAMMES) {
                        programmes += EpgProgramme(
                            channelId = channelId,
                            startMillis = start,
                            stopMillis = currentProgrammeStop ?: start,
                            title = currentProgrammeTitle?.toString()?.trim().orEmpty(),
                        )
                    } else {
                        programmeLimitExceeded = true
                    }
                }
                currentProgrammeChannelId = null
                currentProgrammeStart = null
                currentProgrammeStop = null
                currentProgrammeTitle = null
                skippingProgramme = false
            }
        }
    }

    fun result() = XmlTvParseResult(channels, programmes, channelLimitExceeded, programmeLimitExceeded)
}
