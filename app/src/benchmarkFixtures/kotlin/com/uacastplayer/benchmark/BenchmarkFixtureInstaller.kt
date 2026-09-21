package com.uacastplayer.benchmark

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.core.i18n.AppLanguage
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.data.writeSafely
import com.uacastplayer.epg.EpgChannel
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgIndex
import com.uacastplayer.epg.EpgProgramme
import com.uacastplayer.epg.EpgSnapshotCodec
import com.uacastplayer.epg.EpgSnapshotHeader
import com.uacastplayer.guidedtour.GuidedTourVersion
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistSnapshot
import com.uacastplayer.playlist.PlaylistSnapshotCodec
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceCodec
import com.uacastplayer.playlist.PlaylistSourceType
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Generates synthetic, credential-free state using the same codecs production restores. */
internal object BenchmarkFixtureInstaller {

    private const val TAG = "BenchmarkFixture"
    private const val SOURCE_ID = "benchmark-fixture-source-v1"
    private const val SOURCE_URL = "https://benchmark.invalid/playlist.m3u"
    private const val EPG_URL = "https://benchmark.invalid/guide.xml"
    private const val PROFILE_CHANNELS = 400
    private const val STRESS_CHANNELS = 40_000
    private const val PROFILE_GROUP_SIZE = 100
    private const val STRESS_GROUP_SIZE = 1_000
    private const val EPG_CHANNELS = 1_000
    private const val EPG_PROGRAMMES_PER_CHANNEL = 72
    private const val XML_PROGRAMMES_PER_CHANNEL = 350
    private const val XML_CHANNELS = 1_000
    private const val MINUTES_PER_PROGRAMME = 10L

    fun installUiState(context: Context, stress: Boolean) {
        val nowMillis = System.currentTimeMillis()
        val channelCount = if (stress) STRESS_CHANNELS else PROFILE_CHANNELS
        val groupSize = if (stress) STRESS_GROUP_SIZE else PROFILE_GROUP_SIZE
        val channels = List(channelCount) { index -> playlistChannel(index, groupSize) }
        val source = PlaylistSource(
            id = SOURCE_ID,
            type = PlaylistSourceType.URL,
            location = SOURCE_URL,
            displayName = "Benchmark playlist",
            addedAtEpochMillis = nowMillis,
        )

        writeAtomic(context, "playlist_snapshot_$SOURCE_ID.bin", "benchmark playlist snapshot") { output ->
            PlaylistSnapshotCodec.encode(
                PlaylistSnapshot(SOURCE_ID, nowMillis, channels, skippedLineCount = 0, sourceUrl = SOURCE_URL),
                output,
            )
        }
        writeAtomic(context, "playlist_sources.bin", "benchmark playlist sources") { output ->
            PlaylistSourceCodec.encode(listOf(source), output)
        }
        writeEpgSnapshot(context, nowMillis, minOf(channelCount, EPG_CHANNELS))
        configurePreferences(context)
    }

    fun prepareEpgParseDocument(context: Context) {
        val zoneId = ZoneId.systemDefault()
        val firstStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").withZone(zoneId)
        val slots = List(XML_PROGRAMMES_PER_CHANNEL) { index ->
            val start = firstStart.plus(Duration.ofMinutes(index * MINUTES_PER_PROGRAMME))
            formatter.format(start) to formatter.format(start.plus(Duration.ofMinutes(MINUTES_PER_PROGRAMME)))
        }
        writeAtomic(
            context,
            BenchmarkFixtureContract.EPG_STRESS_FILE,
            "benchmark XMLTV stress document",
        ) { output ->
            // Do not close this wrapper: AtomicFile.finishWrite owns the underlying stream and
            // must sync/close it after the callback returns. Closing here would make that commit
            // operate on an already-closed FileOutputStream.
            val writer = OutputStreamWriter(output, Charsets.UTF_8).buffered()
            writer.append("<tv>\n")
            repeat(XML_CHANNELS) { channelIndex ->
                val id = epgId(channelIndex)
                writer.append("<channel id=\"").append(id).append("\"><display-name>")
                    .append(channelName(channelIndex)).append("</display-name></channel>\n")
            }
            repeat(XML_CHANNELS) { channelIndex ->
                val id = epgId(channelIndex)
                slots.forEachIndexed { slotIndex, (start, stop) ->
                    writer.append("<programme start=\"").append(start)
                        .append("\" stop=\"").append(stop)
                        .append("\" channel=\"").append(id)
                        .append("\"><title>Benchmark Programme ")
                        .append(slotIndex.toString()).append("</title></programme>\n")
                }
            }
            writer.append("</tv>\n")
            writer.flush()
        }
    }

    private fun writeEpgSnapshot(context: Context, savedAtMillis: Long, channelCount: Int) {
        val zoneId = ZoneId.systemDefault()
        val firstStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val channels = List(channelCount) { index ->
            EpgChannel(epgId(index), listOf(channelName(index)), iconUrl = null)
        }
        val programmes = channels.associate { channel ->
            channel.id to List(EPG_PROGRAMMES_PER_CHANNEL) { slot ->
                val start = firstStart + Duration.ofMinutes(slot * 30L).toMillis()
                EpgProgramme(
                    channelId = channel.id,
                    startMillis = start,
                    stopMillis = start + Duration.ofMinutes(30).toMillis(),
                    title = "Benchmark Programme ${slot.toString().padStart(3, '0')}",
                )
            }
        }
        writeAtomic(context, "epg_snapshot.bin", "benchmark EPG snapshot") { output ->
            EpgSnapshotCodec.encode(
                EpgSnapshotHeader(Fingerprint.of(EPG_URL), savedAtMillis),
                EpgData(EpgIndex(channels), programmes),
                output,
            )
        }
    }

    private fun configurePreferences(context: Context) {
        AppPreferences(context).apply {
            language = AppLanguage.ENGLISH
            hasAcceptedTerms = true
            guidedTourCompleted = true
            guidedTourVersion = GuidedTourVersion.CURRENT
            activePlaylistSourceId = SOURCE_ID
            customEpgUrl = EPG_URL
            hasChosenEpgSource = true
            hasSeenIconTierHint = true
            iconWifiOnly = true
        }
    }

    private fun writeAtomic(
        context: Context,
        fileName: String,
        description: String,
        write: (OutputStream) -> Unit,
    ) {
        val written = AtomicFile(File(context.filesDir, fileName)).writeSafely(TAG, description, write)
        check(written) { "$description could not be written" }
    }

    private fun playlistChannel(index: Int, groupSize: Int) = M3uChannel(
        displayName = channelName(index),
        streamUrl = "http://127.0.0.1:9/benchmark/$index.ts",
        tvgId = epgId(index),
        tvgName = channelName(index),
        groupTitle = "Benchmark Group ${((index / groupSize) + 1).toString().padStart(2, '0')}",
    )

    private fun channelName(index: Int) =
        "Benchmark Channel ${(index + 1).toString().padStart(CHANNEL_INDEX_WIDTH, '0')}"

    private fun epgId(index: Int) =
        "benchmark-${(index + 1).toString().padStart(CHANNEL_INDEX_WIDTH, '0')}"

    private const val CHANNEL_INDEX_WIDTH = 5
}
