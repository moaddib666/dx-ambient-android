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

class MaskOverridesTest {

    @org.junit.Test
    fun `mask overrides survive an encode-decode round trip`() {
        val map = mapOf("PL1" to "file:///android_asset/masks/generic.png", "PL2" to "")
        org.junit.Assert.assertEquals(map, decodeMaskOverrides(encodeMaskOverrides(map)))
    }

    @org.junit.Test
    fun `corrupt mask override payload decodes to empty map`() {
        org.junit.Assert.assertEquals(emptyMap<String, String>(), decodeMaskOverrides("{ bad"))
        org.junit.Assert.assertEquals(emptyMap<String, String>(), decodeMaskOverrides(null))
    }

    @org.junit.Test
    fun `default featured playlist ships with the shared generic mask`() {
        org.junit.Assert.assertEquals(
            FeaturedPlaylistsRepository.GENERIC_MASK_URI,
            FeaturedPlaylistsRepository.DEFAULTS.single().maskUri,
        )
    }

    @org.junit.Test
    fun `custom entry maskUri survives persistence round trip`() {
        val custom = listOf(
            FeaturedPlaylist("PL9", "Calm", isDefault = false, thumbnailUrl = null, maskUri = "file:///m.png"),
        )
        org.junit.Assert.assertEquals(custom, decodeCustomFeatured(encodeCustomFeatured(custom)))
    }
}
