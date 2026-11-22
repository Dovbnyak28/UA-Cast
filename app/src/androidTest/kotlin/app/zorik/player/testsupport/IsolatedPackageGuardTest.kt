package app.zorik.player.testsupport

import org.junit.Assert.assertThrows
import org.junit.Test

class IsolatedPackageGuardTest {
    @Test fun acceptsOnlyExplicitlyNamedDebugPackages() {
        assertIsolatedDebugPackage("app.zorik.player.debug")
        assertIsolatedDebugPackage("app.zorik.player.debug.stage5qa")
        for (name in listOf("app.zorik.player", "app.zorik.player.debug.other", "com.example.debug")) {
            assertThrows(AssertionError::class.java) { assertIsolatedDebugPackage(name) }
        }
    }
}
