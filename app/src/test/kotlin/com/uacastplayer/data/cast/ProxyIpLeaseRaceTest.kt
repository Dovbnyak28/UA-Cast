package com.uacastplayer.data.cast

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function
import java.util.function.BiFunction
import org.junit.Assert.assertEquals
import org.junit.Test

/** Force a legitimate release between map lookup and CAS without changing production code. */
class ProxyIpLeaseRaceTest {
    @Test fun `last release racing admission cannot detach the new IP lease from its limit`() {
        val server = ProxyHttpServer { _, _ -> }
        val countersField = ProxyHttpServer::class.java.getDeclaredField("connectionsPerIp").apply {
            isAccessible = true
        }
        val acquire = ProxyHttpServer::class.java.getDeclaredMethod("acquireIpSlot", String::class.java).apply {
            isAccessible = true
        }
        val release = ProxyHttpServer::class.java.declaredMethods.single { it.name == "releaseIpSlot" }.apply {
            isAccessible = true
        }
        val counters = LookupBarrierMap()
        countersField.set(server, counters)
        val previous = acquire.invoke(server, "127.0.0.1")!!
        counters.afterLookup = { release.invoke(server, previous) }
        val admitted = acquire.invoke(server, "127.0.0.1")!!
        counters.afterLookup = null
        // An accept-loop thread can be descheduled here while the old response worker releases.
        // A lease returned successfully must still count against this IP's live quota.
        val accepted = mutableListOf(admitted)
        repeat(8) { acquire.invoke(server, "127.0.0.1")?.let(accepted::add) }
        try {
            assertEquals("per-IP maximum is 8, but an orphaned live lease bypassed it", 8, accepted.size)
            assertEquals("all accepted connections must remain accounted for",
                accepted.size, counters["127.0.0.1"]?.get())
        } finally {
            accepted.forEach { release.invoke(server, it) }
        }
    }

    private class LookupBarrierMap : ConcurrentHashMap<String, AtomicInteger>() {
        var afterLookup: (() -> Unit)? = null

        override fun computeIfAbsent(
            key: String, mappingFunction: Function<in String, out AtomicInteger>,
        ): AtomicInteger {
            val found = super.computeIfAbsent(key, mappingFunction)
            afterLookup?.invoke()
            return found
        }

        override fun compute(
            key: String,
            remappingFunction: BiFunction<in String, in AtomicInteger?, out AtomicInteger?>,
        ): AtomicInteger? {
            // With an atomic compute there is no lookup/increment gap. Release can only happen
            // before or after that transaction; still exercise it instead of keeping the old slot.
            afterLookup?.invoke()
            return super.compute(key, remappingFunction)
        }
    }
}
