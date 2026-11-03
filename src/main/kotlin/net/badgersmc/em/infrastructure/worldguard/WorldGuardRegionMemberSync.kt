package net.badgersmc.em.infrastructure.worldguard

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import net.badgersmc.em.domain.ports.RegionMemberSync
import net.badgersmc.nexus.annotations.Component
import org.bukkit.Bukkit
import java.util.UUID

/** WorldGuard projection of the authoritative stall roster. Missing targets are failures. */
@Component
open class WorldGuardRegionMemberSync : RegionMemberSync {

    override fun addMember(world: String, regionId: String, player: UUID) {
        withRegion(world, regionId) { region ->
            region.members.addPlayer(player)
        }
    }

    override fun removeMember(world: String, regionId: String, player: UUID) {
        withRegion(world, regionId) { region ->
            region.members.removePlayer(player)
        }
    }

    override fun setOwner(world: String, regionId: String, player: UUID) {
        withRegion(world, regionId) { region ->
            // Replace, don't append — a stall transfer shouldn't leave
            // the previous owner with build rights. Use the domain
            // clear-then-add pattern WG exposes via DefaultDomain.
            region.owners.removeAll()
            region.owners.addPlayer(player)
            // Also clear members on owner transfer — old trusted players
            // must be explicitly re-trusted by the new owner.
            region.members.removeAll()
        }
    }

    override fun clearOwnersAndMembers(world: String, regionId: String) {
        withRegion(world, regionId) { region ->
            region.owners.removeAll()
            region.members.removeAll()
        }
    }

    override fun syncGuildMembers(world: String, regionId: String, memberUuids: Set<UUID>) {
        withRegion(world, regionId) { region ->
            region.owners.removeAll()
            region.members.removeAll()
            memberUuids.forEach { region.members.addPlayer(it) }
        }
    }

    private inline fun withRegion(
        world: String,
        regionId: String,
        block: (com.sk89q.worldguard.protection.regions.ProtectedRegion) -> Unit,
    ) {
        block(findRegion(world, regionId))
    }

    /** Keep platform lookup separate from mutations of WorldGuard's access domains. */
    protected open fun findRegion(
        world: String,
        regionId: String,
    ): com.sk89q.worldguard.protection.regions.ProtectedRegion {
        val bukkitWorld = checkNotNull(Bukkit.getWorld(world)) {
            "RegionMemberSync: world $world not loaded"
        }
        val regionManager = checkNotNull(WorldGuard.getInstance().platform.regionContainer
            .get(BukkitAdapter.adapt(bukkitWorld))) {
            "RegionMemberSync: no region manager for $world"
        }
        return checkNotNull(regionManager.getRegion(regionId)) {
            "RegionMemberSync: region $regionId not found in $world"
        }
    }
}
