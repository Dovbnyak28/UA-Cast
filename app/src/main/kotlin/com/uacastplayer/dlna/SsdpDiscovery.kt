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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val TAG = "SsdpDiscovery"
private const val SSDP_ADDRESS = "239.255.255.250"
private const val SSDP_PORT = 1900
private const val SEARCH_TARGET = "urn:schemas-upnp-org:service:AVTransport:1"
private const val DISCOVERY_WINDOW_SECONDS = 3
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
            coroutineScope {
                locations.map { location -> async(Dispatchers.IO) { fetchDevice(location) } }.awaitAll()
            }.filterNotNull()
        } finally {
            multicastLock?.let { if (it.isHeld) it.release() }
        }
    }

    /** Discovery is best-effort by nature: a network that refuses the multicast send, or a socket
     * the system tears down mid-search, means "no devices found this time", never a crash. There is
     * no narrower type that covers all of that here - the same block spans socket setup, send, and
     * receive. */
    @Suppress("TooGenericExceptionCaught")
    private fun collectLocations(): List<String> {
        val locations = LinkedHashSet<String>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = RECEIVE_POLL_TIMEOUT_MILLIS
                sendSearchRequest(socket)
                receiveResponsesUntilDeadline(socket, locations)
            }
        } catch (e: Exception) {
            AppLog.w(TAG) { "SSDP discovery failed: ${e.javaClass.simpleName}" }
        }
        return locations.toList()
    }

    private fun sendSearchRequest(socket: DatagramSocket) {
        val message = searchRequest().toByteArray(Charsets.UTF_8)
        val address = InetAddress.getByName(SSDP_ADDRESS)
        socket.send(DatagramPacket(message, message.size, address, SSDP_PORT))
    }

    private fun receiveResponsesUntilDeadline(socket: DatagramSocket, locations: MutableSet<String>) {
        val deadline = System.currentTimeMillis() + DISCOVERY_WINDOW_SECONDS * MILLIS_PER_SECOND
        val buffer = ByteArray(RECEIVE_BUFFER_BYTES)
        while (System.currentTimeMillis() < deadline) {
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

    private fun searchRequest(): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: $DISCOVERY_WINDOW_SECONDS\r\n" +
            "ST: $SEARCH_TARGET\r\n\r\n"

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
