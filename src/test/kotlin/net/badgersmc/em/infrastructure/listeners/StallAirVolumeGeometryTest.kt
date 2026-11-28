package net.badgersmc.em.infrastructure.listeners

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StallAirVolumeGeometryTest {
    @Test
    fun `unions independent floor outlines including an upper floor overhang`() {
        val lower = StallRegionLayer(mutableListOf(
            StallRegionPoint(0, 0), StallRegionPoint(1, 0), StallRegionPoint(1, 1), StallRegionPoint(0, 1),
        ), floorY = 0)
        val upper = StallRegionLayer(mutableListOf(
            StallRegionPoint(2, 0), StallRegionPoint(3, 0), StallRegionPoint(3, 1), StallRegionPoint(2, 1),
        ), floorY = 3)

        val selected = StallAirVolumeScanner.scan(listOf(lower, upper), topY = 6, maxBlocks = 100,
            contains = { layer, x, z -> x in layer.points.minOf(StallRegionPoint::x)..layer.points.maxOf(StallRegionPoint::x) && z in 0..1 },
            isAir = { x, y, _ -> y in 1..5 && !(x >= 2 && y == 3) && y != 6 },
        ).toSet()

        assertEquals(28, selected.size)
        assertTrue((0..1).all { x -> (1..5).all { y -> StallAirBlock(x, y, 0) in selected } })
        assertTrue((2..3).all { x -> (4..5).all { y -> StallAirBlock(x, y, 0) in selected } })
        assertTrue((2..3).none { x -> StallAirBlock(x, 1, 0) in selected })
    }

    @Test
    fun `deduplicates air shared by overlapping floor outlines`() {
        val lower = StallRegionLayer(mutableListOf(
            StallRegionPoint(0, 0), StallRegionPoint(1, 0), StallRegionPoint(1, 1), StallRegionPoint(0, 1),
        ), floorY = 0)
        val upper = StallRegionLayer(mutableListOf(
            StallRegionPoint(1, 0), StallRegionPoint(2, 0), StallRegionPoint(2, 1), StallRegionPoint(1, 1),
        ), floorY = 3)

        val selected = StallAirVolumeScanner.scan(listOf(lower, upper), topY = 6, maxBlocks = 100,
            contains = { layer, x, z -> x in layer.points.minOf(StallRegionPoint::x)..layer.points.maxOf(StallRegionPoint::x) && z in 0..1 },
            isAir = { x, y, _ -> y in 1..5 && !(x >= 1 && y == 3) && y != 6 },
        )

        assertEquals(22, selected.size)
        assertEquals(1, selected.count { it == StallAirBlock(1, 4, 0) })
    }

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
