package net.badgersmc.em.infrastructure.listeners

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

/** Draws a private outline of the exact air-volume cuboids selected for the stall. */
internal class StallRegionPreviewRenderer(private val plugin: Plugin) {
    private val tasks = mutableMapOf<UUID, org.bukkit.scheduler.BukkitTask>()

    fun stop(player: Player) { tasks.remove(player.uniqueId)?.cancel() }

    fun render(player: Player, draft: StallRegionDraft, repeated: Boolean = false) {
        if (Bukkit.getWorld(draft.world) == null || draft.airVolume.isEmpty()) return
        tasks.remove(player.uniqueId)?.cancel()
        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (player.isOnline) draw(player, draft.airVolume)
        }, 0L, 10L)
        tasks[player.uniqueId] = task
        val duration = if (repeated) PREVIEW_SECONDS * 20 else 1L
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (tasks[player.uniqueId] === task) tasks.remove(player.uniqueId)?.cancel()
        }, duration)
    }

    private fun draw(player: Player, boxes: List<StallRegionBox>) {
        val edges = boxes.flatMap(::edges)
        val samples = edges.sumOf { edge -> max(abs(edge.endX - edge.startX), max(abs(edge.endY - edge.startY), abs(edge.endZ - edge.startZ))) + 1 }
        val stride = max(1, ceil(samples.toDouble() / PARTICLE_BUDGET).toInt())
        edges.forEach { edge -> drawEdge(player, edge, stride) }
    }

    private data class Edge(val startX: Double, val startY: Double, val startZ: Double, val endX: Double, val endY: Double, val endZ: Double)

    private fun edges(box: StallRegionBox): List<Edge> {
        val xs = listOf(box.minX.toDouble(), box.maxX + 1.0)
        val ys = listOf(box.minY.toDouble(), box.maxY + 1.0)
        val zs = listOf(box.minZ.toDouble(), box.maxZ + 1.0)
        val corners = xs.flatMap { x -> ys.flatMap { y -> zs.map { z -> Triple(x, y, z) } } }
        return corners.flatMap { first -> corners.mapNotNull { second ->
            val changed = listOf(first.first != second.first, first.second != second.second, first.third != second.third).count { it }
            if (changed == 1 && corners.indexOf(first) < corners.indexOf(second)) Edge(first.first, first.second, first.third, second.first, second.second, second.third) else null
        } }
    }

    private fun drawEdge(player: Player, edge: Edge, stride: Int) {
        val steps = max(1, max(abs(edge.endX - edge.startX), max(abs(edge.endY - edge.startY), abs(edge.endZ - edge.startZ))).toInt())
        for (step in 0..steps step stride) {
            val fraction = step.toDouble() / steps
            player.spawnParticle(Particle.END_ROD,
                edge.startX + (edge.endX - edge.startX) * fraction,
                edge.startY + (edge.endY - edge.startY) * fraction,
                edge.startZ + (edge.endZ - edge.startZ) * fraction,
                1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun abs(value: Double) = kotlin.math.abs(value).toInt()

    companion object {
        private const val PARTICLE_BUDGET = 1_200
        private const val PREVIEW_SECONDS = 30L
    }
}
