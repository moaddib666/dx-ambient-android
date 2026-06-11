package com.dx.ambient.youtube.featured

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturedPlaylistsTest {

    @Test
    fun `custom featured entries survive an encode-decode round trip`() {
        val custom = listOf(
            FeaturedPlaylist("PL123", "Chill", isDefault = false, thumbnailUrl = "https://t/1.jpg"),
            FeaturedPlaylist("PL456", "Focus", isDefault = false, thumbnailUrl = null),
        )

        val restored = decodeCustomFeatured(encodeCustomFeatured(custom))

        assertEquals(custom, restored)
    }

    @Test
    fun `corrupt persisted payload decodes to empty list instead of crashing`() {
        assertEquals(emptyList<FeaturedPlaylist>(), decodeCustomFeatured("{ not json"))
        assertEquals(emptyList<FeaturedPlaylist>(), decodeCustomFeatured(null))
        assertEquals(emptyList<FeaturedPlaylist>(), decodeCustomFeatured(""))
    }

    @Test
    fun `default featured playlist ships with the expected id and is locked`() {
        val default = FeaturedPlaylistsRepository.DEFAULTS.single()

        assertEquals("PLazDOSmamQaHwJRkOrRBrfB8zT4JBHpgZ", default.playlistId)
        assertTrue(default.isDefault)
    }
}
