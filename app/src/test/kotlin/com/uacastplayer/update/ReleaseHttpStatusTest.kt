package com.uacastplayer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The status half of reading `/releases/latest`, which is the half that was wrong.
 *
 * 404 from that endpoint means "no published release", and it was being folded in with 500 and
 * "offline" as one undifferentiated failure. Probed against the real repository on 2026-08-13, with
 * no release cut yet:
 *
 *     /repos/Dovbnyak28/UA-Cast              -> 200
 *     /repos/Dovbnyak28/UA-Cast/releases/latest -> 404
 *     /repos/Dovbnyak28/UA-Cast/releases     -> 200 []
 */
class ReleaseHttpStatusTest {

    @Test
    fun `no published release is not a failure`() {
        assertEquals(ReleaseLookup.NonePublished, ReleaseHttpStatus.readStatus(404))
    }

    @Test
    fun `a success settles nothing on its own because the body still has to be read`() {
        assertNull(ReleaseHttpStatus.readStatus(200))
        assertNull(ReleaseHttpStatus.readStatus(299))
    }

    /** Rate limiting is the one a busy network can produce, and it is emphatically not "up to
     * date" - GitHub allows 60 unauthenticated requests an hour per IP, shared by everyone behind
     * a NAT. */
    @Test
    fun `rate limiting and server errors stay failures`() {
        assertEquals(ReleaseLookup.Failed, ReleaseHttpStatus.readStatus(403))
        assertEquals(ReleaseLookup.Failed, ReleaseHttpStatus.readStatus(429))
        assertEquals(ReleaseLookup.Failed, ReleaseHttpStatus.readStatus(500))
        assertEquals(ReleaseLookup.Failed, ReleaseHttpStatus.readStatus(503))
    }

    /** A redirect is not followed by hand here - OkHttp already has - so seeing one means something
     * unusual, and guessing "up to date" from it would be guessing. */
    @Test
    fun `anything else is a failure rather than a guess`() {
        assertEquals(ReleaseLookup.Failed, ReleaseHttpStatus.readStatus(301))
        assertEquals(ReleaseLookup.Failed, ReleaseHttpStatus.readStatus(401))
        assertEquals(ReleaseLookup.Failed, ReleaseHttpStatus.readStatus(0))
    }
}
