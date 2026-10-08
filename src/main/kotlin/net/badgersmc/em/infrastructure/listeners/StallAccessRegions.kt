package net.badgersmc.em.infrastructure.listeners

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import net.badgersmc.em.application.StallAccessIndex
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.nexus.annotations.Component
import org.bukkit.Location

/** All overlapping stall policies apply; event lookups never read persistence. */
@Component
open class StallAccessRegions(private val index: StallAccessIndex) {
    open fun at(location: Location): List<Stall> {
        val world = location.world ?: return emptyList()
        val manager = WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world)) ?: return emptyList()
        return manager.getApplicableRegions(BukkitAdapter.asBlockVector(location)).mapNotNull {
            index.cached(world.name, it.id)
        }
    }
}
