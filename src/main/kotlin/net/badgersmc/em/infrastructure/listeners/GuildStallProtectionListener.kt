package net.badgersmc.em.infrastructure.listeners

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.stall.OwnerType
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.annotations.Component
import org.bukkit.Location
import org.bukkit.block.Container
import org.bukkit.block.DoubleChest
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.inventory.Inventory

/** WorldGuard membership is a projection; current guild rank is the authority. */
@net.badgersmc.nexus.paper.listeners.Listener
@Component
open class GuildStallProtectionListener(private val stalls: StallRepository, private val guilds: GuildProvider) : Listener {
    protected open fun at(location: Location): List<Stall> {
        val world = location.world ?: return emptyList()
        val manager = WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world)) ?: return emptyList()
        return manager.getApplicableRegions(BukkitAdapter.asBlockVector(location)).mapNotNull {
            stalls.findByRegion(world.name, it.id)
        }.filter { it.owner.type == OwnerType.GUILD }
    }

    private fun locations(inventory: Inventory): List<Location> {
        val holder = inventory.holder
        if (holder is DoubleChest) return listOfNotNull(
            (holder.leftSide as? Container)?.location, (holder.rightSide as? Container)?.location,
        )
        return if (holder is Container) listOfNotNull(inventory.location) else emptyList()
    }

    private fun allowed(player: Player, locations: List<Location>, permission: GuildProvider.GuildPermission): Boolean {
        if (player.hasPermission("enthusiamarket.admin")) return true
        return locations.flatMap(::at).all {
            it.isActiveGuildStall() && guilds.isMember(player.uniqueId, it.owner.id) &&
                guilds.hasShopPermission(player.uniqueId, it.owner.id, permission)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        if (!allowed(player, locations(event.inventory), GuildProvider.GuildPermission.ACCESS_SHOP_CHESTS)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!allowed(player, locations(event.view.topInventory), GuildProvider.GuildPermission.EDIT_SHOP_STOCK)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (event.rawSlots.none { it < event.view.topInventory.size }) return
        if (!allowed(player, locations(event.view.topInventory), GuildProvider.GuildPermission.EDIT_SHOP_STOCK)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (!allowed(event.player, listOf(event.block.location), GuildProvider.GuildPermission.EDIT_SHOP_STOCK)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (!allowed(event.player, listOf(event.block.location), GuildProvider.GuildPermission.EDIT_SHOP_STOCK)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBucketEmpty(event: org.bukkit.event.player.PlayerBucketEmptyEvent) {
        if (!allowed(event.player, listOf(event.block.location), GuildProvider.GuildPermission.EDIT_SHOP_STOCK)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBucketFill(event: org.bukkit.event.player.PlayerBucketFillEvent) {
        if (!allowed(event.player, listOf(event.block.location), GuildProvider.GuildPermission.EDIT_SHOP_STOCK)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityPlace(event: org.bukkit.event.entity.EntityPlaceEvent) {
        val player = event.player ?: return
        if (!allowed(player, listOf(event.entity.location), GuildProvider.GuildPermission.EDIT_SHOP_STOCK)) event.isCancelled = true
    }
}
