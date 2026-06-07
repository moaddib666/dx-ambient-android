package com.dx.ambient.youtube

import com.dx.ambient.domain.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeModeTest {

    // --- isSupported / youTubeSource -------------------------------------------------

    @Test
    fun `isSupported gates on Google Play Services`() {
        assertTrue(YouTubeMode.isSupported(hasGooglePlayServices = true))
        assertFalse(YouTubeMode.isSupported(hasGooglePlayServices = false))
    }

    @Test
    fun `youTubeSource builds a YOUTUBE MediaSource preserving the raw input`() {
        val source = YouTubeMode.youTubeSource("anything")
        assertEquals("anything", source.uri)
        assertEquals(MediaSourceType.YOUTUBE, source.type)
        assertTrue(source.isYouTube)
    }

    // --- extractVideoId --------------------------------------------------------------

    @Test
    fun `extractVideoId from watch url`() {
        assertEquals("dQw4w9WgXcQ", YouTubeMode.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `extractVideoId from youtu_be short url`() {
        assertEquals("dQw4w9WgXcQ", YouTubeMode.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `extractVideoId from embed shorts live paths`() {
        assertEquals("abc123DEF45", YouTubeMode.extractVideoId("https://www.youtube.com/embed/abc123DEF45"))
        assertEquals("abc123DEF45", YouTubeMode.extractVideoId("https://www.youtube.com/shorts/abc123DEF45"))
        assertEquals("abc123DEF45", YouTubeMode.extractVideoId("https://www.youtube.com/live/abc123DEF45"))
    }

    @Test
    fun `extractVideoId stops at extra query params`() {
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeMode.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLxyz&t=30"),
        )
    }

    @Test
    fun `extractVideoId treats a bare id as the id`() {
        assertEquals("dQw4w9WgXcQ", YouTubeMode.extractVideoId("dQw4w9WgXcQ"))
        assertEquals("ab-cd_EF12", YouTubeMode.extractVideoId("ab-cd_EF12"))
    }

    @Test
    fun `extractVideoId is case-insensitive on host`() {
        assertEquals("abcDEF12345", YouTubeMode.extractVideoId("https://YOUTU.BE/abcDEF12345"))
    }

    @Test
    fun `extractVideoId returns null for a playlist-only url`() {
        assertNull(YouTubeMode.extractVideoId("https://www.youtube.com/playlist?list=PLxyz"))
    }

    @Test
    fun `extractVideoId returns null for blank input`() {
        assertNull(YouTubeMode.extractVideoId(""))
        assertNull(YouTubeMode.extractVideoId("   "))
    }

    @Test
    fun `extractVideoId returns null for bare v=ID without a query separator`() {
        // Documented edge: "v=ID" looks URL-ish (contains '=') but has no [?&] before v=.
        assertNull(YouTubeMode.extractVideoId("v=dQw4w9WgXcQ"))
    }

    // --- extractPlaylistId -----------------------------------------------------------

    @Test
    fun `extractPlaylistId from playlist url`() {
        assertEquals("PLabcDEF", YouTubeMode.extractPlaylistId("https://www.youtube.com/playlist?list=PLabcDEF"))
    }

    @Test
    fun `extractPlaylistId from watch url with list param`() {
        assertEquals("RDxyz123", YouTubeMode.extractPlaylistId("https://www.youtube.com/watch?v=ID&list=RDxyz123"))
    }

    @Test
    fun `extractPlaylistId accepts bare ids with known prefixes`() {
        assertEquals("PLabc", YouTubeMode.extractPlaylistId("PLabc"))
        assertEquals("RDabc", YouTubeMode.extractPlaylistId("RDabc"))
        assertEquals("UUabc", YouTubeMode.extractPlaylistId("UUabc"))
        assertEquals("OLabc", YouTubeMode.extractPlaylistId("OLabc"))
        assertEquals("FLabc", YouTubeMode.extractPlaylistId("FLabc"))
        assertEquals("LLabc", YouTubeMode.extractPlaylistId("LLabc"))
    }

    @Test
    fun `extractPlaylistId returns null for an unrecognized bare string`() {
        assertNull(YouTubeMode.extractPlaylistId("randomstring"))
        assertNull(YouTubeMode.extractPlaylistId(""))
    }

    @Test
    fun `extractPlaylistId matches the loose PL prefix even for a word like PLAYLIST`() {
        // Documented edge: the prefix regex is loose, so "PLAYLIST" matches the PL prefix.
        assertEquals("PLAYLIST", YouTubeMode.extractPlaylistId("PLAYLIST"))
    }
}
