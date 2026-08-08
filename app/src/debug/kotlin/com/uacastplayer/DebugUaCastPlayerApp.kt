package com.uacastplayer

import com.uacastplayer.data.premium.DeveloperModeBillingProvider
import com.uacastplayer.premium.DeveloperMode

/**
 * The debug build's [android.app.Application], named by `src/debug/AndroidManifest.xml`, whose only
 * job beyond the real one is to fill in [DeveloperMode].
 *
 * This is what makes the developer menu a *build-variant* fact rather than a runtime condition.
 * Nothing in `src/main` knows how to grant a license; it only knows that something might have
 * offered to. In a release build this class does not exist, the manifest names
 * [UaCastPlayerApp] directly, [DeveloperMode.states] stays empty, and Settings renders no
 * developer section because there is nothing to render.
 */
class DebugUaCastPlayerApp : UaCastPlayerApp() {

    override fun onCreate() {
        super.onCreate()
        DeveloperMode.states = DeveloperModeBillingProvider.STATES
        DeveloperMode.apply = DeveloperModeBillingProvider::apply
    }
}
