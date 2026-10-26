package com.uacastplayer.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalVideoTest {
    @Test fun aConcreteProviderVideoTypeIsNotOverriddenByAMisleadingDisplayName() {
        assertEquals("video/x-matroska", LocalVideoFormats.mimeType("Alias.mp4", "video/x-matroska"))
        assertEquals("video/webm", LocalVideoFormats.mimeType("Alias.avi", "video/webm; codecs=vp9"))
        assertEquals("video/mp4", LocalVideoFormats.mimeType("Movie.mp4", "video/*"))
    }

    @Test fun localHlsAliasesArePlaylistsNotFiniteVideoFiles() {
        listOf("video/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl").forEach {
            assertNull(it, LocalVideoFormats.mimeType("playlist.m3u8", it))
            assertNull(it, LocalVideoFormats.mimeType("misleading.mp4", it))
        }
    }
    @Test fun commonAndLegacyContainersHaveHonestMimeTypes() {
        assertEquals("video/mp4", LocalVideoFormats.mimeType("Фільм.MP4", "application/octet-stream"))
        assertEquals("video/x-matroska", LocalVideoFormats.mimeType("Movie.mkv", null))
        assertEquals("video/x-msvideo", LocalVideoFormats.mimeType("Movie.avi", null))
        assertEquals("video/webm", LocalVideoFormats.mimeType("Movie.webm", null))
        assertEquals("video/x-ms-wmv", LocalVideoFormats.mimeType("Movie.wmv", null))
    }

    @Test fun unnamedVideoCanUseTheProvidersMimeButOrdinaryDocumentsCannot() {
        assertEquals("video/mp4", LocalVideoFormats.mimeType("Video", "video/mp4; codecs=avc1"))
        assertNull(LocalVideoFormats.mimeType("notes.txt", "text/plain"))
        assertNull(LocalVideoFormats.mimeType("playlist.m3u8", "application/vnd.apple.mpegurl"))
        assertNull(LocalVideoFormats.mimeType("image.jpg", "image/jpeg"))
    }

    @Test fun providerMimeCannotInjectHeadersOrGrowAStateBundle() {
        assertNull(LocalVideoFormats.mimeType("Video", "video/mp4\r\nInjected: yes"))
        assertNull(LocalVideoFormats.mimeType("Video", "video/" + "a".repeat(1000)))
        assertEquals("video/mp4", LocalVideoFormats.mimeType("Movie.mp4", "text/html\r\nInjected: yes"))
    }

    @Test fun onlyExplicitContentDocumentsAreEligible() {
        listOf("file:///private/movie.mp4", "http://server/movie.mp4", "content:///movie", "content://user@authority/a")
            .forEach { assertThrows(Exception::class.java) { LocalVideo(it, "Movie", "video/mp4") } }
        assertEquals("Фільм.mp4", LocalVideo("content://media/video/1", "Фільм.mp4", "video/mp4").displayName)
    }

    @Test fun metadataIsBoundedAndAHeaderInjectionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalVideo("content://media/" + "a".repeat(5000), "Movie", "video/mp4")
        }
        assertThrows(IllegalArgumentException::class.java) { LocalVideo("content://media/1", "", "video/mp4") }
        assertThrows(IllegalArgumentException::class.java) {
            LocalVideo("content://media/1", "Movie", "video/mp4\r\nInjected: yes")
        }
    }
}
