package com.uacastplayer.icons

/**
 * Decides whether [com.uacastplayer.data.icons.IconFailureStore]'s permanent-failure records need
 * a one-time clear because the schema version persisted on disk is behind the current build's.
 *
 * A missing stored version (no schema version has ever been written) counts as "behind" too, not
 * just "older": an existing install upgrading to the first build with this migration at all has
 * permanent-failure records that may have been written before the icon fetch request carried a
 * browser User-Agent, wrongly blacklisting URLs that 403'd only because of that missing header.
 * A fresh install also has a missing stored version, but nothing to clear either way, so treating
 * it the same as "behind" is harmless there.
 */
object IconFailureStoreMigration {
    fun shouldClearPermanentFailures(storedVersion: Int?, currentVersion: Int): Boolean =
        storedVersion == null || storedVersion < currentVersion
}
