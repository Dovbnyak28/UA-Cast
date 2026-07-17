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

    @Test
    fun `soapAction quotes the service type and action name for the SOAPACTION header`() {
        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
            AvTransportSoapBuilder.soapAction("SetAVTransportURI"),
        )
    }
}
