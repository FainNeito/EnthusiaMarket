package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import net.badgersmc.em.events.PostShopTransactionEvent
import net.badgersmc.nexus.annotations.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.EventPriority
import org.bukkit.Bukkit
import net.badgersmc.em.config.EnthusiaMarketConfig

/** Persists every completed shop trade to the transaction log (ItemShops parity SP6). */
@net.badgersmc.nexus.paper.listeners.Listener
@Component
open class ShopTransactionRecorder(
    private val transactions: ShopTransactionRepository,
    private val config: EnthusiaMarketConfig,
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    @Suppress("TooGenericExceptionCaught")
    fun onTransaction(event: PostShopTransactionEvent) {
        try {
            transactions.record(
                ShopTransaction(
                    shopId = event.shopId,
                    owner = event.landlordId,
                    buyer = event.buyer.uniqueId,
                    direction = event.direction,
                    item = event.item.type.name.lowercase(),
                    quantity = event.quantity,
                    totalPrice = event.pricePaid.toLong(),
                    createdAt = System.currentTimeMillis(),
                    notified = config.shop.notifyEnabled && Bukkit.getPlayer(event.landlordId)?.isOnline == true,
                )
            )
        } catch (e: Exception) {
            // History is best-effort: a log write must never disturb the completed trade.
            org.bukkit.Bukkit.getLogger().warning("Failed to record shop transaction: ${e.message}")
        }
    }
}
