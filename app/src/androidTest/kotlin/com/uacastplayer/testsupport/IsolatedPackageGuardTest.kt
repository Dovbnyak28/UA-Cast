package com.uacastplayer.testsupport

import org.junit.Assert.assertThrows
import org.junit.Test

class IsolatedPackageGuardTest {
    @Test fun acceptsOnlyExplicitlyNamedDebugPackages() {
        assertIsolatedDebugPackage("com.uacastplayer.debug")
        assertIsolatedDebugPackage("com.uacastplayer.debug.stage5qa")
        for (name in listOf("com.uacastplayer", "com.uacastplayer.debug.other", "com.example.debug")) {
            assertThrows(AssertionError::class.java) { assertIsolatedDebugPackage(name) }
        }
    }
}
