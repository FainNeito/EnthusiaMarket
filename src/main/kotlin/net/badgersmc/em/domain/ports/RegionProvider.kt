package net.badgersmc.em.domain.ports

interface RegionProvider {
    data class RegionRef(val world: String, val id: String)

    /** All region IDs in [world] whose id starts with [prefix]. */
    fun listByPrefix(world: String, prefix: String): List<RegionRef>

    /** True if a region with [id] exists in [world]. */
    fun exists(world: String, id: String): Boolean

    /**
     * Highest-priority region id containing the block at ([x],[y],[z]) in
     * [world] whose id is a stall, or null when no region covers the point.
     * Implementations may filter to the configured stall prefix.
     */
    fun regionAt(world: String, x: Int, y: Int, z: Int): String?

    /**
     * Bounding box of the region [id] in [world], or null if the region
     * or world is not found. The box is axis-aligned from minimum to
     * maximum block coordinates (inclusive).
     */
    data class RegionBounds @JvmOverloads constructor(
        val minX: Int, val minY: Int, val minZ: Int,
        val maxX: Int, val maxY: Int, val maxZ: Int,
        val claimedColumns: Long? = rectangularColumns(minX, maxX, minZ, maxZ),
        private val exactClaimedVolume: Long? = null,
    ) {
        val claimedVolume: Long? get() {
            exactClaimedVolume?.let { return it }
            val columns = claimedColumns ?: return null
            val layers = maxY.toLong() - minY + 1
            return if (columns > 0 && layers > 0 && columns <= Long.MAX_VALUE / layers) columns * layers else null
        }

        companion object {
            private fun rectangularColumns(minX: Int, maxX: Int, minZ: Int, maxZ: Int): Long? {
                val x = maxX.toLong() - minX + 1
                val z = maxZ.toLong() - minZ + 1
                return if (x > 0 && z > 0 && x <= Long.MAX_VALUE / z) x * z else null
            }
        }

        /** Inclusive block span on the X axis. */
        val width: Int get() = maxX - minX + 1
        /** Inclusive block span on the Y axis. */
        val height: Int get() = maxY - minY + 1
        /** Inclusive block span on the Z axis. */
        val length: Int get() = maxZ - minZ + 1
    }

    /** Region bounding box for [id] in [world], or null if not found. */
    fun bounds(world: String, id: String): RegionBounds?

    data class Vertex(val x: Double, val z: Double)
    data class Footprint(val vertices: List<Vertex>, val minY: Double, val maxY: Double)

    /** Actual boundary vertices; Y bounds are continuous outer limits. Unsupported shapes return null. */
    fun footprint(world: String, id: String): Footprint? = null
}
