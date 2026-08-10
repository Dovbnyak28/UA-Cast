package com.uacastplayer.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderingControlSoapTest {

    /**
     * The header is the whole reason RenderingControl needs its own builder. Sent under the
     * AVTransport namespace a renderer answers with a fault, and the volume silently never moves -
     * a failure that looks exactly like a renderer that does not support volume at all.
     */
    @Test
    fun theSoapActionHeaderCarriesTheRenderingControlNamespace() {
        assertEquals(
            "\"urn:schemas-upnp-org:service:RenderingControl:1#SetVolume\"",
            RenderingControlSoapBuilder.soapAction("SetVolume"),
        )
        assertTrue(AvTransportSoapBuilder.soapAction("Play") != RenderingControlSoapBuilder.soapAction("Play"))
    }

    @Test
    fun setVolumeCarriesTheValueTheMasterChannelAndTheInstance() {
        val envelope = RenderingControlSoapBuilder.setVolumeEnvelope(42)

        assertTrue(envelope.contains("<DesiredVolume>42</DesiredVolume>"))
        assertTrue(envelope.contains("<Channel>Master</Channel>"))
        assertTrue(envelope.contains("<InstanceID>0</InstanceID>"))
        assertTrue(envelope.contains("xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\""))
    }

    @Test
    fun getVolumeAsksForTheMasterChannel() {
        val envelope = RenderingControlSoapBuilder.getVolumeEnvelope()

        assertTrue(envelope.contains("<u:GetVolume"))
        assertTrue(envelope.contains("<Channel>Master</Channel>"))
    }

    @Test
    fun readsTheVolumeOutOfARealShapedResponse() {
        val response = """<?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:GetVolumeResponse xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                  <CurrentVolume>17</CurrentVolume>
                </u:GetVolumeResponse>
              </s:Body>
            </s:Envelope>"""

        assertEquals(17, VolumeResponseParser.parse(response))
    }

    /** Renderers differ in how much whitespace they wrap the value in; the number is the same. */
    @Test
    fun surroundingWhitespaceIsTolerated() {
        assertEquals(5, VolumeResponseParser.parse("<CurrentVolume>  5\n  </CurrentVolume>"))
        assertEquals(0, VolumeResponseParser.parse("<CurrentVolume>0</CurrentVolume>"))
    }

    /**
     * Anything that is not a plain number is null, never a guess. A fault, an empty body and a
     * truncated response all land here, and each would otherwise become a volume the UI shows as
     * fact.
     */
    @Test
    fun anythingThatIsNotAPlainNumberReadsAsUnknown() {
        assertNull(VolumeResponseParser.parse(""))
        assertNull(VolumeResponseParser.parse("<s:Fault><faultcode>s:Client</faultcode></s:Fault>"))
        assertNull(VolumeResponseParser.parse("<CurrentVolume></CurrentVolume>"))
        assertNull(VolumeResponseParser.parse("<CurrentVolume>loud</CurrentVolume>"))
        assertNull(VolumeResponseParser.parse("<CurrentVolume>-5</CurrentVolume>"))
        assertNull(VolumeResponseParser.parse("<CurrentVolume>12"))
    }

    @Test
    fun volumeIsClampedToTheAssumedScaleBeforeItIsSent() {
        assertEquals(0, VolumeRange.clamp(-10))
        assertEquals(100, VolumeRange.clamp(250))
        assertEquals(63, VolumeRange.clamp(63))
        assertTrue(RenderingControlSoapBuilder.setVolumeEnvelope(VolumeRange.clamp(250))
            .contains("<DesiredVolume>100</DesiredVolume>"))
    }
}
