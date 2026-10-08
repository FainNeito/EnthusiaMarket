package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.ItemStackMatch
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.events.PostShopTransactionEvent
import net.badgersmc.nexus.annotations.Component
import org.bukkit.Bukkit
import org.bukkit.block.Container
import org.bukkit.block.DoubleChest
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.Plugin
import java.util.UUID

/** Only single-actor manual edits are credited; mixed/hopper/sale interference is unknown. */
@net.badgersmc.nexus.paper.listeners.Listener
@Component
open class StallStockContributorListener(private val stock: ContainerStockListener, private val accounting: GuildStockAccounting, private val plugin: Plugin) : Listener {
    private data class Pending(val shop: Shop, val inventory: Inventory, val before: Int, val actor: UUID?, var expected: Int?, var ambiguous: Boolean = false)
    private val pending = mutableMapOf<Long, Pending>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        if (event.clickedInventory === event.inventory || event.isShiftClick || event.action == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) {
            capture(event.inventory, (event.whoClicked as? Player)?.uniqueId) { shop -> expectedClick(shop, event) }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        if (event.rawSlots.any { it < event.inventory.size }) capture(event.inventory, (event.whoClicked as? Player)?.uniqueId) { shop ->
            val item = ItemStackSerializer.deserialize(shop.sellItem) ?: return@capture null
            event.newItems.filterKeys { it < event.inventory.size }.entries.sumOf { (slot, stack) ->
                units(stack, item) - units(event.inventory.getItem(slot), item)
            }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: InventoryMoveItemEvent) { capture(event.source, null); capture(event.destination, null) }
    @EventHandler(priority = EventPriority.MONITOR)
    fun onSale(event: PostShopTransactionEvent) { pending[event.shopId]?.ambiguous = true }

    private fun capture(inventory: Inventory, actor: UUID?, expected: (Shop) -> Int? = { null }) {
        val holder = inventory.holder
        val locations = if (holder is DoubleChest) listOfNotNull((holder.leftSide as? Container)?.location, (holder.rightSide as? Container)?.location)
            else listOfNotNull(inventory.location)
        val candidates = stock.accountingCandidates().filter { shop -> locations.any { location ->
            location.world?.name == shop.containerWorld && location.blockX == shop.containerX && location.blockY == shop.containerY && location.blockZ == shop.containerZ
        } }
        candidates.forEach { shop ->
            captureShop(shop, inventory, Capture(actor, expected(shop), candidates))
        }
    }

    private data class Capture(val actor: UUID?, val expected: Int?, val candidates: List<Shop>)

    private fun captureShop(shop: Shop, inventory: Inventory, capture: Capture) {
        val template = ItemStackSerializer.deserialize(shop.sellItem) ?: return
        val contributor = capture.actor.takeUnless { sharedPool(shop, template, capture.candidates) }
        val existing = pending[shop.id]
        if (existing != null) { updatePending(existing, contributor, capture.expected); return }
        val entry = Pending(shop, inventory, ItemStackMatch.countSimilar(inventory, template), contributor, capture.expected, contributor == null)
        pending[shop.id] = entry
        Bukkit.getScheduler().runTask(plugin, Runnable { complete(entry) })
    }

    private fun sharedPool(shop: Shop, template: ItemStack, candidates: List<Shop>): Boolean = candidates.any { other ->
        other.id != shop.id && ItemStackSerializer.deserialize(other.sellItem)?.let {
            ItemStackMatch.isSimilarIgnoringDamageNullZero(it, template)
        } == true
    }

    private fun updatePending(entry: Pending, actor: UUID?, delta: Int?) {
        if (actor == null || actor != entry.actor) entry.ambiguous = true
        entry.expected = entry.expected?.let { previous -> delta?.let { previous + it } }
    }

    private fun complete(entry: Pending) {
        pending.remove(entry.shop.id)
        val current = ItemStackSerializer.deserialize(entry.shop.sellItem) ?: return
        val after = ItemStackMatch.countSimilar(entry.inventory, current)
        val exact = entry.expected?.let { after == entry.before + it } == true
        accounting.stock(entry.shop, entry.before, after, entry.actor.takeUnless { entry.ambiguous || !exact })
    }

    private fun units(stack: org.bukkit.inventory.ItemStack?, item: org.bukkit.inventory.ItemStack): Int =
        if (stack != null && ItemStackMatch.isSimilarIgnoringDamageNullZero(stack, item)) stack.amount else 0

    private fun room(inventory: Inventory, item: ItemStack): Int = inventory.storageContents.sumOf { slot -> space(slot, item) }

    private fun space(slot: ItemStack?, item: ItemStack): Int =
        if (slot == null || slot.type.isAir) item.maxStackSize
        else if (ItemStackMatch.isSimilarIgnoringDamageNullZero(slot, item)) (item.maxStackSize - slot.amount).coerceAtLeast(0)
        else 0

    private fun expectedClick(shop: Shop, event: InventoryClickEvent): Int? {
        val item = ItemStackSerializer.deserialize(shop.sellItem) ?: return null
        if (event.clickedInventory !== event.inventory) return shiftDelta(event, item)
        return topDelta(event, item)
    }

    private fun shiftDelta(event: InventoryClickEvent, item: ItemStack): Int? {
        if (event.action != InventoryAction.MOVE_TO_OTHER_INVENTORY || units(event.currentItem, item) == 0) return null
        return minOf(units(event.currentItem, item), room(event.inventory, item))
    }

    private fun topDelta(event: InventoryClickEvent, item: ItemStack): Int? = when (event.action) {
        InventoryAction.PLACE_ALL, InventoryAction.PLACE_SOME -> placement(event, item)
        InventoryAction.PLACE_ONE -> if (units(event.cursor, item) > 0) 1 else 0
        InventoryAction.SWAP_WITH_CURSOR -> units(event.cursor, item) - units(event.currentItem, item)
        else -> null // Unproven edit types remain unattributed.
    }

    private fun placement(event: InventoryClickEvent, item: ItemStack): Int =
        if (units(event.cursor, item) > 0) minOf(event.cursor.amount, item.maxStackSize - (event.currentItem?.amount ?: 0)) else 0
}
