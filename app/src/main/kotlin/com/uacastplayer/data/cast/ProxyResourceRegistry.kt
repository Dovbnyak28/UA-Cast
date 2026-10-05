package com.uacastplayer.data.cast

import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.core.net.HttpDefaults
import com.uacastplayer.core.net.HttpHeaderValuePolicy
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.proxy.RemuxHandoffPolicy
import kotlin.concurrent.thread
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Response

private const val MAX_RESOURCES = 512

internal const val RESOURCE_TYPE_PLAYLIST = "playlist"
internal const val RESOURCE_TYPE_MEDIA = "media"

/** [userAgent] always has a value (defaulted to [HttpDefaults.BROWSER_USER_AGENT] at registration
 * time if the channel didn't specify one via `#EXTVLCOPT:http-user-agent=`); [referrer] is only
 * ever set from that same per-channel override. Resources discovered while rewriting a playlist
 * (segments, sub-playlists, key URIs) inherit both from their parent - see [ProxyServer.servePlaylist]. */
internal data class ResourceEntry(
    val type: String,
    val originalUrl: String,
    val userAgent: String,
    val referrer: String?,
)

/**
 * The proxy's per-session state that outlives any single request: the resource map (every
 * playlist/media URL discovered, keyed by its URL/type/access headers via [Fingerprint], so
 * re-registering the same request is idempotent), LRU-bounded to [MAX_RESOURCES] entries - plus the
 * "one active remux stream per session" handoff (docs/PROXY_RULES.md): a channel switch stops the
 * replaced session's upstream reader immediately, while keeping its completed buffer servable for
 * a grace period (see [beginDraining]/[RemuxHandoffPolicy]).
 */
internal class ProxyResourceRegistry(private val httpClient: OkHttpClient) {

    private companion object {
        const val RESOURCE_MAP_INITIAL_CAPACITY = 16
        const val RESOURCE_MAP_LOAD_FACTOR = 0.75f
    }

