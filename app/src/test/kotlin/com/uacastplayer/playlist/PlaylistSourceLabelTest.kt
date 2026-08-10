package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a playlist is called when nobody named it.
 *
 * The screens fell back to the source id, which is a SHA-256 of the location - so the home screen
 * announced "Active playlist: 6368ffd4" in its largest type, on a real phone, for the author's own
 * playlist. These are the labels that replace it.
 */
class PlaylistSourceLabelTest {

    private fun label(type: PlaylistSourceType, location: String) =
        PlaylistSourceLabel.forLocation(type, location)

    @Test
    fun aUrlIsNamedAfterItsHostAndFile() {
        assertEquals("iptv.example.com/list.m3u", label(PlaylistSourceType.URL, "http://iptv.example.com/list.m3u"))
        assertEquals("example.com/tv.m3u8", label(PlaylistSourceType.URL, "https://www.example.com/tv.m3u8"))
    }

    /**
     * The query string is dropped, and this is the reason rather than tidiness: a get.php URL
     * carries `username=` and `password=` in it. A label is rendered on a home screen somebody may
     * be showing to a room, and screenshots of that screen end up in bug reports.
     */
    @Test
    fun credentialsInAUrlNeverReachTheLabel() {
        val withCredentials = "http://box.example.com/get.php?username=roman&password=hunter2&type=m3u"
        val labelled = label(PlaylistSourceType.URL, withCredentials)

        assertEquals("box.example.com/get.php", labelled)
        assertTrue("no credential may survive", labelled!!.none { it == '?' } && !labelled.contains("hunter2"))
    }

    /** An Xtream location *is* the credentials, so only the host is ever usable. */
    @Test
    fun anXtreamSourceIsNamedAfterItsServer() {
        val xtream = "http://box.example.com/get.php?username=roman&password=hunter2"
        val labelled = label(PlaylistSourceType.XTREAM, xtream)

        assertEquals("box.example.com", labelled)
    }

    /**
     * A Storage Access Framework URI is opaque on purpose - the author's own playlist reads
     * `.../document/msf%3A965`, where the tail is a row id in someone else's database. There is
     * nothing here worth showing, so this says so instead of inventing something; the real file
     * name is asked of the provider when the file is picked.
     */
    @Test
    fun aFileUriHasNothingWorthShowing() {
        val downloads = "content://com.android.providers.downloads.documents/document/msf%3A965"

        assertNull(label(PlaylistSourceType.FILE, downloads))
    }

    /** A host with no file after it still names the source. */
    @Test
    fun aBareHostIsEnough() {
        assertEquals("example.com", label(PlaylistSourceType.URL, "http://example.com"))
        assertEquals("example.com", label(PlaylistSourceType.URL, "http://example.com/"))
    }

    /**
     * Only the host and the last segment are used, so a deep path does not make a long label - and
     * the length guard exists for the case that does: providers that hand out tokenised file names.
     * The end is kept because that is the part telling two lists on one server apart.
     */
    @Test
    fun aTokenisedFileNameIsCutFromTheFront() {
        val deepPath = "http://example.com/" + "very-long-segment/".repeat(12) + "list.m3u"
        assertEquals("a deep path is not a long name", "example.com/list.m3u", label(PlaylistSourceType.URL, deepPath))

        val tokenised = "http://example.com/" + "a".repeat(80) + ".m3u"
        val labelled = label(PlaylistSourceType.URL, tokenised)!!

        assertTrue("kept short", labelled.length <= 48)
        assertTrue("keeps the distinguishing end", labelled.endsWith(".m3u"))
        assertTrue("says it was cut", labelled.startsWith("…"))
    }

    @Test
    fun rubbishIsNotDressedUpAsAName() {
        assertNull(label(PlaylistSourceType.URL, ""))
        assertNull(label(PlaylistSourceType.XTREAM, ""))
    }
}
