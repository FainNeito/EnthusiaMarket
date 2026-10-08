package net.badgersmc.em.application

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/** Preserve actual collected stacks and the exact amounts delivered for compensation. */
internal object InventoryTradeDelivery {
    data class Result(val leftoverAmount: Int, val addedPerItem: List<Pair<ItemStack, Int>>)

    fun deliver(inventory: Inventory, collectedItems: List<ItemStack>): Result {
        var leftoverAmount = 0
        val addedPerItem = mutableListOf<Pair<ItemStack, Int>>()
        for (item in collectedItems) {
            val leftover = inventory.addItem(item).values.sumOf { it.amount }
            leftoverAmount += leftover
            addedPerItem.add(item to (item.amount - leftover))
        }
        return Result(leftoverAmount, addedPerItem)
    }

    fun removeDelivered(inventory: Inventory, addedPerItem: List<Pair<ItemStack, Int>>) {
        for ((item, added) in addedPerItem) {
            if (added > 0) inventory.removeItem(item.clone().apply { amount = added })
        }
    }
}
