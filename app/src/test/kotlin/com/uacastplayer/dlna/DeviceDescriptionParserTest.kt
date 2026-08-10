package com.uacastplayer.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val SAMSUNG_DEVICE_DESCRIPTION = """
    <?xml version="1.0" encoding="UTF-8"?>
    <root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
      <specVersion>
        <major>1</major>
        <minor>0</minor>
      </specVersion>
      <device>
        <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
        <friendlyName>[TV] Samsung Living Room</friendlyName>
        <manufacturer>Samsung Electronics</manufacturer>
        <modelName>UE55XXX</modelName>
        <UDN>uuid:12345678-1234-1234-1234-1234567890ab</UDN>
        <dlna:X_DLNADOC>DMR-1.50</dlna:X_DLNADOC>
        <serviceList>
          <service>
            <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
            <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
            <controlURL>/upnp/control/RenderingControl1</controlURL>
            <eventSubURL>/upnp/event/RenderingControl1</eventSubURL>
            <SCPDURL>/RenderingControl_1.xml</SCPDURL>
          </service>
          <service>
            <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
            <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
            <controlURL>/upnp/control/AVTransport1</controlURL>
            <eventSubURL>/upnp/event/AVTransport1</eventSubURL>
            <SCPDURL>/AVTransport_1.xml</SCPDURL>
          </service>
        </serviceList>
      </device>
    </root>
""".trimIndent()

// LG's real-world device descriptions order controlURL before serviceType inside <service>, and
// use root-relative-without-slash controlURL values - both deliberately different from the
// Samsung fixture above to exercise ordering- and resolution-independence.
private val LG_DEVICE_DESCRIPTION = """
    <?xml version="1.0"?>
    <root xmlns="urn:schemas-upnp-org:device-1-0">
      <specVersion><major>1</major><minor>0</minor></specVersion>
      <device>
        <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
        <friendlyName>LG webOS TV</friendlyName>
        <manufacturer>LG Electronics</manufacturer>
        <serviceList>
          <service>
            <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
            <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
            <controlURL>ConnectionManager/control</controlURL>
            <eventSubURL>ConnectionManager/event</eventSubURL>
            <SCPDURL>ConnectionManager/scpd.xml</SCPDURL>
          </service>
          <service>
            <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
            <controlURL>AVTransport/control</controlURL>
            <eventSubURL>AVTransport/event</eventSubURL>
            <SCPDURL>AVTransport/scpd.xml</SCPDURL>
            <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
          </service>
        </serviceList>
      </device>
    </root>
""".trimIndent()

class DeviceDescriptionParserTest {

    private fun parse(xml: String, locationUrl: String) =
        DeviceDescriptionParser.parse(xml.toByteArray(Charsets.UTF_8), locationUrl)

    @Test
    fun `parses friendlyName and resolves an absolute-path controlURL for a Samsung-style description`() {
        val device = parse(SAMSUNG_DEVICE_DESCRIPTION, "http://192.168.1.50:9197/dmr")

        assertEquals("[TV] Samsung Living Room", device?.friendlyName)
        assertEquals("http://192.168.1.50:9197/upnp/control/AVTransport1", device?.controlUrl)
    }

    @Test
    fun `resolves a relative controlURL for an LG-style description with different tag ordering`() {
        val device = parse(LG_DEVICE_DESCRIPTION, "http://192.168.1.77:1400/description.xml")

        assertEquals("LG webOS TV", device?.friendlyName)
        assertEquals("http://192.168.1.77:1400/AVTransport/control", device?.controlUrl)
    }

    @Test
    fun `a device description with no AVTransport service yields null`() {
        val xml = """
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <friendlyName>Printer</friendlyName>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>/control/RenderingControl1</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()

        assertNull(parse(xml, "http://10.0.0.9/desc.xml"))
    }

    @Test
    fun `a device description with no friendlyName yields null`() {
        val xml = """
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/control/AVTransport1</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()

        assertNull(parse(xml, "http://10.0.0.9/desc.xml"))
    }

    @Test
    fun `malformed XML yields null instead of throwing`() {
        assertNull(parse("<root><device><friendlyName>Broken</device>", "http://10.0.0.9/desc.xml"))
    }

    @Test
    fun `resolveControlUrl handles both absolute-path and relative controlURL forms`() {
        assertEquals(
            "http://10.0.0.9:1400/upnp/control/AVTransport1",
            DeviceDescriptionParser.resolveControlUrl("http://10.0.0.9:1400/desc.xml", "/upnp/control/AVTransport1"),
        )
        assertEquals(
            "http://10.0.0.9:1400/AVTransport/control",
            DeviceDescriptionParser.resolveControlUrl("http://10.0.0.9:1400/desc.xml", "AVTransport/control"),
        )
        assertEquals(
            "http://10.0.0.9:9999/control",
            DeviceDescriptionParser.resolveControlUrl("http://10.0.0.9:1400/desc.xml", "http://10.0.0.9:9999/control"),
        )
    }

    @Test
    fun `resolveControlUrl returns null for an unparsable url`() {
        assertNull(DeviceDescriptionParser.resolveControlUrl("not a url", "also not a url"))
    }

    @Test
    fun theRenderingControlUrlIsExtractedAndResolvedLikeTheTransportOne() {
        val device = parse(SAMSUNG_DEVICE_DESCRIPTION, "http://192.168.0.42:9197/dmr")

        assertEquals(
            "http://192.168.0.42:9197/upnp/control/RenderingControl1",
            device?.renderingControlUrl,
        )
    }

    /**
     * A renderer that plays but exposes no RenderingControl is still a usable target: it costs the
     * volume slider and nothing else. Dropping it would remove a working TV from the list over a
     * service the cast itself never needs.
     */
    @Test
    fun aRendererWithoutRenderingControlIsStillUsable() {
        val noVolume = SAMSUNG_DEVICE_DESCRIPTION.replace(
            "urn:schemas-upnp-org:service:RenderingControl:1",
            "urn:schemas-upnp-org:service:ConnectionManager:1",
        )

        val device = parse(noVolume, "http://192.168.0.42:9197/dmr")

        assertEquals("http://192.168.0.42:9197/upnp/control/AVTransport1", device?.controlUrl)
        assertNull(device?.renderingControlUrl)
    }
}
