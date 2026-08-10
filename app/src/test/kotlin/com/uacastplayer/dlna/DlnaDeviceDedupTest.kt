package com.uacastplayer.dlna

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One row per renderer in the device sheet.
 *
 * Discovery asks three search targets, twice each, and a device is free to answer each with a
 * different `LOCATION`. Deduplicating those - which is all discovery did - collapses the replies
 * but not the renderers behind them, so a single TV that answered `/desc.xml` to one query and
 * `/description.xml` to another appeared twice in the sheet, under the same name, with nothing on
 * the row to tell the two apart.
 */
class DlnaDeviceDedupTest {

    private fun renderer(name: String, control: String, rendering: String? = null) =
        DlnaDevice(friendlyName = name, controlUrl = control, renderingControlUrl = rendering)

    @Test
    fun oneRendererAnsweringSeveralSearchesIsListedOnce() {
        val devices = listOf(
            renderer("[TV] Samsung 6 Series (40)", "http://192.168.1.5:9197/upnp/control/AVTransport1"),
            renderer("[TV] Samsung 6 Series (40)", "http://192.168.1.5:9197/upnp/control/AVTransport1"),
            renderer("[TV] Samsung 6 Series (40)", "http://192.168.1.5:9197/upnp/control/AVTransport1"),
        )

        assertEquals(1, devices.distinctRenderers().size)
    }

    /**
     * The case the friendly name would get wrong. Two identical speakers out of the box, or a
     * receiver exposing one renderer per zone, share a name and are genuinely two things to cast
     * to - collapsing them would hide one the user might have wanted.
     */
    @Test
    fun twoRenderersSharingANameAreBothKept() {
        val devices = listOf(
            renderer("Sonos One", "http://192.168.1.20:1400/MediaRenderer/AVTransport/Control"),
            renderer("Sonos One", "http://192.168.1.21:1400/MediaRenderer/AVTransport/Control"),
        )

        assertEquals(2, devices.distinctRenderers().size)
    }

    /** Discovery order is the order replies arrived, and the sheet shows it as found. Deduplicating
     * must not reshuffle the list under the user while it is on screen. */
    @Test
    fun theFirstSightingKeepsItsPlace() {
        val tv = renderer("LG webOS TV", "http://192.168.1.5/upnp/control")
        val speaker = renderer("Kitchen", "http://192.168.1.9/upnp/control")

        val result = listOf(tv, speaker, tv, speaker, tv).distinctRenderers()

        assertEquals(listOf(tv, speaker), result)
    }

    /**
     * Two descriptions of one device can disagree about the parts this app treats as optional - a
     * reply to `ssdp:all` that lists RenderingControl and a service-level reply that does not. The
     * first sighting wins, which is the same rule discovery already applies to `LOCATION`; what
     * matters is that the second does not become a second row.
     */
    @Test
    fun descriptionsThatDisagreeAboutVolumeAreStillOneRenderer() {
        val withVolume = renderer("TV", "http://192.168.1.5/av", rendering = "http://192.168.1.5/rc")
        val withoutVolume = renderer("TV", "http://192.168.1.5/av", rendering = null)

        val result = listOf(withVolume, withoutVolume).distinctRenderers()

        assertEquals(listOf(withVolume), result)
    }

    @Test
    fun anEmptyLanStaysEmpty() {
        assertEquals(emptyList<DlnaDevice>(), emptyList<DlnaDevice>().distinctRenderers())
    }
}
