package com.uacastplayer.premium

import kotlinx.coroutines.flow.StateFlow

/**
 * The one question the rest of the app is allowed to ask about access: *is this unlocked?*
 *
 * It knows nothing about billing, stores, purchases or prices - only about the current
 * [Entitlements]. That is what makes it testable with a value rather than a mock, and what keeps
 * the answer identical no matter where the entitlement came from: a real purchase, a trial, a
 * cached license read while offline, or the debug menu.
 *
 * There is deliberately no `isPremium` on this type. A boolean invites call sites to reason about
 * tiers themselves, which is exactly the sprawl this layer exists to prevent - the caller wants to
 * know whether *this* thing works, and nothing more.
 */
class FeatureManager(private val source: StateFlow<Entitlements>) {

    /** Current access, for UI that renders a badge or a lock and needs to recompose when it moves. */
    val entitlements: StateFlow<Entitlements> get() = source

    /** Whether [feature] can be used right now. */
    fun isUnlocked(feature: Feature): Boolean = feature in source.value.unlocked

    /** Whether [feature] is behind a paywall for this user - i.e. locked, and not merely absent.
     * The distinction the UI needs: a lock badge belongs on the first, nothing on the second. */
    fun isLocked(feature: Feature): Boolean = !isUnlocked(feature)
}
