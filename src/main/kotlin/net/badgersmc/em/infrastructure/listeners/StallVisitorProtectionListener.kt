package net.badgersmc.em.infrastructure.listeners

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.annotations.Component
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerTakeLecternBookEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.block.Lectern

/** Public workstation use must not grant visitors decoration/book ownership. */
@net.badgersmc.nexus.paper.listeners.Listener
@Component
open class StallVisitorProtectionListener(private val stalls: StallRepository, private val guilds: GuildProvider) : Listener {
    protected open fun mayModify(player: Player, location: Location): Boolean {
        if (player.hasPermission("enthusiamarket.admin")) return true
        val world = location.world ?: return false
        val manager = WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world)) ?: return true
        return manager.getApplicableRegions(BukkitAdapter.asBlockVector(location)).mapNotNull {
            stalls.findByRegion(world.name, it.id)
        }.all { it.canManage(player.uniqueId, guilds) }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntity(event: PlayerInteractEntityEvent) {
        if (event.rightClicked is org.bukkit.entity.ArmorStand || event.rightClicked is org.bukkit.entity.ItemFrame) {
            if (!mayModify(event.player, event.rightClicked.location)) event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onTakeBook(event: PlayerTakeLecternBookEvent) {
        if (!mayModify(event.player, event.lectern.location)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLecternInventory(event: InventoryClickEvent) {
        val lectern = event.view.topInventory.holder as? Lectern ?: return
        val player = event.whoClicked as? Player ?: return
        if (!mayModify(player, lectern.location)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onReadLectern(event: org.bukkit.event.player.PlayerInteractEvent) {
        if (!isMainHandBookRead(event)) return
        val lectern = event.clickedBlock?.state as? Lectern ?: return
        if (mayModify(event.player, lectern.location)) return
        // Open a copy of a written book; no lectern inventory is exposed to visitors.
        val book = writtenBook(lectern) ?: return
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY)
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY)
        event.player.openBook(book.clone())
    }

    private fun isMainHandBookRead(event: org.bukkit.event.player.PlayerInteractEvent): Boolean =
        event.action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && event.hand == org.bukkit.inventory.EquipmentSlot.HAND

    private fun writtenBook(lectern: Lectern): org.bukkit.inventory.ItemStack? =
        lectern.inventory.getItem(0)?.takeIf { it.type == org.bukkit.Material.WRITTEN_BOOK }
}
