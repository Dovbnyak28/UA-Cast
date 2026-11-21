package com.uacastplayer.ui.home

import com.uacastplayer.R
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgIndex
import com.uacastplayer.epg.EpgUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeEpgStatusTest {
    private val cached = EpgData(EpgIndex(emptyList()), emptyMap())

    @Test fun noSourceNeverClaimsAnOngoingDownload() {
        assertEquals(R.string.home_epg_not_configured, homeEpgStatus(EpgUiState()))
    }

    @Test fun configuredButIdleIsDifferentFromLoading() {
        assertEquals(R.string.home_epg_waiting, homeEpgStatus(EpgUiState(customUrl = "https://example/epg.xml")))
        assertEquals(R.string.home_epg_restoring, homeEpgStatus(EpgUiState(isLoading = true)))
    }

    @Test fun aFailedRefreshDoesNotHideBehindTheOldCachedGuide() {
        assertEquals(R.string.home_epg_unavailable, homeEpgStatus(EpgUiState(data = cached, hasError = true)))
    }

    @Test fun cachedDataAndAnActiveRefreshHaveDistinctStatuses() {
        assertEquals(R.string.home_epg_ready, homeEpgStatus(EpgUiState(data = cached)))
        assertEquals(R.string.home_epg_restoring, homeEpgStatus(EpgUiState(data = cached, isLoading = true)))
    }
}
