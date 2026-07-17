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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val TAG = "SsdpDiscovery"
private const val SSDP_ADDRESS = "239.255.255.250"
private const val SSDP_PORT = 1900
private const val SEARCH_TARGET = "urn:schemas-upnp-org:service:AVTransport:1"
private const val DISCOVERY_WINDOW_SECONDS = 3
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

    /** Blocks the calling thread for about [DISCOVERY_WINDOW_SECONDS] seconds - call from a background dispatcher. */
    fun discover(): List<DlnaDevice> {
        val multicastLock = acquireMulticastLock()
        return try {
            collectLocations().mapNotNull(::fetchDevice)
        } finally {
            multicastLock?.let { if (it.isHeld) it.release() }
        }
    }

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
        val deadline = System.currentTimeMillis() + DISCOVERY_WINDOW_SECONDS * 1000L
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
        if (!response.isSuccessful) return null
        val body = response.body?.byteStream() ?: return null
        return when (val result = BoundedByteReader.readBytes(body, MAX_DEVICE_DESCRIPTION_BYTES)) {
            is BoundedBytesResult.Success -> DeviceDescriptionParser.parse(result.bytes, location)
            BoundedBytesResult.SizeLimitExceeded -> null
        }
    }

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
