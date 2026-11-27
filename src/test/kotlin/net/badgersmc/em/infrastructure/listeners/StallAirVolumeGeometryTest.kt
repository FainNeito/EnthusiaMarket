package net.badgersmc.em.infrastructure.listeners

import kotlin.test.Test
import kotlin.test.assertEquals

class StallAirVolumeGeometryTest {
    @Test
    fun `merges a rectangular air volume into one cuboid`() {
        val blocks = buildList {
            for (x in 0..1) for (y in 2..3) for (z in 4..6) add(StallAirBlock(x, y, z))
        }

        assertEquals(listOf(StallRegionBox(0, 2, 4, 1, 3, 6)), StallAirVolumeGeometry.cuboids(blocks))
    }

    @Test
    fun `keeps sloped roof layers separate without adding roof air`() {
        val blocks = buildList {
            add(StallAirBlock(0, 2, 0))
            add(StallAirBlock(0, 3, 0))
            add(StallAirBlock(1, 2, 0))
        }

        assertEquals(
            setOf(StallRegionBox(0, 2, 0, 1, 2, 0), StallRegionBox(0, 3, 0, 0, 3, 0)),
            StallAirVolumeGeometry.cuboids(blocks).toSet(),
        )
    }

    @Test
    fun `does not bridge solid gaps or disconnected layers`() {
        val blocks = listOf(StallAirBlock(0, 2, 0), StallAirBlock(0, 4, 0), StallAirBlock(2, 4, 0))

        assertEquals(
            setOf(StallRegionBox(0, 2, 0, 0, 2, 0), StallRegionBox(0, 4, 0, 0, 4, 0), StallRegionBox(2, 4, 0, 2, 4, 0)),
            StallAirVolumeGeometry.cuboids(blocks).toSet(),
        )
    }
}
