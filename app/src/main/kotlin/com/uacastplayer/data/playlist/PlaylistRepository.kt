package com.uacastplayer.data.playlist

import android.content.Context
import android.net.Uri
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uParser
import com.uacastplayer.playlist.PlaylistLoadResult
import com.uacastplayer.playlist.PlaylistSnapshot
import com.uacastplayer.playlist.ChannelGrouper
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

sealed class PlaylistOutcome {
    data class Loaded(val groups: List<GroupedChannels>, val skippedLineCount: Int) : PlaylistOutcome()
    data object SizeLimitExceeded : PlaylistOutcome()
    data class HttpError(val code: Int) : PlaylistOutcome()
    data class ReadError(val message: String?) : PlaylistOutcome()
}

private const val TAG = "PlaylistRepository"

class PlaylistRepository(context: Context) {

    private val appContext = context.applicationContext
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val urlLoader = PlaylistUrlLoader(httpClient)
    private val fileLoader = PlaylistFileLoader(appContext)
    private val snapshotStore = PlaylistSnapshotStore(appContext)

    suspend fun loadFromUrl(url: String): PlaylistOutcome {
        val outcome = toOutcome(urlLoader.load(url))
        persistIfLoaded(outcome, sourceIdentifier = url)
        return outcome
    }

    suspend fun loadFromFile(uri: Uri): PlaylistOutcome {
        val outcome = toOutcome(fileLoader.load(uri))
        persistIfLoaded(outcome, sourceIdentifier = uri.toString())
        return outcome
    }

    suspend fun restoreSnapshot(): PlaylistOutcome? = withContext(Dispatchers.IO) {
        val snapshot = snapshotStore.load() ?: return@withContext null
        PlaylistOutcome.Loaded(ChannelGrouper.group(snapshot.channels), snapshot.skippedLineCount)
    }

    private suspend fun persistIfLoaded(outcome: PlaylistOutcome, sourceIdentifier: String) {
        if (outcome !is PlaylistOutcome.Loaded) return
        val channels = outcome.groups.flatMap { it.channels }
        val snapshot = PlaylistSnapshot(
            sourceFingerprint = Fingerprint.of(sourceIdentifier),
            savedAtEpochMillis = System.currentTimeMillis(),
            channels = channels,
            skippedLineCount = outcome.skippedLineCount,
        )
        try {
            snapshotStore.save(snapshot)
        } catch (e: Exception) {
            AppLog.w(TAG) { "Failed to persist playlist snapshot: ${e.javaClass.simpleName}" }
        }
    }

    private fun toOutcome(result: PlaylistLoadResult): PlaylistOutcome = when (result) {
        is PlaylistLoadResult.Success -> {
            val parsed = M3uParser.parse(result.text)
            PlaylistOutcome.Loaded(ChannelGrouper.group(parsed.channels), parsed.skippedLineCount)
        }
        PlaylistLoadResult.SizeLimitExceeded -> PlaylistOutcome.SizeLimitExceeded
        is PlaylistLoadResult.HttpError -> PlaylistOutcome.HttpError(result.code)
        is PlaylistLoadResult.ReadError -> PlaylistOutcome.ReadError(result.message)
    }
}
