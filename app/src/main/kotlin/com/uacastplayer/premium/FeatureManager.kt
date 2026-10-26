package com.uacastplayer.premium

import kotlinx.coroutines.flow.StateFlow

/** Every screen uses the same resolved Lite/Premium entitlement. Store availability affects
 * checkout, never the features a licence grants; cached paid access survives an offline launch. */
class FeatureManager(private val source: StateFlow<Entitlements>) {
    val entitlements: StateFlow<Entitlements> get() = source

    fun isUnlocked(feature: Feature): Boolean = feature in source.value.unlocked

    fun isLocked(feature: Feature): Boolean = !isUnlocked(feature)
}
