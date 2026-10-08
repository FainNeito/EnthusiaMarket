package net.badgersmc.em.infrastructure.listeners

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.Flags
import com.sk89q.worldguard.protection.flags.RegionGroup
import com.sk89q.worldguard.protection.flags.StateFlag
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallAccessSettings
import net.badgersmc.em.domain.stall.StallCapability
import org.bukkit.Bukkit

/** Only open the WorldGuard gates guarded by Market; never add allies as building members. */
class StallAccessProjection {
    fun apply(stall: Stall, settings: StallAccessSettings) {
        val world = Bukkit.getWorld(stall.world) ?: return
        val manager = WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world)) ?: return
        val region = manager.getRegion(stall.regionId) ?: return
        region.setFlag(Flags.POTION_SPLASH, if (settings.allowIncomingPotions) StateFlag.State.ALLOW else StateFlag.State.DENY)
        region.setFlag(Flags.POTION_SPLASH.regionGroupFlag, RegionGroup.ALL)
        val alliedChests = settings.allies.values.any { StallCapability.CHESTS in it }
        region.setFlag(Flags.CHEST_ACCESS, StateFlag.State.ALLOW)
        region.setFlag(Flags.CHEST_ACCESS.regionGroupFlag, if (alliedChests) RegionGroup.ALL else RegionGroup.MEMBERS)
        // The durable policy is reapplied at startup; a region-store failure must not undo the policy.
        runCatching { manager.save() }
    }
}
