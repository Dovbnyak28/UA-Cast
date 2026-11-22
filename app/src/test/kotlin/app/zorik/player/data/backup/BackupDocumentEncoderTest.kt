package app.zorik.player.data.backup

import app.zorik.player.backup.BackupCodec
import app.zorik.player.backup.BackupData
import app.zorik.player.backup.BackupFavorite
import app.zorik.player.backup.BackupJsonInputGuard
import app.zorik.player.backup.BackupPlaylistSource
import app.zorik.player.backup.BackupSettings
import app.zorik.player.core.security.Fingerprint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupDocumentEncoderTest {
    private val payload = "I0VYVE0zVQo="
    private val favorite = BackupFavorite(
        "canonical-key", "Україна España 😀 \"quoted\"\n\t\u0001".repeat(512), "https://provider.test/live?token=test",
        "id", "Новини", Long.MAX_VALUE, "TV name", "https://pack.test/logo.png", "Agent/1.0", "https://test/",
    )
    private val data = BackupData(
        listOf(
            BackupPlaylistSource("local", "FILE", "content://old/document", "Локальний", 42,
                payload, Fingerprint.of(payload)),
            BackupPlaylistSource("url", "URL", "https://provider.test/list?token=test", "Remote", Long.MAX_VALUE),
        ),
        listOf(favorite),
        BackupSettings(
            "DEFAULT", "COMPACT", "SMALL", "legacy-id", "https://epg.test/guide?token=test", epgDisabled = true,
        ),
    )

    @Test fun streamedJsonPreservesEverySchemaFieldAndUnicode() {
        val bytes = requireNotNull(BackupDocumentEncoder.encode(data, Job()))
        assertEquals(data, BackupCodec.decode(bytes.toString(Charsets.UTF_8)))
        assertTrue(BackupJsonInputGuard.accepts(bytes.toString(Charsets.UTF_8)))
    }

    @Test fun theLimitCountsUtf8BytesAndAcceptsTheExactBoundary() {
        val expected = requireNotNull(BackupDocumentEncoder.encode(data, Job()))
        val text = expected.toString(Charsets.UTF_8)
        assertTrue("Fixture must exercise multibyte characters", expected.size > text.length)
        assertTrue("Fixture must exercise a larger valid backup", expected.size > 16 * 1024)
        assertArrayEquals(expected, BackupDocumentEncoder.encode(data, Job(), maxBytes = expected.size))
        assertNull(BackupDocumentEncoder.encode(data, Job(), maxBytes = expected.size - 1))
        assertNull(BackupDocumentEncoder.encode(data, Job(), maxBytes = 0))
    }

    @Test fun oversizeRefusalStopsBeforeTraversingTheRemainingFavorites() {
        var visited = 0
        val favorites = object : AbstractList<BackupFavorite>() {
            override val size = 4_000
            override fun get(index: Int): BackupFavorite {
                visited++
                return favorite.copy(displayName = "x".repeat(8192))
            }
        }
        assertNull(BackupDocumentEncoder.encode(data.copy(favorites = favorites), Job(), maxBytes = 1024))
        assertTrue("Encoding must stop at the byte cap, visited $visited rows", visited < 100)
    }

    @Test fun theImportObjectBudgetIsCheckedBeforeAllocatingAnyRows() {
        var visited = 0
        val favorites = object : AbstractList<BackupFavorite>() {
            override val size = BackupJsonInputGuard.MAX_OBJECTS
            override fun get(index: Int): BackupFavorite { visited++; return favorite }
        }
        assertNull(BackupDocumentEncoder.encode(data.copy(favorites = favorites), Job()))
        assertEquals(0, visited)
    }

    @Test fun anAlreadyCancelledOwnerIsNotConvertedToAnEncodingFailure() {
        val owner = Job().apply { cancel() }
        assertThrows(CancellationException::class.java) { BackupDocumentEncoder.encode(data, owner) }
    }

    @Test fun cancellationDuringEncodingStopsWithoutProducingPartialJson() {
        val owner = Job()
        var visited = 0
        val favorites = object : AbstractList<BackupFavorite>() {
            override val size = 4_000
            override fun get(index: Int): BackupFavorite {
                visited++
                owner.cancel()
                return favorite.copy(displayName = "x".repeat(8192))
            }
        }
        assertThrows(CancellationException::class.java) {
            BackupDocumentEncoder.encode(data.copy(favorites = favorites), owner)
        }
        assertTrue("Cancellation must not scan the remaining rows", visited < 100)
    }
}
