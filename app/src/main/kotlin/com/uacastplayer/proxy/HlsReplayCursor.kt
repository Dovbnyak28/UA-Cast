package com.uacastplayer.proxy

/**
 * Stateful position in one flattened HLS replay, kept independent from HTTP and socket wiring.
 *
 * A conforming live playlist never decreases `#EXT-X-MEDIA-SEQUENCE`, but encoders do restart in
 * the field. Keeping the old sequence forever after such a restart makes every new segment look
 * already consumed and leaves the renderer on a silent connection. Resetting on the first lower
 * window is unsafe too: a CDN can briefly return one stale cached manifest. Three consecutive lower
 * windows distinguish a persistent encoder reset from that ordinary cache race without retaining
 * playlist bodies or segment data.
 */
data class HlsReplayCursor(
    val nextSequence: Long = 0,
    val rollbackObservations: Int = 0,
) {

    /** Selects what this refresh owes and returns the cursor state to retain for the next step. */
    fun select(playlist: HlsMediaPlaylist): HlsReplaySelection {
        val isRollback = playlist.nextSequenceAfter < nextSequence
        val observations = if (isRollback) rollbackObservations + 1 else 0
        val reset = observations >= ROLLBACK_CONFIRMATION_REFRESHES
        val effectiveCursor = copy(
            nextSequence = if (reset) playlist.mediaSequence else nextSequence,
            rollbackObservations = if (reset) 0 else observations,
        )
        return HlsReplaySelection(
            segmentUris = HlsFlattenPolicy.segmentsToServe(playlist, effectiveCursor.nextSequence),
            cursor = effectiveCursor,
            resetDetected = reset,
        )
    }

    /** Advances only after the selected playlist pass has been attempted. */
    fun afterServing(playlist: HlsMediaPlaylist): HlsReplayCursor = copy(
        nextSequence = HlsFlattenPolicy.sequenceAfterServing(playlist, nextSequence),
    )

    private companion object {
        const val ROLLBACK_CONFIRMATION_REFRESHES = 3
    }
}

data class HlsReplaySelection(
    val segmentUris: List<String>,
    val cursor: HlsReplayCursor,
    val resetDetected: Boolean,
)
