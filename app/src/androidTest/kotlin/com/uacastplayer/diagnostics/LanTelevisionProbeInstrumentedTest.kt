package com.uacastplayer.diagnostics

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.dlna.SsdpDiscovery
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in, read-only device inventory. Never sends Play/Stop or connects to a debug service. */
@RunWith(AndroidJUnit4::class)
class LanTelevisionProbeInstrumentedTest {
    @Test fun discoverTelevisionsOnThePhoneNetwork(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("LAN probing requires explicit device-test opt-in",
            InstrumentationRegistry.getArguments().getString("lanTelevisionProbe") == "true")
        val context = instrumentation.targetContext.applicationContext
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        try {
            val renderers = withTimeout(12_000) { SsdpDiscovery(context, client).discover() }
            report("DLNA renderers: ${renderers.size}")
            renderers.forEach { report("DLNA ${it.friendlyName}: ${it.controlUrl}") }
            val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            listOf("_googlecast._tcp.", "_androidtvremote2._tcp.", "_adb-tls-connect._tcp.")
                .forEach { discoverServices(nsd, it) }
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun discoverServices(nsd: NsdManager, type: String) {
        val found = ConcurrentLinkedQueue<NsdServiceInfo>()
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { started.countDown() }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) { found.add(serviceInfo) }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) { stopped.countDown() }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                report("mDNS $type start failed: $errorCode")
                started.countDown()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { stopped.countDown() }
        }
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        try {
            started.await(2, TimeUnit.SECONDS)
            // No Activity or UI-thread work: the instrumentation worker gives NSD a bounded window.
            CountDownLatch(1).await(3, TimeUnit.SECONDS)
            report("mDNS $type services: ${found.size}")
            found.distinctBy { it.serviceName }.take(8).forEach { resolveService(nsd, it) }
        } finally {
            nsd.stopServiceDiscovery(listener)
            stopped.await(2, TimeUnit.SECONDS)
        }
    }

    @Suppress("DEPRECATION") // Listener API also supports the project's minSdk 24 devices.
    private fun resolveService(nsd: NsdManager, service: NsdServiceInfo) {
        val completed = CountDownLatch(1)
        nsd.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                report("mDNS ${service.serviceName}: resolve failed $errorCode")
                completed.countDown()
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                report("mDNS ${serviceInfo.serviceName}: ${serviceInfo.host?.hostAddress}:${serviceInfo.port}")
                completed.countDown()
            }
        })
        if (!completed.await(2, TimeUnit.SECONDS)) report("mDNS ${service.serviceName}: resolve timeout")
    }

    private fun report(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0,
            android.os.Bundle().apply { putString("stream", "\nLAN_PROBE $message\n") })
    }
}
