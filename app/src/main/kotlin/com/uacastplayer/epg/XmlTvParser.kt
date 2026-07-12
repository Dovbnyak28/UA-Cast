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
    const val MAX_PROGRAMMES = 250_000
    const val MAX_TEXT_LENGTH = 16 * 1024

    fun parse(input: InputStream): XmlTvParseResult {
        val factory = SAXParserFactory.newInstance()
        trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true)

        val reader = factory.newSAXParser().xmlReader
        trySetFeature(reader, "http://xml.org/sax/features/external-general-entities", false)
        trySetFeature(reader, "http://xml.org/sax/features/external-parameter-entities", false)
        reader.entityResolver = EntityResolver { _, _ -> InputSource(StringReader("")) }

        val handler = XmlTvHandler()
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

private class XmlTvHandler : DefaultHandler() {

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
    private var currentProgrammeDesc: StringBuilder? = null

    private var textTarget: StringBuilder? = null

    override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
        when (qName) {
            "channel" -> {
                currentChannelId = attributes.getValue("id")
                currentDisplayNames = mutableListOf()
                currentIconUrl = null
            }
            "display-name" -> textTarget = StringBuilder()
            "icon" -> if (currentChannelId != null) currentIconUrl = attributes.getValue("src")
            "programme" -> {
                currentProgrammeChannelId = attributes.getValue("channel")
                currentProgrammeStart = attributes.getValue("start")?.let(XmlTvTimeParser::parse)
                currentProgrammeStop = attributes.getValue("stop")?.let(XmlTvTimeParser::parse)
                currentProgrammeTitle = null
                currentProgrammeDesc = null
            }
            "title" -> if (currentProgrammeChannelId != null) {
                textTarget = StringBuilder()
                currentProgrammeTitle = textTarget
            }
            "desc" -> if (currentProgrammeChannelId != null) {
                textTarget = StringBuilder()
                currentProgrammeDesc = textTarget
            }
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
            "display-name" -> {
                currentDisplayNames?.add(textTarget.toString().trim())
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
            "title", "desc" -> textTarget = null
            "programme" -> {
                val channelId = currentProgrammeChannelId
                val start = currentProgrammeStart
                if (channelId != null && start != null) {
                    if (programmes.size < XmlTvParser.MAX_PROGRAMMES) {
                        programmes += EpgProgramme(
                            channelId = channelId,
                            startMillis = start,
                            stopMillis = currentProgrammeStop ?: start,
                            title = currentProgrammeTitle?.toString()?.trim().orEmpty(),
                            description = currentProgrammeDesc?.toString()?.trim()?.ifEmpty { null },
                        )
                    } else {
                        programmeLimitExceeded = true
                    }
                }
                currentProgrammeChannelId = null
                currentProgrammeStart = null
                currentProgrammeStop = null
                currentProgrammeTitle = null
                currentProgrammeDesc = null
            }
        }
    }

    fun result() = XmlTvParseResult(channels, programmes, channelLimitExceeded, programmeLimitExceeded)
}
