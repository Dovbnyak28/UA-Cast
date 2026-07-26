package com.uacastplayer.data.cast

import com.uacastplayer.core.net.HttpDefaults
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.proxy.RemuxHandoffPolicy
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import okhttp3.Response

private const val MAX_RESOURCES = 512

internal const val RESOURCE_TYPE_PLAYLIST = "playlist"
internal const val RESOURCE_TYPE_MEDIA = "media"

/** [userAgent] always has a value (defaulted to [HttpDefaults.BROWSER_USER_AGENT] at registration
 * time if the channel didn't specify one via `#EXTVLCOPT:http-user-agent=`); [referrer] is only
 * ever set from that same per-channel override. Resources discovered while rewriting a playlist
 * (segments, sub-playlists, key URIs) inherit both from their parent - see [ProxyServer.servePlaylist]. */
internal data class ResourceEntry(val type: String, val originalUrl: String, val userAgent: String, val referrer: String?)

/**
 * The proxy's per-session state that outlives any single request: the resource map (every
 * playlist/media URL discovered, keyed by a stable `SHA-256("type:url")` id via [Fingerprint], so
 * re-registering the same URL is idempotent), LRU-bounded to [MAX_RESOURCES] entries - plus the
 * "one active remux stream per session" handoff (docs/PROXY_RULES.md): a channel switch replaces
 * the active [RawTsRemuxSession] but keeps the replaced one servable for a grace period (see
 * [beginDraining]/[RemuxHandoffPolicy]) instead of stopping it the instant it's replaced.
 */
internal class ProxyResourceRegistry(private val httpClient: OkHttpClient) {

