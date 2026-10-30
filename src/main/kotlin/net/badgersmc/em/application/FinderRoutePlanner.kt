package net.badgersmc.em.application

/** Bounded explicit walkway graph. Does not inspect terrain or infer walkable connections. */
object FinderRoutePlanner {
    data class Node(val id: String, val point: FinderTrailTracker.Point, val links: List<String>)

    fun plan(nodes: List<Node>, from: FinderTrailTracker.Point, target: FinderTrailTracker.Point,
             attachment: Double): List<FinderTrailTracker.Point> {
        if (!valid(nodes)) return emptyList()
        val radius = attachment.takeIf { it.isFinite() }?.coerceIn(1.0, 32.0) ?: 12.0
        val start = nodes.minBy { it.point.distance(from) }.takeIf { it.point.distance(from) <= radius }
            ?: return emptyList()
        val end = nodes.minBy { it.point.distance(target) }.takeIf { it.point.distance(target) <= radius }
            ?: return emptyList()
        return shortest(nodes, start.id, end.id)
    }

    private fun valid(nodes: List<Node>): Boolean = nodes.size in 1..128 &&
        nodes.map { it.id }.distinct().size == nodes.size && nodes.all(::validNode)

    private fun validNode(it: Node): Boolean =
            it.id.isNotBlank() && it.links.size <= 128 &&
                listOf(it.point.x, it.point.y, it.point.z).all(Double::isFinite)


    private data class Search(val costs: MutableMap<String, Double>, val previous: MutableMap<String, String>)

    private fun shortest(nodes: List<Node>, start: String, end: String): List<FinderTrailTracker.Point> {
        val byId = nodes.associateBy { it.id }
        val remaining = byId.keys.toMutableSet()
        val costs = mutableMapOf(start to 0.0)
        val previous = mutableMapOf<String, String>()
        while (remaining.isNotEmpty()) {
            val current = remaining.minBy { costs[it] ?: Double.POSITIVE_INFINITY }
            if (current !in costs) return emptyList()
            if (current == end) return reconstruct(byId, previous, end)
            remaining.remove(current)
            relax(nodes, current, remaining, Search(costs, previous))
        }
        return emptyList()
    }

    private fun relax(nodes: List<Node>, current: String, remaining: Set<String>,
                      search: Search) {
        val node = nodes.first { it.id == current }
        nodes.filter { connected(node, it, remaining) }.forEach { next ->
            val cost = search.costs.getValue(current) + node.point.distance(next.point)
            if (cost < (search.costs[next.id] ?: Double.POSITIVE_INFINITY)) {
                search.costs[next.id] = cost
                search.previous[next.id] = current
            }
        }
    }

    private fun connected(from: Node, to: Node, remaining: Set<String>): Boolean =
        to.id in remaining && (to.id in from.links || from.id in to.links)

    private fun reconstruct(nodes: Map<String, Node>, previous: Map<String, String>, end: String): List<FinderTrailTracker.Point> {
        val result = mutableListOf(nodes.getValue(end).point)
        var cursor = end
        while (cursor in previous) {
            cursor = previous.getValue(cursor)
            result += nodes.getValue(cursor).point
        }
        return result.reversed()
    }
}
