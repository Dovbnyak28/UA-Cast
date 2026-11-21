package com.uacastplayer.performance

import com.uacastplayer.core.settings.BufferSize

/**
 * What this app may hold, decided from the heap it was actually given.
 *
 * **The number that governs an OutOfMemoryError is not the one this app was reading.**
 * [DevicePerformanceClassifier] scores a device from total RAM, cores and API level; the heap an
 * app is allowed to grow into is a separate, much smaller number the manufacturer sets, and
 * nothing here consulted it. A field crash makes the gap concrete: a moto e15 on Android 14 with
 * an octa-core CPU scores well on every input that classifier looks at, and its heap growth limit
 * is **128MB**. It died with `OutOfMemoryError ... MediaCodecRenderer.feedInputBuffer` - out of
 * heap while feeding the video decoder - after a diagnostics report caught it at **111MB used of
 * 128MB**.
 *
 * ## What the heap actually goes on, measured
 *
 * Built as real objects and weighed by settling the heap either side of the allocation (desktop
 * JVM, which shares ART's compressed references and 8-byte alignment closely enough for a budget,
 * not for an accounting):
 *
 * - **161,838 programmes: 7.7MB** when titles repeat as they do in a real guide (series and news
 *   recur across days and channels), **27MB** when every title is distinct. Call it ~170 bytes per
 *   programme at the pessimistic end.
 * - **3,530 channels: 1.5MB**, and 4.5MB while a load holds parse, grouping and snapshot copies at
 *   once.
 *
 * The guide is the one term large enough to matter *and* under this app's control, and it is worse
 * than its resting size suggests: a parse accumulates programmes in a list and then builds
 * `programmesByChannelId` as a second structure, so the peak is roughly twice the final figure.
 * 27MB resting is ~54MB in flight, which is what puts a 128MB heap at the 111MB that was measured.
 *
 * Coil is already bounded at 10% of the heap (see `UaCastPlayerApp`), and ExoPlayer's held media at
 * 8-24MB by [BufferSize]. Those two, the guide, and the playlist are the whole of what this app can
 * decide; the rest is Compose and the framework.
 */
object HeapBudget {

    /** Below this, every allocation decision here takes its most conservative branch. Chosen as the
     * measured device's own limit, so a heap at least as tight as the one that crashed is treated
     * as tight. */
    const val TIGHT_HEAP_BYTES = 128L * 1024 * 1024

    /** A heap this size has room for the guide and the media buffer at their full defaults. */
    private const val ROOMY_HEAP_BYTES = 320L * 1024 * 1024

    /**
     * Pessimistic per-programme cost - the all-distinct-titles measurement rather than the
     * repeating-titles one. A budget built on the cheap case is not a budget.
     */
    private const val BYTES_PER_PROGRAMME = 170

    /**
     * Share of the heap the guide may hold **at rest**. Half of what is left after Coil's 10% and a
     * large media buffer, because the parse peak is about twice the resting size (see above) and
     * that peak has to fit too.
     */
    private const val GUIDE_HEAP_SHARE = 0.15

    /**
     * Never cut below this, whatever the arithmetic says. A guide is a feature, and a device too
     * small for a useful one is better served by a thin guide than by an empty one - three days of
     * a few hundred channels lands around here. At [BYTES_PER_PROGRAMME] this is ~7MB resting,
     * which even the measured 128MB device can hold.
     */
    const val MIN_PROGRAMMES = 40_000

    /**
     * The ceiling, unchanged from what [com.uacastplayer.epg.XmlTvParser] has always used, and
     * still a backstop against a broken or hostile feed rather than an operating limit. Every heap
     * from ~450MB up gets exactly this, which is what a modern phone already had.
     */
    const val MAX_PROGRAMMES = 400_000

    /**
     * How many programmes a heap of [maxHeapBytes] may retain.
     *
     * `Runtime.getRuntime().maxMemory()` is the value to pass: on ART it reports the growth limit,
     * which is the number the OutOfMemoryError message quotes and the one the process actually dies
     * at.
     */
    fun maxProgrammes(maxHeapBytes: Long): Int {
        val affordable = (maxHeapBytes * GUIDE_HEAP_SHARE / BYTES_PER_PROGRAMME).toLong()
        return affordable.coerceIn(MIN_PROGRAMMES.toLong(), MAX_PROGRAMMES.toLong()).toInt()
    }

    /**
     * The media buffer this heap can afford, as a *default* only - the user's own choice in
     * Settings still wins, the same way the icon and density tier defaults work.
     *
     * [BufferSize.LARGE] is never returned. It exists for an unstable connection, which is a fact
     * about the network rather than the device, so it stays something a user opts into.
     *
     * The step from MEDIUM to SMALL is 16MB of held media down to 8MB. On the measured device that
     * is 8MB back on a heap that was 17MB from its limit, taken from the exact allocation the crash
     * happened inside.
     */
    fun defaultBufferSize(maxHeapBytes: Long): BufferSize =
        if (maxHeapBytes < ROOMY_HEAP_BYTES) BufferSize.SMALL else BufferSize.MEDIUM

    /** Whether this heap is tight enough to be worth saying so in a diagnostics report - the
     * question "why did this device run out of memory" is otherwise answered by a number nobody
     * collected. */
    fun isTight(maxHeapBytes: Long): Boolean = maxHeapBytes <= TIGHT_HEAP_BYTES
}
