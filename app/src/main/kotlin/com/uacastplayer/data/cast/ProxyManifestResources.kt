package com.uacastplayer.data.cast

import com.uacastplayer.proxy.M3u8Rewriter
import com.uacastplayer.proxy.HlsPlaylistBudget

/**
 * URLs promised by an HLS manifest are leases, not disposable LRU entries. Retain the current
 * and previous window of each reachable manifest (master, video, audio, keys), plus one draining
 * channel. Publish an entire rewrite atomically or reject it before sending any URLs. The graph
 * and character budgets bound even a hostile feed; increasing the ordinary LRU is not necessary.
 */
internal class ProxyManifestResources {
    private data class Window(val current: Map<String, ResourceEntry>, val previous: Map<String, ResourceEntry>)
    private var root: ResourceEntry? = null
    private var windows = mapOf<String, Window>()
    private var active = mapOf<String, ResourceEntry>()
    private var draining = mapOf<String, ResourceEntry>()
    private var accepting = true

    @Synchronized fun beginRoot(entry: ResourceEntry) {
        if (!accepting || root == entry) return
        draining = active
        root = entry
        windows = emptyMap()
        active = mapOf(entry.resourceId() to entry)
    }

    @Synchronized fun get(id: String): ResourceEntry? = active[id] ?: draining[id]
    @Synchronized fun snapshot(): Map<String, ResourceEntry> = draining + active

    @Synchronized fun clear() {
        accepting = false
        root = null
        windows = emptyMap()
        active = emptyMap()
        draining = emptyMap()
    }

    @Synchronized fun openSession() {
        clear()
        accepting = true
    }

    @Synchronized fun rewrite(
        text: String,
        finalUrl: String,
        parent: ResourceEntry,
        localUrl: (String) -> String,
    ): String? {
        if (root == null) beginRoot(parent)
        val parentId = parent.resourceId()
        // A late old-channel response cannot publish into the new channel's graph.
        if (!accepting || parentId !in active) return null
        val entries = linkedMapOf<String, ResourceEntry>()
        var chars = 0L
        return try {
            val budget = RewriteBudget(text)
            val rewritten = M3u8Rewriter.rewrite(text, finalUrl) { url ->
                budget.claimReference()
                val type = if (url.substringBefore('?').endsWith(".m3u8", true) ||
                    url.substringBefore('?').endsWith(".m3u", true)
                ) RESOURCE_TYPE_PLAYLIST else RESOURCE_TYPE_MEDIA
                val entry = ResourceEntry(type, url, parent.userAgent, parent.referrer)
                val id = entry.resourceId()
                if (id !in entries) {
                    chars += entry.retainedChars()
                    if (entries.size >= MAX_ENTRIES || chars > MAX_CHARS) throw BudgetExceeded()
                    entries[id] = entry
                }
                budget.claimReplacement(localUrl(id))
            }
            val existing = windows[parentId]
            val previous = existing?.current.orEmpty()
            val window = if (existing != null && previous == entries) existing else Window(entries, previous)
            val candidate = windows + (parentId to window)
            val reachable = reachableGraph(candidate, checkNotNull(root))
            if (reachable == null) null else {
                active = reachable
                windows = candidate.filterKeys { it in reachable }
                rewritten
            }
        } catch (_: BudgetExceeded) {
            null
        }
    }

    private fun reachableGraph(candidate: Map<String, Window>, rootEntry: ResourceEntry): Map<String, ResourceEntry>? {
        val result = linkedMapOf(rootEntry.resourceId() to rootEntry)
        val pending = ArrayDeque<String>().apply { add(rootEntry.resourceId()) }
        val visited = hashSetOf<String>()
        var chars = rootEntry.retainedChars()
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            val window = candidate[id]?.takeIf { visited.add(id) } ?: continue
            for ((childId, entry) in window.previous + window.current) {
                if (childId !in result) {
                    chars += entry.retainedChars()
                    if (result.size >= MAX_ENTRIES || chars > MAX_CHARS) return null
                    result[childId] = entry
                }
                if (childId in candidate && childId !in visited) pending.add(childId)
            }
        }
        return result
    }

    private class BudgetExceeded : RuntimeException()

    /** Bounds transient rewrite work independently of the number of distinct retained resources. */
    private class RewriteBudget(text: String) {
        private var references = 0
        private var outputChars = text.length.toLong()

        init {
            if (!HlsPlaylistBudget.accepts(text)) throw BudgetExceeded()
        }

        fun claimReference() {
            if (++references > MAX_REFERENCE_OCCURRENCES) throw BudgetExceeded()
        }

        fun claimReplacement(replacement: String): String {
            // Conservative upper bound: keep the original text AND every replacement. A raw
            // relative URI can be much shorter than the absolute URL passed to the callback.
            // Reject before joinToString appends this replacement, not after creating the result.
            outputChars += replacement.length
            if (outputChars > MAX_REWRITTEN_CHARS) throw BudgetExceeded()
            return replacement
        }
    }

    companion object {
        const val MAX_ENTRIES = 20_000
        const val MAX_CHARS = 4L * 1024 * 1024
        const val MAX_REFERENCE_OCCURRENCES = HlsPlaylistBudget.MAX_REFERENCES
        const val MAX_MANIFEST_LINES = HlsPlaylistBudget.MAX_LINES
        const val MAX_REWRITTEN_CHARS = 8 * 1024 * 1024
    }
}

private fun ResourceEntry.retainedChars(): Long =
    type.length.toLong() + originalUrl.length + userAgent.length + (referrer?.length ?: 0)
