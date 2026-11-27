package net.badgersmc.em.infrastructure.listeners

/** One interior air block selected for a stall's usable volume. */
internal data class StallAirBlock(val x: Int, val y: Int, val z: Int)

/** Inclusive WorldGuard cuboid used as one piece of a logical stall volume. */
internal data class StallRegionBox(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
)

/** Compresses a voxel set into disjoint cuboids without filling any gaps. */
internal object StallAirVolumeGeometry {
    private data class HorizontalRect(val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int)
    private data class OpenBox(val rect: HorizontalRect, val minY: Int, var maxY: Int)

    fun cuboids(blocks: Collection<StallAirBlock>): List<StallRegionBox> {
        if (blocks.isEmpty()) return emptyList()
        val byY = blocks.groupBy(StallAirBlock::y).toSortedMap()
        val open = mutableMapOf<HorizontalRect, OpenBox>()
        val result = mutableListOf<StallRegionBox>()
        var previousY: Int? = null

        byY.forEach { (y, layer) ->
            if (previousY != null && y != previousY!! + 1) closeMissing(open, emptySet(), result)
            val rectangles = horizontalRectangles(layer)
            closeMissing(open, rectangles, result)
            rectangles.forEach { rect ->
                val box = open[rect]
                if (box == null) open[rect] = OpenBox(rect, y, y) else box.maxY = y
            }
            previousY = y
        }
        closeMissing(open, emptySet(), result)
        return result
    }

    private fun horizontalRectangles(layer: List<StallAirBlock>): Set<HorizontalRect> {
        val rows = layer.groupBy(StallAirBlock::x).toSortedMap()
        val active = mutableMapOf<Pair<Int, Int>, HorizontalRect>()
        val complete = mutableSetOf<HorizontalRect>()
        rows.forEach { (x, blocks) ->
            val zValues = blocks.map(StallAirBlock::z).distinct().sorted()
            val current = contiguousRuns(zValues).toSet()
            (active.keys - current).forEach { key -> active.remove(key)?.let(complete::add) }
            current.forEach { key ->
                val previous = active[key]
                if (previous != null && previous.maxX == x - 1) active[key] = previous.copy(maxX = x)
                else {
                    previous?.let(complete::add)
                    active[key] = HorizontalRect(x, x, key.first, key.second)
                }
            }
        }
        complete += active.values
        return complete
    }

    private fun contiguousRuns(values: List<Int>): List<Pair<Int, Int>> {
        if (values.isEmpty()) return emptyList()
        val result = mutableListOf<Pair<Int, Int>>()
        var start = values.first()
        var end = start
        values.drop(1).forEach { value ->
            if (value == end + 1) end = value else {
                result += start to end
                start = value
                end = value
            }
        }
        result += start to end
        return result
    }

    private fun closeMissing(
        open: MutableMap<HorizontalRect, OpenBox>,
        current: Set<HorizontalRect>,
        result: MutableList<StallRegionBox>,
    ) {
        val finished = open.keys - current
        finished.forEach { rect ->
            val box = open.remove(rect) ?: return@forEach
            result += StallRegionBox(rect.minX, box.minY, rect.minZ, rect.maxX, box.maxY, rect.maxZ)
        }
    }
}
