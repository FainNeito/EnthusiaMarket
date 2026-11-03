package net.badgersmc.em.interaction.gui

import net.badgersmc.em.domain.ports.RegionProvider.RegionBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuctionRegionSizeTest {
    @Test fun `inclusive negative coordinates use width length height and bounding volume`() {
        val bounds = RegionBounds(-7, 120, -9, -3, 129, -5)
        assertEquals(AuctionRegionSize(5, 5, 10, 250), AuctionRegionSize.from(bounds))
    }

    @Test fun `single block has one block on every axis`() {
        assertEquals(AuctionRegionSize(1, 1, 1, 1), AuctionRegionSize.from(RegionBounds(0, 0, 0, 0, 0, 0)))
    }

    @Test fun `unavailable and inverted bounds are not presented as dimensions`() {
        assertNull(AuctionRegionSize.from(null))
        assertNull(AuctionRegionSize.from(RegionBounds(2, 0, 0, 1, 0, 0)))
        assertNull(AuctionRegionSize.from(RegionBounds(0, 2, 0, 0, 1, 0)))
        assertNull(AuctionRegionSize.from(RegionBounds(0, 0, 2, 0, 0, 1)))
    }

    @Test fun `large spans do not wrap and overflowing volume is unavailable`() {
        assertEquals(AuctionRegionSize(4_294_967_296L, 1, 1, 4_294_967_296L),
            AuctionRegionSize.from(RegionBounds(Int.MIN_VALUE, 0, 0, Int.MAX_VALUE, 0, 0)))
        assertNull(AuctionRegionSize.from(RegionBounds(Int.MIN_VALUE, 0, Int.MIN_VALUE,
            Int.MAX_VALUE, 0, Int.MAX_VALUE)))
    }
}
