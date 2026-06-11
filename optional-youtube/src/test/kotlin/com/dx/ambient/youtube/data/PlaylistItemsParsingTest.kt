package com.dx.ambient.youtube.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistItemsParsingTest {

    @Test
    fun `parses video ids and next page token`() {
        val body = """
            {
              "kind": "youtube#playlistItemListResponse",
              "nextPageToken": "CAUQAA",
              "items": [
                {"contentDetails": {"videoId": "abc123DEF-_"}},
                {"contentDetails": {"videoId": "xyz789"}},
                {"contentDetails": {}},
                {}
              ]
            }
        """.trimIndent()

        val (ids, next) = parsePlaylistItemIds(body)

        assertEquals(listOf("abc123DEF-_", "xyz789"), ids)
        assertEquals("CAUQAA", next)
    }

    @Test
    fun `last page has no next token`() {
        val body = """{"items": [{"contentDetails": {"videoId": "only1"}}]}"""

        val (ids, next) = parsePlaylistItemIds(body)

        assertEquals(listOf("only1"), ids)
        assertNull(next)
    }

    @Test
    fun `empty playlist parses to empty list`() {
        val (ids, next) = parsePlaylistItemIds("""{"items": []}""")

        assertEquals(emptyList<String>(), ids)
        assertNull(next)
    }

    @Test
    fun `unknown fields are ignored`() {
        val body = """
            {
              "etag": "tag",
              "pageInfo": {"totalResults": 1},
              "items": [{"id": "pi-1", "contentDetails": {"videoId": "vid42", "startAt": "0"}}]
            }
        """.trimIndent()

        val (ids, _) = parsePlaylistItemIds(body)

        assertEquals(listOf("vid42"), ids)
    }
}
