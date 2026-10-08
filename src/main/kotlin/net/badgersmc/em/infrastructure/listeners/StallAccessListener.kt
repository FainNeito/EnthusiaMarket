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
        val hidden = hasBlockedEffect(player, event.item.location)
        if (hidden || !allowed(player, event.item.location, StallCapability.ITEM_PICKUP)) event.isCancelled = true
    }

    private fun hasBlockedEffect(player: Player, location: Location): Boolean {
        val blocked = regions.at(location).flatMap { access.current(it).blockedEffects }.toSet()
        return player.activePotionEffects.any { it.type.name in blocked }
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
        if (!inventoryAllowed(player, event.inventory, StallCapability.CHESTS)) { event.isCancelled = true; return }
        val location = event.inventory.location ?: return
        val capability = workstation(event.inventory.type) ?: return
        if (!allowed(player, location, capability)) event.isCancelled = true
    }

    private fun workstation(type: org.bukkit.event.inventory.InventoryType): StallCapability? = when (type) {
            org.bukkit.event.inventory.InventoryType.ANVIL -> StallCapability.ANVIL
            org.bukkit.event.inventory.InventoryType.LECTERN -> StallCapability.LECTERN
            else -> null
        }

    private fun inventoryAllowed(player: Player, inventory: org.bukkit.inventory.Inventory, capability: StallCapability): Boolean =
        containerLocations(inventory).all { allowed(player, it, capability) }

    private fun containerLocations(inventory: org.bukkit.inventory.Inventory): List<Location> {
        val holder = inventory.holder
        return when (holder) {
            is org.bukkit.block.DoubleChest -> listOfNotNull(
                (holder.leftSide as? org.bukkit.block.Container)?.location,
                (holder.rightSide as? org.bukkit.block.Container)?.location,
            )
            is org.bukkit.block.Container -> listOf(holder.location)
            else -> emptyList()
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: org.bukkit.event.inventory.InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!inventoryAllowed(player, event.view.topInventory, StallCapability.STOCK)) { event.isCancelled = true; return }
        val location = event.view.topInventory.location ?: return
        if (!allowed(player, location, StallCapability.ENTRY)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryDrag(event: org.bukkit.event.inventory.InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!inventoryAllowed(player, event.view.topInventory, StallCapability.STOCK)) { event.isCancelled = true; return }
        val location = event.view.topInventory.location ?: return
        if (!allowed(player, location, StallCapability.ENTRY)) event.isCancelled = true
    }

    private fun capability(material: Material): StallCapability? = when {
        material.name.endsWith("ANVIL") -> StallCapability.ANVIL
        material == Material.LECTERN -> StallCapability.LECTERN
        else -> physicalUse(material)
    }

    private fun physicalUse(material: Material): StallCapability? = when {
        isDoor(material) -> StallCapability.DOORS
        material.name.endsWith("BUTTON") -> StallCapability.BUTTONS
        material == Material.LEVER -> StallCapability.LEVERS
        else -> null
    }

    private fun isDoor(material: Material): Boolean = material.name.endsWith("DOOR") || material.name.endsWith("FENCE_GATE")
}
