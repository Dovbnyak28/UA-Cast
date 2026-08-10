package com.uacastplayer.dlna

import android.content.Context
import android.net.wifi.WifiManager
import com.uacastplayer.core.io.BoundedByteReader
import com.uacastplayer.core.io.BoundedBytesResult
import com.uacastplayer.log.AppLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val TAG = "SsdpDiscovery"
private const val SSDP_ADDRESS = "239.255.255.250"
private const val SSDP_PORT = 1900
private const val SEARCH_MX_SECONDS = 2

/**
 * Measured from the *first* datagram sent, not the last. Devices spread their replies randomly over
 * [SEARCH_MX_SECONDS] to avoid answering in one burst, and the sends themselves are spaced out (see
 * [SEND_SPACING_MILLIS]), so the window has to cover the send sweep plus a full MX after it.
 */
private const val DISCOVERY_WINDOW_SECONDS = 4
private const val SEARCH_REPEATS_PER_TARGET = 2

/** Back-to-back multicast sends are a burst the Wi-Fi driver may itself drop, which would defeat
 * the point of repeating them. */
private const val SEND_SPACING_MILLIS = 100L

/** `ssdp:all` answers for every device on the LAN - routers, printers, speakers - and each distinct
 * LOCATION costs an HTTP fetch. Renderers that matter are found long before this many; the cap
 * exists so a noisy network cannot turn one tap into dozens of requests. */
private const val MAX_LOCATIONS = 32

private const val MILLIS_PER_SECOND = 1000L
private const val RECEIVE_POLL_TIMEOUT_MILLIS = 500
private const val MAX_DEVICE_DESCRIPTION_BYTES = 256 * 1024
private const val RECEIVE_BUFFER_BYTES = 4096
private const val MULTICAST_LOCK_TAG = "UACastPlayer:dlnaDiscovery"

/**
 * SSDP M-SEARCH discovery for `urn:schemas-upnp-org:service:AVTransport:1` renderers, plus the
 * follow-up device-description fetch that turns a LOCATION url into a [DlnaDevice]. This class is
 * the real socket/HTTP I/O; header and XML parsing are delegated to [SsdpResponseParser] and
 * [DeviceDescriptionParser] so those stay unit-testable without a network.
 *
 * SSDP responses to an M-SEARCH come back as plain unicast UDP to the sender's ephemeral port, so
 * a normal [DatagramSocket] is enough - no [java.net.MulticastSocket] needed. The
 * [WifiManager.MulticastLock] is still acquired for the duration of the search, mirroring the
 * acquire-before/release-after discipline [com.uacastplayer.data.cast.CastWakeLocks] uses for the
 * proxy session's power/Wi-Fi locks.
 */
class SsdpDiscovery(context: Context, private val httpClient: OkHttpClient) {

    private val appContext = context.applicationContext

    /**
     * Takes at least [DISCOVERY_WINDOW_SECONDS] (the fixed M-SEARCH listen window), plus one
     * device-description fetch.
     *
     * The fetches run concurrently rather than one after another: each is an independent HTTP round
     * trip to a different device, capped at [DEVICE_DESCRIPTION_TIMEOUT_SECONDS] by the client, so
     * serially a LAN with several renderers - or one unresponsive TV that burns its whole timeout -
     * added that timeout again per device to a sheet the user is watching spin. Concurrently the
     * whole batch costs the slowest single device.
     */
    suspend fun discover(): List<DlnaDevice> {
        val multicastLock = acquireMulticastLock()
        return try {
            val locations = withContext(Dispatchers.IO) { collectLocations() }
            val devices = coroutineScope {
                locations.map { location -> async(Dispatchers.IO) { fetchDevice(location) } }.awaitAll()
            }.filterNotNull().distinctRenderers()
            // The other half of the empty-sheet diagnosis: locations that answered but yielded no
            // usable renderer are the normal ssdp:all result (routers, printers, speakers), so this
            // pair of numbers says whether the LAN is silent or simply has nothing to cast to.
            AppLog.d(TAG) { "DLNA discovery: ${devices.size} renderer(s) from ${locations.size} location(s)" }
            devices
        } finally {
            multicastLock?.let { if (it.isHeld) it.release() }
        }
    }

