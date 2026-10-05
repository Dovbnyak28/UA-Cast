package com.uacastplayer.ui.epg

import com.uacastplayer.R
import org.junit.Assert.assertEquals
import org.junit.Test

class EpgGuideMessageTest {
    @Test fun `in flight retry wins over previous missing or error state`() {
        assertEquals(R.string.epg_guide_loading, epgGuideEmptyMessage(false, true, true))
    }

    @Test fun `failed download is not presented as unmatched channel`() {
        assertEquals(R.string.epg_guide_error, epgGuideEmptyMessage(false, false, true))
    }

    @Test fun `missing guide and channel with no schedule are distinguishable`() {
        assertEquals(R.string.epg_guide_no_match, epgGuideEmptyMessage(true, false, false))
        assertEquals(R.string.epg_guide_no_data, epgGuideEmptyMessage(false, false, false))
    }
}
