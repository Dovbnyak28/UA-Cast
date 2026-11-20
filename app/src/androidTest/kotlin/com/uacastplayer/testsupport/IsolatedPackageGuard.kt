package com.uacastplayer.testsupport

import org.junit.Assert.assertTrue

/** Limit state-changing fixtures to the original debug app or the dedicated Samsung QA copy. */
internal fun assertIsolatedDebugPackage(packageName: String) {
    assertTrue(
        "Device tests require an explicitly isolated debug package",
        packageName == "com.uacastplayer.debug" || packageName == "com.uacastplayer.debug.stage5qa",
    )
}
