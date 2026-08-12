package com.uacastplayer.data.epg

/**
 * Why a guide did not load, in one short line fit for a log and for the diagnostics report.
 *
 * [EpgOutcome] has always carried the diagnosis - an HTTP code, an exception class, a size refusal -
 * and [com.uacastplayer.app.EpgController] collapsed all three into `hasError = true` and logged
 * nothing at all. A field report from a real device showed what that costs: its headline line read
 * `EPG: not loaded (source custom)` and no part of the report, or of the log beneath it, could say
 * whether the server was down, the address wrong, or the feed too big. The one question the report
 * raised was the one question it could not answer.
 *
 * Every string here is built from a code, a class name or a constant - never from an exception
 * message, which for an Xtream feed would carry the user's credentials into a shared file.
 */
object EpgFailureReason {

    private const val BYTES_PER_MB = 1024 * 1024

    /** Null for a guide that loaded - there is nothing to explain. */
    fun of(outcome: EpgOutcome): String? = when (outcome) {
        is EpgOutcome.Loaded -> null
        is EpgOutcome.HttpError -> "the server answered HTTP ${outcome.code}"
        is EpgOutcome.ReadError -> "could not be downloaded (${outcome.cause ?: "unknown error"})"
        EpgOutcome.SizeLimitExceeded ->
            "the feed is larger than the ${EpgDownloader.MAX_EPG_BYTES / BYTES_PER_MB}MB download limit"
    }
}
