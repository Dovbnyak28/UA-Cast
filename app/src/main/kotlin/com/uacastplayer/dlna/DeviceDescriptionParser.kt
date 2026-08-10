package com.uacastplayer.dlna

import java.io.ByteArrayInputStream
import java.io.StringReader
import java.net.URI
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.XMLReader
import org.xml.sax.helpers.DefaultHandler

private const val AV_TRANSPORT_SERVICE_TYPE_PREFIX = "urn:schemas-upnp-org:service:avtransport:"
private const val RENDERING_CONTROL_SERVICE_TYPE_PREFIX = "urn:schemas-upnp-org:service:renderingcontrol:"
private const val MAX_TEXT_LENGTH = 4 * 1024

/**
 * Hardened SAX parser for a UPnP device description XML body (fetched from a discovered device's
 * LOCATION url). Same XXE-hardening discipline as [com.uacastplayer.epg.XmlTvParser]: external
 * entities disabled, entity resolver returns nothing, feature names set defensively since
 * Android's Expat-backed parser and the desktop JVM's Xerces-backed one (used by unit tests) don't
 * recognize the same feature set.
 *
 * Only the first `AVTransport` service found is used - a real renderer exposes exactly one. Its
 * `controlURL` is very often a relative path, so it is resolved against [locationUrl] the same way
 * `M3u8Rewriter` resolves playlist references, via [URI.resolve].
 */
object DeviceDescriptionParser {

    fun parse(xml: ByteArray, locationUrl: String): DlnaDevice? {
        val handler = DeviceDescriptionHandler()
        val reader = try {
            hardenedReader(handler)
        } catch (_: Exception) {
            return null
        }
        return try {
            reader.parse(InputSource(ByteArrayInputStream(xml)))
            handler.result(locationUrl)
        } catch (_: Exception) {
            null
        }
    }

    fun resolveControlUrl(locationUrl: String, controlUrl: String): String? = try {
        URI(locationUrl).resolve(URI(controlUrl)).toString()
    } catch (_: Exception) {
        null
    }

    private fun hardenedReader(handler: DefaultHandler): XMLReader {
        val factory = SAXParserFactory.newInstance()
        trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true)

        val reader = factory.newSAXParser().xmlReader
        trySetFeature(reader, "http://xml.org/sax/features/external-general-entities", false)
        trySetFeature(reader, "http://xml.org/sax/features/external-parameter-entities", false)
        reader.entityResolver = EntityResolver { _, _ -> InputSource(StringReader("")) }
        reader.contentHandler = handler
        return reader
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

private class DeviceDescriptionHandler : DefaultHandler() {

    private var friendlyName: String? = null
    private var avTransportControlUrl: String? = null

    private var renderingControlUrl: String? = null
    private var insideService = false
    private var pendingServiceType: String? = null
    private var pendingControlUrl: String? = null

    private var textTarget: StringBuilder? = null

    override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
        when (localNameOf(qName)) {
            "friendlyName" -> if (friendlyName == null) textTarget = StringBuilder()
            "service" -> {
                insideService = true
                pendingServiceType = null
                pendingControlUrl = null
            }
            "serviceType" -> if (insideService) textTarget = StringBuilder()
            "controlURL" -> if (insideService) textTarget = StringBuilder()
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        val target = textTarget ?: return
        val remaining = MAX_TEXT_LENGTH - target.length
        if (remaining <= 0) return
        target.append(ch, start, minOf(length, remaining))
    }

    override fun endElement(uri: String?, localName: String?, qName: String) {
        when (localNameOf(qName)) {
            "friendlyName" -> {
                if (friendlyName == null) friendlyName = textTarget?.toString()?.trim()
                textTarget = null
            }
            "serviceType" -> {
                if (insideService) pendingServiceType = textTarget?.toString()?.trim()
                textTarget = null
            }
            "controlURL" -> {
                if (insideService) pendingControlUrl = textTarget?.toString()?.trim()
                textTarget = null
            }
            "service" -> {
                if (avTransportControlUrl == null && isAvTransport(pendingServiceType)) {
                    avTransportControlUrl = pendingControlUrl
                }
                if (renderingControlUrl == null && isRenderingControl(pendingServiceType)) {
                    renderingControlUrl = pendingControlUrl
                }
                insideService = false
                pendingServiceType = null
                pendingControlUrl = null
            }
        }
    }

    /** Null unless the description yielded everything needed to actually cast to this renderer: a
     * name to show, an AVTransport control URL, and a way to resolve that URL against the device's
     * own location (it is routinely relative). A device missing any of the three is not a usable
     * target, so it is dropped rather than surfaced as an entry that would fail on tap. */
    fun result(locationUrl: String): DlnaDevice? {
        val name = friendlyName?.ifBlank { null }
        val rawControlUrl = avTransportControlUrl?.ifBlank { null }
        val resolvedControlUrl = rawControlUrl?.let { DeviceDescriptionParser.resolveControlUrl(locationUrl, it) }
        // RenderingControl is optional: a renderer that plays but exposes no volume service is a
        // perfectly usable target, so a missing one costs the volume slider and nothing else.
        val resolvedVolumeUrl = renderingControlUrl?.ifBlank { null }
            ?.let { DeviceDescriptionParser.resolveControlUrl(locationUrl, it) }
        return if (name != null && resolvedControlUrl != null) {
            DlnaDevice(
                friendlyName = name,
                controlUrl = resolvedControlUrl,
                renderingControlUrl = resolvedVolumeUrl,
            )
        } else {
            null
        }
    }

    private fun isAvTransport(serviceType: String?): Boolean =
        serviceType?.lowercase()?.startsWith(AV_TRANSPORT_SERVICE_TYPE_PREFIX) == true

    private fun isRenderingControl(serviceType: String?): Boolean =
        serviceType?.lowercase()?.startsWith(RENDERING_CONTROL_SERVICE_TYPE_PREFIX) == true

    private fun localNameOf(qName: String): String = qName.substringAfterLast(':')
}