    /** Discovery is best-effort by nature: a network that refuses the multicast send, or a socket
     * the system tears down mid-search, means "no devices found this time", never a crash. There is
     * no narrower type that covers all of that here - the same block spans socket setup, send, and
     * receive. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun collectLocations(): List<String> {
        val locations = LinkedHashSet<String>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = RECEIVE_POLL_TIMEOUT_MILLIS
                val deadline = System.currentTimeMillis() + DISCOVERY_WINDOW_SECONDS * MILLIS_PER_SECOND
                sendSearchRequests(socket)
                receiveResponsesUntilDeadline(socket, deadline, locations)
            }
        } catch (e: Exception) {
            AppLog.w(TAG) { "SSDP discovery failed: ${e.javaClass.simpleName}" }
        }
        // Logged even (especially) when empty: an empty device sheet is the symptom users report,
        // and without this there is no way to tell "nothing answered the multicast" from "several
        // devices answered but none of them is a renderer".
        AppLog.d(TAG) { "SSDP search finished: ${locations.size} distinct location(s)" }
        return locations.take(MAX_LOCATIONS)
    }

    /** Sends every payload [SsdpSearchRequests] defines - several targets, each repeated. A send
     * that fails on one datagram must not abandon the rest: a network can refuse the multicast
     * momentarily and still answer the next one. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendSearchRequests(socket: DatagramSocket) {
        val address = InetAddress.getByName(SSDP_ADDRESS)
        val payloads = SsdpSearchRequests.payloads(
            address = SSDP_ADDRESS,
            port = SSDP_PORT,
            mx = SEARCH_MX_SECONDS,
            repeats = SEARCH_REPEATS_PER_TARGET,
        )
        var sent = 0
        for (payload in payloads) {
            val message = payload.toByteArray(Charsets.UTF_8)
            try {
                socket.send(DatagramPacket(message, message.size, address, SSDP_PORT))
                sent++
            } catch (e: Exception) {
                AppLog.w(TAG) { "M-SEARCH send failed: ${e.javaClass.simpleName}" }
            }
            // delay, not Thread.sleep: this is the first of two places a dismissed sheet has to be
            // able to stop the search - see receiveResponsesUntilDeadline for the other.
            delay(SEND_SPACING_MILLIS)
        }
        AppLog.d(TAG) { "M-SEARCH sent: $sent of ${payloads.size} datagram(s)" }
    }

    /**
     * Blocks on the socket in [RECEIVE_POLL_TIMEOUT_MILLIS] slices rather than for the whole
     * window, which is what makes the search abandonable.
     *
     * Cancelling a coroutine does not interrupt a thread parked in `DatagramSocket.receive`, so
     * without the `isActive` check every dismissed sheet still held an IO thread, a UDP socket and
     * a multicast lock for the full [DISCOVERY_WINDOW_SECONDS]. Opening and closing the sheet
     * repeatedly stacked those up: `Dispatchers.IO` runs 64 threads, and searches that nobody was
     * waiting for any more could take all of them - starving the playlist load, the EPG parse, the
     * icon fetches and the cast proxy, none of which have anything to do with DLNA.
     */
    private suspend fun receiveResponsesUntilDeadline(
        socket: DatagramSocket,
        deadline: Long,
        locations: MutableSet<String>,
    ) {
        val buffer = ByteArray(RECEIVE_BUFFER_BYTES)
        while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadline) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                SsdpResponseParser.parse(text).location?.let { locations += it }
            } catch (_: SocketTimeoutException) {
                // Expected: soTimeout just lets us re-check the overall deadline periodically.
            }
        }
    }

    /** One unreachable or misbehaving renderer must not lose the whole discovery result - a device
     * whose description cannot be fetched is simply dropped from the list (see the mapNotNull in
     * [discover]), which is why anything thrown here degrades to null rather than propagating. */
    @Suppress("TooGenericExceptionCaught")
    private fun fetchDevice(location: String): DlnaDevice? {
        val request = Request.Builder().url(location).build()
        return try {
            httpClient.newCall(request).execute().use { response -> parseDeviceDescription(response, location) }
        } catch (e: Exception) {
            AppLog.w(TAG) { "Device description fetch failed for $location: ${e.javaClass.simpleName}" }
            null
        }
    }

    private fun parseDeviceDescription(response: Response, location: String): DlnaDevice? {
        val body = response.body?.byteStream()?.takeIf { response.isSuccessful } ?: return null
        return when (val result = BoundedByteReader.readBytes(body, MAX_DEVICE_DESCRIPTION_BYTES)) {
            is BoundedBytesResult.Success -> DeviceDescriptionParser.parse(result.bytes, location)
            BoundedBytesResult.SizeLimitExceeded -> null
        }
    }

    /** The lock is an optimisation, not a requirement: without it the search still runs, it just
     * may miss replies the Wi-Fi hardware filtered out. So a device that refuses to hand one over -
     * for any reason - degrades to searching without it rather than failing discovery outright. */
    @Suppress("TooGenericExceptionCaught")
    private fun acquireMulticastLock(): WifiManager.MulticastLock? = try {
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    } catch (e: Exception) {
        AppLog.w(TAG) { "Failed to acquire multicast lock: ${e.javaClass.simpleName}" }
        null
    }
}
