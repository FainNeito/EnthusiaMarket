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
            captureShop(shop, inventory, actor, expected(shop), candidates)
        }
    }

    private fun captureShop(shop: Shop, inventory: Inventory, actor: UUID?, expected: Int?, candidates: List<Shop>) {
            val template = ItemStackSerializer.deserialize(shop.sellItem) ?: return
            // A shared container exposes one physical stock pool. Never credit that pool
            // to several shop ledgers or guess which shop a contributor intended.
            val shared = candidates.any { other -> other.id != shop.id &&
                ItemStackSerializer.deserialize(other.sellItem)?.let { ItemStackMatch.isSimilarIgnoringDamageNullZero(it, template) } == true }
            val contributor = actor.takeUnless { shared }
            val existing = pending[shop.id]
            if (existing != null) {
                if (contributor == null || contributor != existing.actor) existing.ambiguous = true
                val delta = expected
                existing.expected = if (delta != null && existing.expected != null) existing.expected!! + delta else null
            } else {
                val item = ItemStackSerializer.deserialize(shop.sellItem) ?: return
                val entry = Pending(shop, inventory, ItemStackMatch.countSimilar(inventory, item), contributor, expected, contributor == null)
                pending[shop.id] = entry
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    pending.remove(shop.id)
                    val current = ItemStackSerializer.deserialize(shop.sellItem) ?: return@Runnable
                    val after = ItemStackMatch.countSimilar(inventory, current)
                    val exact = entry.expected?.let { after == entry.before + it } == true
                    accounting.stock(shop, entry.before, after, entry.actor.takeUnless { entry.ambiguous || !exact })
                })
            }
    }

    private fun units(stack: org.bukkit.inventory.ItemStack?, item: org.bukkit.inventory.ItemStack): Int =
        if (stack != null && ItemStackMatch.isSimilarIgnoringDamageNullZero(stack, item)) stack.amount else 0

    private fun room(inventory: Inventory, item: org.bukkit.inventory.ItemStack): Int = inventory.storageContents.sumOf { slot ->
        if (slot == null || slot.type.isAir) item.maxStackSize
        else if (ItemStackMatch.isSimilarIgnoringDamageNullZero(slot, item)) (item.maxStackSize - slot.amount).coerceAtLeast(0)
        else 0
    }

    private fun expectedClick(shop: Shop, event: InventoryClickEvent): Int? {
        val item = ItemStackSerializer.deserialize(shop.sellItem) ?: return null
        val cursor = event.cursor
        val current = event.currentItem
        if (event.clickedInventory !== event.inventory) {
            if (event.action != org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY || units(current, item) == 0) return null
            return minOf(units(current, item), room(event.inventory, item))
        }
        return when (event.action) {
            org.bukkit.event.inventory.InventoryAction.PLACE_ALL,
            org.bukkit.event.inventory.InventoryAction.PLACE_SOME -> if (units(cursor, item) > 0) minOf(cursor.amount, item.maxStackSize - (current?.amount ?: 0)) else 0
            org.bukkit.event.inventory.InventoryAction.PLACE_ONE -> if (units(cursor, item) > 0) 1 else 0
            org.bukkit.event.inventory.InventoryAction.SWAP_WITH_CURSOR -> units(cursor, item) - units(current, item)
            else -> null // Unproven edit types remain unattributed.
        }
    }
}
