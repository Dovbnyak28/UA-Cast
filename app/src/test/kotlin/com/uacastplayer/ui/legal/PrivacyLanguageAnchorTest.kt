package com.uacastplayer.ui.legal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyLanguageAnchorTest {
    @Test fun onlyReviewedLanguagesInTheSameDocumentAreAllowed() {
        for (language in listOf("en", "uk", "ru")) {
            assertTrue(isPrivacyLanguageAnchor("file:///android_asset/privacy-policy.html#$language"))
        }
    }

    @Test fun anAnchorCannotEscapeTheOfflineDocument() {
        for (url in listOf(
            "file:///android_asset/privacy-policy.html",
            "file:///android_asset/privacy-policy.html?file=secrets#uk",
            "file:///android_asset/terms-of-use.html#uk",
            "file:///data/data/com.uacastplayer/shared_prefs/private.xml#uk",
            "file:///android_asset/../private.xml#uk",
            "https://example.org/privacy-policy.html#uk",
            "javascript:alert(1)",
            "content://provider/private#uk",
            "file:///android_asset/privacy-policy.html#unknown",
        )) {
            assertFalse(url, isPrivacyLanguageAnchor(url))
        }
    }
}
