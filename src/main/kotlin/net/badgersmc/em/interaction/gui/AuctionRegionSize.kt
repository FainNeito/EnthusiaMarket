package net.badgersmc.em.interaction.gui

import net.badgersmc.em.domain.ports.RegionProvider

/** Inclusive enclosing-box dimensions, not a count of empty or buildable blocks. */
internal data class AuctionRegionSize(val width: Long, val length: Long, val height: Long, val volume: Long) {
    companion object {
        fun from(bounds: RegionProvider.RegionBounds?): AuctionRegionSize? {
            bounds ?: return null
            val width = bounds.maxX.toLong() - bounds.minX.toLong() + 1
            val length = bounds.maxZ.toLong() - bounds.minZ.toLong() + 1
            val height = bounds.maxY.toLong() - bounds.minY.toLong() + 1
            if (minOf(width, length, height) <= 0) return null
            val volume = try {
                Math.multiplyExact(Math.multiplyExact(width, length), height)
            } catch (_: ArithmeticException) {
                return null
            }
            return AuctionRegionSize(width, length, height, volume)
        }
    }
}
