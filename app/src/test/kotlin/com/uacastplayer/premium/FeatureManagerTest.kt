package com.uacastplayer.premium

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeatureManagerTest {

    private val now = 1_800_000_000_000L

    /**
     * Every test below one runs as a build that *may* withhold - see [FeatureManager]'s
     * `withholdingIsHonest`. A plain test JVM is not one: it has neither a live store nor a
     * developer menu, so without this the manager would answer "unlocked" to everything and the
     * assertions here would be describing a build nobody ships.
     */
    @Before
    fun grantTheMenu() {
        DeveloperMode.states = listOf("TRIAL")
        DeveloperMode.apply = { _, _ -> error("not called: these tests only need the menu to exist") }
    }

    @After
    fun takeTheMenuBack() {
        DeveloperMode.states = emptyList()
        DeveloperMode.apply = null
    }

    /**
     * The guard that makes wiring the gates safe while there is nothing to sell.
     *
     * The trial is 14 days and granted exactly once, so with the gates live and no store, an
     * install that has merely existed for a fortnight would lose DLNA, multiple playlists, backups
     * and parental control with no way to buy them back. This is the assertion that stops that
     * shipping - and it runs, deliberately, *without* the developer menu the other tests fake.
     */
    @Test
    fun aBuildWithNothingToSellUnlocksEverything() {
        takeTheMenuBack()
        check(!PremiumAvailability.STORE_IS_LIVE) { "this test describes the pre-store build" }
        val (manager, _) = managerFor(License.FREE)

        for (feature in Feature.entries) {
            assertTrue("$feature must not be locked with no store", manager.isUnlocked(feature))
        }
    }

    /**
     * The whole withholding rule, every row of it.
     *
     * The row this exists for is the third: a build that was flipped live, running against a store
     * that answered with nothing. That is not a hypothetical - Play returns an ordinary empty
     * success for a product id its console does not have, so a draft product, a typo in one id, or
     * a release published before the track it belongs to reaches users as an app that has taken
     * DLNA, backups and extra playlists away and offers nothing to buy them back with. It cannot be
     * reached from a test through [FeatureManager.isUnlocked], because `STORE_IS_LIVE` is a
     * compile-time false here, which is exactly why the predicate is separable.
     */
    @Test
    fun onlyABuildThatCanAlsoGrantIsAllowedToWithhold() {
        // storeIsLive, storeCanSell, hasDeveloperMenu -> may withhold
        assertTrue(FeatureManager.mayWithhold(true, storeCanSell = true, hasDeveloperMenu = false))
        assertTrue(FeatureManager.mayWithhold(true, storeCanSell = true, hasDeveloperMenu = true))

        assertFalse(
            "a live build whose store sells nothing must not lock anything",
            FeatureManager.mayWithhold(true, storeCanSell = false, hasDeveloperMenu = false),
        )
        assertFalse(
            "no store at all: same rule, said earlier",
            FeatureManager.mayWithhold(false, storeCanSell = false, hasDeveloperMenu = false),
        )
        assertFalse(
            "storeCanSell cannot let a build with no store withhold",
            FeatureManager.mayWithhold(false, storeCanSell = true, hasDeveloperMenu = false),
        )

        // The debug menu grants by hand, so it is allowed to take away by hand too.
        assertTrue(FeatureManager.mayWithhold(false, storeCanSell = false, hasDeveloperMenu = true))
        assertTrue(FeatureManager.mayWithhold(false, storeCanSell = true, hasDeveloperMenu = true))
        assertTrue(FeatureManager.mayWithhold(true, storeCanSell = false, hasDeveloperMenu = true))
    }

    private fun managerFor(
        license: License,
        nowMillis: Long = now,
    ): Pair<FeatureManager, MutableStateFlow<Entitlements>> {
        val flow = MutableStateFlow(Entitlements.of(license, nowMillis))
        return FeatureManager(flow) to flow
    }

    @Test
    fun anUnpaidInstallCanCastButCannotAddASecondPlaylist() {
        val (manager, _) = managerFor(License.FREE)

        assertTrue(manager.isUnlocked(Feature.CHROMECAST))
        assertTrue(manager.isUnlocked(Feature.PIP))
        assertTrue(manager.isUnlocked(Feature.THEMES))

        assertFalse(manager.isUnlocked(Feature.MULTI_PLAYLIST))
        assertTrue(manager.isLocked(Feature.DLNA))
    }

    @Test
    fun aTrialUnlocksEverythingWhileItLasts() {
        val (manager, _) = managerFor(License.trialStartingAt(now))

        for (feature in Feature.entries) {
            assertTrue("$feature should be unlocked during the trial", manager.isUnlocked(feature))
        }
    }

    /** The bug this whole design is meant to make impossible: an expired entitlement still
     * unlocking things because one call site read `license.tier` instead of asking. */
    @Test
    fun anExpiredTrialUnlocksExactlyWhatFreeDoes() {
        val trial = License.trialStartingAt(now)
        val (manager, _) = managerFor(trial, nowMillis = now + License.TRIAL_DURATION_MILLIS)

        assertEquals(FeaturePolicy.featuresFor(LicenseTier.FREE), manager.entitlements.value.unlocked)
        assertFalse(manager.isUnlocked(Feature.DLNA))
        assertTrue(manager.isUnlocked(Feature.CHROMECAST))
        assertTrue(manager.entitlements.value.hasLapsed)
        assertEquals(LicenseTier.FREE, manager.entitlements.value.effectiveTier)
    }

    /** A purchase must be visible to screens that are already on screen, without anything being
     * told to refresh - which is why access is a flow rather than a function call. */
    @Test
    fun buyingWhileAScreenIsOpenChangesTheAnswerImmediately() {
        val (manager, flow) = managerFor(License.FREE)
        assertFalse(manager.isUnlocked(Feature.DLNA))

        flow.value = Entitlements.of(License(LicenseTier.YEARLY, expiresAtMillis = now + 1_000_000), now)

        assertTrue(manager.isUnlocked(Feature.DLNA))
    }

    @Test
    fun isLockedIsExactlyTheOppositeOfIsUnlocked() {
        val (manager, _) = managerFor(License.FREE)
        for (feature in Feature.entries) {
            assertEquals(feature.name, !manager.isUnlocked(feature), manager.isLocked(feature))
        }
    }

    @Test
    fun theDefaultEntitlementsGrantOnlyWhatFreeGrants() {
        assertEquals(FeaturePolicy.featuresFor(LicenseTier.FREE), Entitlements.FREE.unlocked)
        assertFalse(Entitlements.FREE.hasLapsed)
    }
}
