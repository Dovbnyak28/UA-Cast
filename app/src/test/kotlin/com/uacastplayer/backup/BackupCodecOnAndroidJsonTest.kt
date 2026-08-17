package com.uacastplayer.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [BackupCodec] against the `org.json` that actually runs on a phone.
 *
 * `BackupCodecTest` beside this one runs on the reference `org.json` from Maven
 * (`testImplementation(libs.org.json)`), and the two implementations do not agree. Measured
 * directly, same input, same call, on `{"a":null}`:
 *
 * - reference `org.json`: `optString("a")` is `""`
 * - Android's `org.json`: `optString("a")` is `"null"` - a four-character string
 *
 * Android's `optString` goes through `JSON.toString(opt(name))`, and `JSONObject.NULL.toString()`
 * is `"null"`, so an explicit null in the file arrives as text that looks like a value. Every blank
 * check in this codec is what decides whether a field is present, so on a device that check was
 * being handed a non-blank string for a field that was explicitly null.
 *
 * A backup this app writes never contains one - `putOpt` drops nulls - but the format is documented
 * as portable and hand-editable, and `decode`'s own contract is about surviving exactly such a file.
 *
 * This class exists as much for the next divergence as for this one: a JSON codec whose only tests
 * run on a different JSON implementation is a codec nobody has tested.
 */
@RunWith(RobolectricTestRunner::class)
class BackupCodecOnAndroidJsonTest {

    /** The measurement above, pinned. If this ever reads `""`, the test runner has stopped using
     * Android's `org.json` and the rest of this class has stopped testing anything. */
    @Test
    fun `the runner really is on android's org-json`() {
        assertEquals("null", JSONObject("""{"a":null}""").optString("a"))
    }

    private fun backup(favorite: String) = """
        {"version":1,"sources":[],"favorites":[$favorite],"settings":{}}
    """.trimIndent()

    @Test
    fun `an explicitly null tvg-id is absent, not the word null`() {
        val decoded = BackupCodec.decode(
            backup("""{"key":"k","displayName":"One","streamUrl":"http://h/1","tvgId":null}"""),
        )

        assertEquals(1, decoded?.favorites?.size)
        assertNull("tvgId came through as text", decoded?.favorites?.first()?.tvgId)
    }

    @Test
    fun `an explicitly null group title is absent, not the word null`() {
        val decoded = BackupCodec.decode(
            backup("""{"key":"k","displayName":"One","streamUrl":"http://h/1","groupTitle":null}"""),
        )

        assertNull("groupTitle came through as text", decoded?.favorites?.first()?.groupTitle)
    }

    /**
     * A required field that is explicitly null must fail the same emptiness check a missing one
     * fails, or the entry is imported with `"null"` where its stream URL should be - a favorite that
     * looks ordinary in the list and plays nothing.
     */
    @Test
    fun `a favorite whose required field is explicitly null is skipped`() {
        val decoded = BackupCodec.decode(
            backup("""{"key":"k","displayName":"One","streamUrl":null}"""),
        )

        assertEquals(emptyList<BackupFavorite>(), decoded?.favorites)
    }

    @Test
    fun `a source whose required field is explicitly null is skipped`() {
        val decoded = BackupCodec.decode(
            """{"version":1,"sources":[{"id":"s","type":"URL","location":null}],"favorites":[],"settings":{}}""",
        )

        assertEquals(emptyList<BackupPlaylistSource>(), decoded?.sources)
    }

    /** Settings are read the same way and reach `enumValueOf`, where `"null"` is merely refused -
     * but it must be refused for being absent, not for failing to name a constant. */
    @Test
    fun `explicitly null settings are absent`() {
        val decoded = BackupCodec.decode(
            """{"version":1,"sources":[],"favorites":[],"settings":{"listDensity":null,"epgCustomUrl":null}}""",
        )

        assertNull(decoded?.settings?.listDensity)
        assertNull(decoded?.settings?.epgCustomUrl)
    }

    /** The control: a real value is still read, so the fix cannot be "return null for everything". */
    @Test
    fun `a present value still arrives intact`() {
        val decoded = BackupCodec.decode(
            backup("""{"key":"k","displayName":"One","streamUrl":"http://h/1","tvgId":"one.ua"}"""),
        )

        assertEquals("one.ua", decoded?.favorites?.first()?.tvgId)
    }
}
