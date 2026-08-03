package com.uacastplayer.dlna

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document

class AvTransportSoapBuilderTest {

    private fun parseXml(xml: String): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    private fun firstTextOf(doc: Document, tagName: String): String =
        doc.getElementsByTagName(tagName).item(0).textContent

    @Test
    fun `SetAVTransportURI envelope is well-formed and carries the media url, title and action name`() {
        val envelope = AvTransportSoapBuilder.setAvTransportUriEnvelope(
            mediaUrl = "http://192.168.1.20:8080/hls/tok3n/abc123",
            title = "News 24 & More",
        )

        val doc = parseXml(envelope)

        assertEquals(1, doc.getElementsByTagName("u:SetAVTransportURI").length)
        assertEquals("http://192.168.1.20:8080/hls/tok3n/abc123", firstTextOf(doc, "CurrentURI"))

        val metadata = firstTextOf(doc, "CurrentURIMetaData")
        assertTrue(metadata.contains("http://192.168.1.20:8080/hls/tok3n/abc123"))
        // metadata is only decoded one level by the outer parse - the DIDL-Lite fragment's own
        // entity-escaping (title's "&" -> "&amp;") is still intact at this point; see the
        // didlDoc round-trip below for the fully-decoded assertion.
        assertTrue(metadata.contains("News 24 &amp; More"))

        // CurrentURIMetaData carries the DIDL-Lite fragment as escaped text; it must itself be
        // well-formed XML once unescaped, not just a string that happens to contain the right words.
        val didlDoc = parseXml(metadata)
        assertEquals("News 24 & More", firstTextOf(didlDoc, "dc:title"))
    }

    @Test
    fun `Play envelope is well-formed and names the Play action`() {
        val doc = parseXml(AvTransportSoapBuilder.playEnvelope())

        assertEquals(1, doc.getElementsByTagName("u:Play").length)
        assertEquals("0", firstTextOf(doc, "InstanceID"))
        assertEquals("1", firstTextOf(doc, "Speed"))
    }

    @Test
    fun `Stop envelope is well-formed and names the Stop action`() {
        val doc = parseXml(AvTransportSoapBuilder.stopEnvelope())

        assertEquals(1, doc.getElementsByTagName("u:Stop").length)
        assertEquals("0", firstTextOf(doc, "InstanceID"))
    }

    @Test
    fun `didlLite is itself well-formed and includes the media url and title`() {
        val didl = AvTransportSoapBuilder.didlLite(mediaUrl = "http://10.0.0.5/stream.m3u8", title = "Sports <Live>")

        val doc = parseXml(didl)

        assertEquals("Sports <Live>", firstTextOf(doc, "dc:title"))
        assertEquals("http://10.0.0.5/stream.m3u8", firstTextOf(doc, "res"))
    }

    /**
     * The proxy url serves a rewritten HLS playlist, remuxed MPEG-TS or an origin passthrough
     * depending on what the channel turns out to be, and which one is not known when this metadata
     * is built. Naming one type was a claim the proxy could not keep - and it named HLS, which most
     * non-Samsung sets will not take over DLNA, so a renderer that filters on protocolInfo refused
     * channels it could have played.
     */
    @Test
    fun `the res protocolInfo does not commit to a content type the proxy may not serve`() {
        val didl = AvTransportSoapBuilder.didlLite("http://10.0.0.5/hls/tok/abc", "Any")
        val protocolInfo = parseXml(didl).getElementsByTagName("res").item(0)
            .attributes.getNamedItem("protocolInfo").nodeValue

        assertTrue("must be an http-get resource", protocolInfo.startsWith("http-get:*:"))
        assertTrue("MIME field must stay a wildcard: $protocolInfo", protocolInfo.startsWith("http-get:*:*:"))
    }

    /** Without these a renderer may treat a live channel as a seekable file and stall on the first
     * range request the origin answers with the head of the stream. */
    @Test
    fun `the res advertises a non-seekable live stream`() {
        val didl = AvTransportSoapBuilder.didlLite("http://10.0.0.5/hls/tok/abc", "Any")
        val protocolInfo = parseXml(didl).getElementsByTagName("res").item(0)
            .attributes.getNamedItem("protocolInfo").nodeValue

        assertTrue("no seek support: $protocolInfo", protocolInfo.contains("DLNA.ORG_OP=00"))
        assertTrue("streaming-mode flags: $protocolInfo", protocolInfo.contains("DLNA.ORG_FLAGS=8D5"))
    }

    /** videoBroadcast rather than the generic videoItem is what tells a renderer to render a live
     * channel - no duration, no scrub bar - instead of a progress bar for a stream with no end. */
    @Test
    fun `the item is classed as a live broadcast`() {
        val didl = AvTransportSoapBuilder.didlLite("http://10.0.0.5/hls/tok/abc", "Any")
        assertEquals("object.item.videoItem.videoBroadcast", firstTextOf(parseXml(didl), "upnp:class"))
    }

    @Test
    fun `soapAction quotes the service type and action name for the SOAPACTION header`() {
        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
            AvTransportSoapBuilder.soapAction("SetAVTransportURI"),
        )
    }
}
