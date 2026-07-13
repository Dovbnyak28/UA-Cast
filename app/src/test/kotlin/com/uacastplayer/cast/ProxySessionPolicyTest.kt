package com.uacastplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class ProxySessionPolicyTest {

    @Test
    fun `proxy started means start the foreground service`() {
        assertEquals(
            ProxyServiceCommand.StartForeground,
            ProxySessionPolicy.commandFor(ProxyLifecycleEvent.STARTED),
        )
    }

    @Test
    fun `proxy stopped means stop the foreground service`() {
        assertEquals(
            ProxyServiceCommand.StopForeground,
            ProxySessionPolicy.commandFor(ProxyLifecycleEvent.STOPPED),
        )
    }
}
