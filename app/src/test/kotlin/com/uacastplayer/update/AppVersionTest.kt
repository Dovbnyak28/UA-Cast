package com.uacastplayer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    private fun version(raw: String): AppVersion =
        AppVersion.parse(raw) ?: error("expected '$raw' to parse")

    /**
     * The whole reason this class exists. As strings `"0.10.0" < "0.9.0"`, so a check built on
     * string comparison keeps working through 0.1 -> 0.9 and then stops offering updates forever,
     * without an error anywhere.
     */
    @Test
    fun twoDigitComponentsCompareNumericallyNotLexically() {
        assertTrue(version("0.10.0") > version("0.9.0"))
        assertTrue(version("1.0.0") > version("0.99.99"))
        assertTrue(version("2.10.3") > version("2.9.11"))
    }

    @Test
    fun missingTrailingComponentsCountAsZero() {
        assertEquals(0, version("0.9").compareTo(version("0.9.0")))
        assertEquals(0, version("0.9.0").compareTo(version("0.9.0.0")))
        assertTrue(version("0.9.1") > version("0.9"))
    }

    /** CI appends its run number to the version name (`0.9.0.42`, see docs/RELEASING.md), so an
     * installed CI build must read as newer than the plain release tag it was built from - and
     * still older than the next release. */
    @Test
    fun ciBuildSuffixIsNewerThanItsReleaseButOlderThanTheNext() {
        assertTrue(version("0.9.0.42") > version("0.9.0"))
        assertTrue(version("0.9.0.42") < version("0.9.1"))
        assertTrue(version("0.9.0.42") < version("0.10.0"))
    }

    @Test
    fun leadingVIsAcceptedBecauseThatIsHowTagsAreWritten() {
        assertEquals(0, version("v0.9.0").compareTo(version("0.9.0")))
        assertEquals(0, version("V1.2.3").compareTo(version("1.2.3")))
    }

    /** Semver's rule, and it matters in the one direction that would annoy: a device on the final
     * 1.0.0 must never be offered 1.0.0-rc2 as an "update". */
    @Test
    fun preReleaseSortsBelowTheSameVersionWithoutOne() {
        assertTrue(version("1.0.0") > version("1.0.0-rc2"))
        assertTrue(version("1.0.0-rc2") > version("1.0.0-rc1"))
        assertTrue(version("1.0.1-rc1") > version("1.0.0"))
    }

    @Test
    fun whitespaceAroundATagIsTolerated() {
        assertEquals(0, version("  v1.4.0  ").compareTo(version("1.4.0")))
    }

    /**
     * Null, never a default of 0.0.0. A tag nobody can parse must not read as an ancient version -
     * that would make every unparseable release look like an update to be offered to everyone.
     */
    @Test
    fun unparseableInputIsNullRatherThanZero() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("   "))
        assertNull(AppVersion.parse("v"))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1..2"))
        assertNull(AppVersion.parse("1.2.x"))
        assertNull(AppVersion.parse("1.-2.0"))
        assertNull(AppVersion.parse("release-2026"))
    }

    @Test
    fun equalVersionsCompareEqualInBothDirections() {
        assertEquals(0, version("1.2.3").compareTo(version("1.2.3")))
        assertEquals(0, version("1.2.3-beta").compareTo(version("1.2.3-beta")))
    }
}
