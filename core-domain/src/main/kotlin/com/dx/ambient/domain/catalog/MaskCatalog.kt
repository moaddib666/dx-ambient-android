package com.dx.ambient.domain.catalog

import com.dx.ambient.domain.model.Mask

/**
 * Pickable alpha masks for live scene tuning in the players: the masks bundled with the
 * app plus any images the user imported into the library. Implemented in core-data.
 */
interface MaskCatalog {
    suspend fun masks(): List<Mask>
}
