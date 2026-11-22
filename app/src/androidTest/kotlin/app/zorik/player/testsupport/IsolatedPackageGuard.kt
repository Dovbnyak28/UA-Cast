package app.zorik.player.testsupport

import org.junit.Assert.assertTrue

/** Limit state-changing fixtures to the original debug app or the dedicated Samsung QA copy. */
internal fun assertIsolatedDebugPackage(packageName: String) {
    assertTrue(
        "Device tests require an explicitly isolated debug package",
        packageName == "app.zorik.player.debug" || packageName == "app.zorik.player.debug.stage5qa",
    )
}