    private val resources = object : LinkedHashMap<String, ResourceEntry>(
        RESOURCE_MAP_INITIAL_CAPACITY,
        RESOURCE_MAP_LOAD_FACTOR,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResourceEntry>?): Boolean =
            size > MAX_RESOURCES
    }
    private val resourcesLock = Any()
    private val manifests = ProxyManifestResources()

    private var activeRemuxSession: RawTsRemuxSession? = null
    private var drainingRemuxSession: RawTsRemuxSession? = null
    private var drainTimerThread: Thread? = null
    private val remuxLock = Any()
    private var remuxOwner: RemuxRequestLease? = null

    fun registerPlaylist(url: String, userAgent: String?, referrer: String?): String {
        val entry = ResourceEntry(
            RESOURCE_TYPE_PLAYLIST, url,
            HttpHeaderValuePolicy.userAgentOrDefault(userAgent), HttpHeaderValuePolicy.sanitize(referrer),
        )
        return synchronized(remuxLock) {
            manifests.beginRoot(entry)
            val id = entry.resourceId()
            if (manifests.isActive(id) && remuxOwner?.rootId != id) {
                remuxOwner = RemuxRequestLease(id)
                // Release the old origin BEFORE fetching the next channel: a provider allowing
                // one connection may reject B while A is still open. Keep A's completed segments.
                activeRemuxSession?.let(::beginDraining)
                activeRemuxSession = null
            }
            register(entry.type, entry.originalUrl, entry.userAgent, entry.referrer)
        }
    }

    private fun register(type: String, url: String, userAgent: String, referrer: String?): String {
        // Same URL with different provider credentials/headers is a different resource. Otherwise
        // an old receiver request silently acquires the newest channel's access requirements.
        val entry = ResourceEntry(type, url, userAgent, referrer)
        val id = entry.resourceId()
        synchronized(resourcesLock) { resources[id] = entry }
        return id
    }

    fun get(resourceId: String): ResourceEntry? = manifests.get(resourceId)
        ?: synchronized(resourcesLock) { resources[resourceId] }

    fun rewriteManifest(
        text: String, finalUrl: String, parent: ResourceEntry, lease: RemuxRequestLease?, localUrl: (String) -> String,
    ): String? = synchronized(remuxLock) {
        // An old A response may finish after A -> B -> A. Parent id equality alone would let it
        // publish old segment URLs into the renewed graph. Validate and publish in one transaction.
        if (lease == null || lease !== remuxOwner) null else manifests.rewrite(text, finalUrl, parent, localUrl)
    }

    fun openSession() = synchronized(remuxLock) {
        remuxOwner = null
        manifests.openSession()
    }

    /** Capture before fetching/sniffing upstream, never after a delayed callback or unwrap. */
    fun captureRemuxLease(resourceId: String): RemuxRequestLease? = synchronized(remuxLock) {
        remuxOwner?.takeIf { manifests.isActive(resourceId) }
    }

    /** Exposed only so tests can verify header inheritance (see [ProxyServer.servePlaylist])
     * without going through a real socket. */
    fun snapshot(): Map<String, ResourceEntry> =
        synchronized(resourcesLock) { resources.toMap() } + manifests.snapshot()

    /** Discards every registered resource and stops any active/draining remux session -
     * appropriate when the whole proxy server is stopping, not a mid-session channel switch. */
    fun clearAll() {
        synchronized(remuxLock) {
            remuxOwner = null
            synchronized(resourcesLock) { resources.clear() }
            manifests.clear()
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

    /** Validates the pre-fetch lease atomically with publishing a producer. A running server may
     * already belong to another session or channel; only the current root generation may replace
     * its active remux. Stale responses are closed without starting a reader or stopping the new
     * channel. Existing draining segments remain readable through [remuxSessionFor]. */
    fun startRemuxSession(
        resourceId: String,
        response: Response,
        lease: RemuxRequestLease?,
        segmentUrl: (resourceId: String, sequence: Int) -> String,
        isServerRunning: () -> Boolean,
    ): RawTsRemuxSession? {
        val session = RawTsRemuxSession(resourceId, response, httpClient, segmentUrl)
        val selected = synchronized(remuxLock) {
            val ownsCurrentRoot = lease != null && lease === remuxOwner
            if (!ownsCurrentRoot || !manifests.isActive(resourceId) || !isServerRunning()) {
                runCatchingNonFatal { response.close() }
                null
            } else {
                val previous = activeRemuxSession
                // HEAD+GET (and some renderers, two GETs) can race through the initial upstream
                // sniff before either request has installed a reusable remux session. The first
                // request owns the live reader. Replacing it with an identical second resource
                // stops the first and lets its waiting handler return an empty manifest. Reuse it;
                // the redundant upstream response has no owner and must be closed here.
                if (previous != null && previous.resourceId == resourceId && !previous.hasEnded) {
                    runCatchingNonFatal { response.close() }
                    previous
                } else {
                    activeRemuxSession = session
                    if (previous != null && previous.resourceId != resourceId) {
                        beginDraining(previous)
                    } else {
                        previous?.stop()
                    }
                    session
                }
            }
        }
        if (selected === session) session.start()
        return selected
    }

    /** See [RemuxHandoffPolicy]: keeps [previous]'s buffered window servable for a grace period so
     * an in-flight request for the channel just switched away from doesn't turn a successful switch
     * into a visible glitch. Its upstream reader is stopped immediately: draining serves bytes
     * already promised by its last playlist, it must not keep growing a second 48 MiB buffer and
     * consuming a second IPTV connection beside the new active session. Only one session ever
     * drains at a time - an older draining session is discarded when a newer replacement lands. */
    private fun beginDraining(previous: RawTsRemuxSession) {
        drainingRemuxSession?.stop()
        previous.stop()
        drainingRemuxSession = previous
        drainTimerThread?.interrupt() // the session it was timing is stopped just above
        val startedAtNanos = System.nanoTime()
        drainTimerThread = thread(name = "ProxyServer-drain-${previous.resourceId}") {
            // An interrupt (stop(), or a newer drain replacing this one) just means "check now
            // instead of later" - the state checks below already handle every such case as a no-op.
            try {
                Thread.sleep(RemuxHandoffPolicy.DRAIN_TIMEOUT_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            synchronized(remuxLock) {
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
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
        // Returning to A must fetch a fresh producer, not poll the frozen A left in draining.
        drainingRemuxSession?.takeIf { it.resourceId == resourceId && !manifests.isActive(resourceId) }
    }
}

internal fun ResourceEntry.resourceId(): String = Fingerprint.of(
    listOf(type, originalUrl, userAgent, referrer.orEmpty()).joinToString(":") { "${it.length}:$it" },
)