    private val resources = object : LinkedHashMap<String, ResourceEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResourceEntry>?): Boolean =
            size > MAX_RESOURCES
    }
    private val resourcesLock = Any()

    private var activeRemuxSession: RawTsRemuxSession? = null
    private var drainingRemuxSession: RawTsRemuxSession? = null
    private var drainTimerThread: Thread? = null
    private val remuxLock = Any()

    fun registerPlaylist(url: String, userAgent: String?, referrer: String?): String = register(
        RESOURCE_TYPE_PLAYLIST,
        url,
        userAgent?.ifBlank { null } ?: HttpDefaults.BROWSER_USER_AGENT,
        referrer?.ifBlank { null },
    )

    fun register(type: String, url: String, userAgent: String, referrer: String?): String {
        val id = Fingerprint.of("$type:$url")
        synchronized(resourcesLock) { resources[id] = ResourceEntry(type, url, userAgent, referrer) }
        return id
    }

    fun get(resourceId: String): ResourceEntry? = synchronized(resourcesLock) { resources[resourceId] }

    /** Exposed only so tests can verify header inheritance (see [ProxyServer.servePlaylist])
     * without going through a real socket. */
    fun snapshot(): Map<String, ResourceEntry> = synchronized(resourcesLock) { resources.toMap() }

    /** Discards every registered resource and stops any active/draining remux session -
     * appropriate when the whole proxy server is stopping, not a mid-session channel switch. */
    fun clearAll() {
        synchronized(resourcesLock) { resources.clear() }
        synchronized(remuxLock) {
            activeRemuxSession?.stop()
            activeRemuxSession = null
            drainingRemuxSession?.stop()
            drainingRemuxSession = null
            // Wake a pending drain timer (see beginDraining) so it exits now instead of sleeping
            // out the rest of its grace period against a server that no longer exists.
            drainTimerThread?.interrupt()
            drainTimerThread = null
        }
    }

    /** Called once the new channel's load is confirmed to have succeeded on the receiver (see
     * `cast/CastSessionRepository`) - lets a still-draining previous remux session be torn down
     * right away instead of waiting out the rest of [RemuxHandoffPolicy.DRAIN_TIMEOUT_MILLIS]. A
     * harmless no-op when nothing is draining. */
    fun confirmActiveSession() {
        synchronized(remuxLock) {
            val draining = drainingRemuxSession ?: return@synchronized
            if (RemuxHandoffPolicy.shouldKillDraining(confirmed = true, elapsedMillis = 0)) {
                draining.stop()
                drainingRemuxSession = null
                drainTimerThread?.interrupt()
                drainTimerThread = null
            }
        }
    }

    /** Returns null - with [response] closed - if [isServerRunning] says the server was stopped
     * while this request's upstream fetch was in flight. Without that check, a connection handler
     * racing a stop() (the receiver polls its playlist right as the cast session ends) could
     * install and start a fresh reader session *after* stop() already cleared everything, leaving
     * a background thread reading the upstream live stream indefinitely with no owner left to
     * ever stop it. Both this check and [clearAll] run under [remuxLock], so whichever runs second
     * sees the other's effect. */
    fun startRemuxSession(
        resourceId: String,
        response: Response,
        segmentUrl: (resourceId: String, sequence: Int) -> String,
        isServerRunning: () -> Boolean,
    ): RawTsRemuxSession? {
        val session = RawTsRemuxSession(resourceId, response, httpClient, segmentUrl)
        synchronized(remuxLock) {
            if (!isServerRunning()) {
                runCatching { response.close() }
                return null
            }
            val previous = activeRemuxSession
            activeRemuxSession = session
            if (previous != null && previous.resourceId != resourceId) beginDraining(previous) else previous?.stop()
        }
        session.start()
        return session
    }

    /** See [RemuxHandoffPolicy]: keeps [previous] servable for a grace period instead of stopping
     * it the instant it's replaced, so an in-flight request for the channel just switched away from
     * doesn't turn a successful switch into a visible glitch. Only one session ever drains at a
     * time - a session already draining when a newer replacement lands has waited long enough. */
    private fun beginDraining(previous: RawTsRemuxSession) {
        drainingRemuxSession?.stop()
        drainingRemuxSession = previous
        drainTimerThread?.interrupt() // the session it was timing is stopped just above
        val startedAt = System.currentTimeMillis()
        drainTimerThread = thread(name = "ProxyServer-drain-${previous.resourceId}") {
            // An interrupt (stop(), or a newer drain replacing this one) just means "check now
            // instead of later" - the state checks below already handle every such case as a no-op.
            try {
                Thread.sleep(RemuxHandoffPolicy.DRAIN_TIMEOUT_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            synchronized(remuxLock) {
                val elapsed = System.currentTimeMillis() - startedAt
                val stillDraining = drainingRemuxSession === previous
                val expired = RemuxHandoffPolicy.shouldKillDraining(confirmed = false, elapsedMillis = elapsed)
                if (stillDraining && expired) {
                    previous.stop()
                    drainingRemuxSession = null
                }
            }
        }
    }

    /** Which session a *segment* fetch is served from - dead or alive, since a dead session's
     * already-buffered segments are still perfectly servable. Playlist polls use
     * [remuxSessionForPlaylist] instead, which is stricter about dead sessions. */
    fun remuxSessionFor(resourceId: String): RawTsRemuxSession? = synchronized(remuxLock) {
        activeRemuxSession?.takeIf { it.resourceId == resourceId }
            ?: drainingRemuxSession?.takeIf { it.resourceId == resourceId }
    }

    /** Which session a *playlist* poll is served from, or null to fall through to a fresh upstream
     * fetch (and thus a fresh remux session - see [startRemuxSession]). The distinction that
     * matters is WHICH slot a dead session (see [RawTsRemuxSession.hasEnded]) occupies:
     * - a dead ACTIVE session reads as absent, so the poll restarts it - its playlist is frozen
     *   forever, and serving it would make every [com.uacastplayer.cast.CastRecoveryPolicy] reload
     *   of this same URL a guaranteed failure;
     * - a DRAINING session is served as-is even when dead: it's the channel just switched away
     *   from, and falling through for it would hand the OLD channel's resourceId to
     *   [startRemuxSession], which would then demote (and within the drain window kill) the LIVE
     *   active session of the CURRENT channel. A frozen playlist is exactly what a drained-out
     *   channel is allowed to end on. */
    fun remuxSessionForPlaylist(resourceId: String): RawTsRemuxSession? = synchronized(remuxLock) {
        val active = activeRemuxSession?.takeIf { it.resourceId == resourceId }
        if (active != null) return@synchronized active.takeIf { !it.hasEnded }
        drainingRemuxSession?.takeIf { it.resourceId == resourceId }
    }
}
