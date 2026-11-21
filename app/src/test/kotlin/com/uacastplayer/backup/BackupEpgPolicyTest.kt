package com.uacastplayer.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEpgPolicyTest {
    private val empty = BackupData(emptyList(), emptyList(), BackupSettings())
    private val guide = "https://guide.example.test/epg.xml"
    private val off = BackupSettings(epgDisabled = true)

    @Test fun anExplicitOffChoiceWinsOverAnyConflictingGuideUrl() {
        for (url in listOf(null, "", guide, "file:///private.xml")) {
            assertEquals("", BackupEpgPolicy.urlToApply(off.copy(epgCustomUrl = url)))
        }
    }

    @Test fun missingOrInvalidGuideWithoutAnOffChoiceKeepsTheTargetSelection() {
        for (url in listOf(null, "", "file:///private.xml", "not a URL")) {
            assertNull(BackupEpgPolicy.urlToApply(BackupSettings(epgCustomUrl = url)))
        }
    }

    @Test fun equivalentNormalizedUrlsDoNotPromiseAnUnnecessaryChange() {
        val settings = BackupSettings(epgCustomUrl = "  HTTPS://GUIDE.example.test:443/epg.xml  ")
        assertEquals(guide, BackupEpgPolicy.urlToApply(settings))
        assertEquals(emptyList<String>(), preview(settings, BackupSettings(epgCustomUrl = guide)).changedSettings)
    }

    @Test fun previewNamesTheOffActionForBothConfiguredAndUnchosenTargets() {
        for (current in listOf(BackupSettings(), BackupSettings(epgCustomUrl = guide))) {
            assertEquals(listOf("epgDisabled"), preview(off, current).changedSettings)
        }
        assertEquals(emptyList<String>(), preview(off, off).changedSettings)
    }

    @Test fun previewMatchesAReplacementUrlAndLeavesLegacyMissingChoicesAlone() {
        val custom = BackupSettings(epgCustomUrl = guide)
        assertEquals(listOf("epgCustomUrl"), preview(custom, off).changedSettings)
        assertEquals(emptyList<String>(), preview(BackupSettings(), custom).changedSettings)
        assertEquals(emptyList<String>(), preview(BackupSettings(), off).changedSettings)
    }

    @Test fun onlyAnExplicitJsonBooleanTrueCanDisableTheGuide() {
        for (raw in listOf("false", "null", "\"true\"", "1", "[]", "{}")) {
            val decoded = requireNotNull(BackupCodec.decode(document("\"epgDisabled\":$raw")))
            assertFalse("Invalid marker $raw must not erase the target guide", decoded.settings.epgDisabled)
            assertNull(BackupEpgPolicy.urlToApply(decoded.settings))
        }
        val decoded = requireNotNull(BackupCodec.decode(document("\"epgDisabled\":true")))
        assertTrue(decoded.settings.epgDisabled)
        assertEquals("", BackupEpgPolicy.urlToApply(decoded.settings))
    }

    @Test fun theMarkerRoundTripsAndRemainsOptionalInOldV1AndV2Documents() {
        assertEquals(off, BackupCodec.decode(BackupCodec.encode(empty.copy(settings = off)))?.settings)
        for (version in 1..2) {
            val decoded = requireNotNull(BackupCodec.decode(document("", version)))
            assertFalse(decoded.settings.epgDisabled)
        }
        assertFalse(BackupCodec.encode(empty).contains("epgDisabled"))
    }

    private fun preview(imported: BackupSettings, current: BackupSettings) =
        BackupPreviewPolicy.create(empty.copy(settings = imported), empty.copy(settings = current))

    private fun document(settings: String, version: Int = 2) =
        """{"version":$version,"sources":[],"favorites":[],"settings":{$settings}}"""
}
