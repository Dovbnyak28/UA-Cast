package com.uacastplayer.data.cast

import java.security.MessageDigest

/** Parsed, authorized target shared by admission and serving so the two cannot disagree. */
internal sealed interface ProxyRequestTarget {
    val resourceId: String

    data class Resource(override val resourceId: String) : ProxyRequestTarget

    data class RemuxSegment(
        override val resourceId: String,
        val segmentPathPart: String,
    ) : ProxyRequestTarget

    companion object {
        fun parse(path: String, expectedSessionToken: String): ProxyRequestTarget? {
            val pathOnly = path.substringBefore('?')
            val segments = pathOnly.split('/')
            val validPartCount = segments.size in RESOURCE_PART_COUNT..SEGMENT_PART_COUNT
            val validPath = validPartCount &&
                segments.firstOrNull() == "" &&
                segments.drop(1).none(String::isEmpty) &&
                segments[HLS_PART_INDEX] == HLS_PART &&
                tokensEqual(segments[TOKEN_PART_INDEX], expectedSessionToken)
            if (!validPath) return null

            val resourceId = segments[RESOURCE_PART_INDEX]
            return if (segments.size == RESOURCE_PART_COUNT) {
                Resource(resourceId)
            } else {
                RemuxSegment(resourceId, segments[SEGMENT_PART_INDEX])
            }
        }

        /** Constant-time so a client fishing for the session token cannot learn it from how
         * quickly a wrong prefix is rejected. */
        private fun tokensEqual(candidate: String, expected: String): Boolean =
            MessageDigest.isEqual(
                candidate.toByteArray(Charsets.UTF_8),
                expected.toByteArray(Charsets.UTF_8),
            )

        private const val HLS_PART = "hls"
        private const val HLS_PART_INDEX = 1
        private const val TOKEN_PART_INDEX = 2
        private const val RESOURCE_PART_INDEX = 3
        private const val SEGMENT_PART_INDEX = 4
        private const val RESOURCE_PART_COUNT = 4
        private const val SEGMENT_PART_COUNT = 5
    }
}
