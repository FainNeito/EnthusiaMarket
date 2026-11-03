package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.FinderRoutePlanner
import net.badgersmc.em.application.FinderTrailTracker
import net.badgersmc.em.config.EnthusiaMarketConfig

/** Nexus retains YAML collection entries as maps; validate them at the adapter boundary. */
internal object FinderRouteConfiguration {
    fun plan(config: EnthusiaMarketConfig.FinderRouting, world: String,
             from: FinderTrailTracker.Point, target: FinderTrailTracker.Point): List<FinderTrailTracker.Point> {
        if (!config.enabled || config.nodes.size > 4096) return emptyList()
        val entries = config.nodes.asSequence().filter { (it as? Map<*, *>)?.get("world") == world }.take(129)
        val nodes = entries.toList().map { parse(it) ?: return emptyList() }
        return FinderRoutePlanner.plan(nodes, from, target, config.attachmentDistance)
    }

    private fun parse(raw: Any): FinderRoutePlanner.Node? {
        val entry = raw as? Map<*, *> ?: return null
        val id = entry["id"] as? String ?: return null
        val links = links(entry) ?: return null
        val point = FinderTrailTracker.Point(coordinate(entry, "x"), coordinate(entry, "y"), coordinate(entry, "z"))
        return FinderRoutePlanner.Node(id, point, links)
    }

    private fun links(entry: Map<*, *>): List<String>? {
        val value = entry["links"] ?: return emptyList()
        val links = value as? List<*> ?: return null
        return if (links.all { it is String }) links.filterIsInstance<String>().take(129) else null
    }

    private fun coordinate(entry: Map<*, *>, key: String): Double = entry[key]?.toString()?.toDoubleOrNull() ?: Double.NaN
}
