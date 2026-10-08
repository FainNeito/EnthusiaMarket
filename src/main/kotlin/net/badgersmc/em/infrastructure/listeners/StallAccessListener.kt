package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.StallAccessSettingsService
import net.badgersmc.em.domain.stall.StallCapability
import net.badgersmc.nexus.annotations.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent

@Component
@net.badgersmc.nexus.paper.listeners.Listener
class StallAccessListener(private val regions: StallAccessRegions, private val access: StallAccessSettingsService) : Listener {
    fun allowed(player: Player, location: Location, capability: StallCapability): Boolean =
        player.hasPermission("enthusiamarket.admin") || regions.at(location).all {
            access.allows(it.id.value, player.uniqueId, StallCapability.ENTRY) &&
                access.allows(it.id.value, player.uniqueId, capability)
        }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (allowed(event.player, event.from, StallCapability.ENTRY) &&
            !allowed(event.player, event.to, StallCapability.ENTRY)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        if (!allowed(event.player, event.to, StallCapability.ENTRY)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val hidden = regions.at(event.item.location).any { stall ->
            access.current(stall).blockedEffects.any { name ->
                player.activePotionEffects.any { effect -> effect.type.name == name }
            }
        }
        if (hidden || !allowed(player, event.item.location, StallCapability.ITEM_PICKUP)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        val capability = capability(block.type) ?: return
        if (!allowed(event.player, block.location, capability)) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY)
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        val location = event.inventory.location ?: return
        if (!allowed(player, location, StallCapability.ENTRY)) { event.isCancelled = true; return }
        if (event.inventory.holder is org.bukkit.block.Container && !containerAllowed(player, location)) {
            event.isCancelled = true; return
        }
        val capability = when (event.inventory.type) {
            org.bukkit.event.inventory.InventoryType.ANVIL -> StallCapability.ANVIL
            org.bukkit.event.inventory.InventoryType.LECTERN -> StallCapability.LECTERN
            else -> return
        }
        if (!allowed(player, location, capability)) event.isCancelled = true
    }

    private fun containerAllowed(player: Player, location: Location): Boolean = player.hasPermission("enthusiamarket.admin") ||
        regions.at(location).all { access.allows(it.id.value, player.uniqueId, StallCapability.CHESTS) }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: org.bukkit.event.inventory.InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val location = event.view.topInventory.location ?: return
        if (!allowed(player, location, StallCapability.ENTRY)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryDrag(event: org.bukkit.event.inventory.InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val location = event.view.topInventory.location ?: return
        if (!allowed(player, location, StallCapability.ENTRY)) event.isCancelled = true
    }

    private fun capability(material: Material): StallCapability? = when {
        material.name.endsWith("ANVIL") -> StallCapability.ANVIL
        material == Material.LECTERN -> StallCapability.LECTERN
        material.name.endsWith("DOOR") || material.name.endsWith("FENCE_GATE") -> StallCapability.DOORS
        material.name.endsWith("BUTTON") -> StallCapability.BUTTONS
        material == Material.LEVER -> StallCapability.LEVERS
        else -> null
    }
}
