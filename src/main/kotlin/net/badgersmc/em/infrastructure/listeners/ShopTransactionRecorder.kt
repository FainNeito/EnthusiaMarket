package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.ShopSaleAttribution
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
    private val transactions: ShopHistoryStorage,
    private val config: EnthusiaMarketConfig,
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    @Suppress("TooGenericExceptionCaught")
    fun onTransaction(event: PostShopTransactionEvent) {
        try {
            if (config.shop.archiveCaptureEnabled && event.awardCaptureIncomplete) {
                runCatching { transactions.markCaptureFailure() }
            }
            val transaction =
                ShopTransaction(
                    shopId = event.shopId,
                    owner = event.landlordId,
                    buyer = event.buyer.uniqueId,
                    direction = event.direction,
                    item = event.item.type.name.lowercase(),
                    quantity = event.quantity,
                    totalPrice = event.grossPayment ?: event.pricePaid.toLong(),
                    createdAt = System.currentTimeMillis(),
                    notified = config.shop.notifyEnabled && Bukkit.getPlayer(event.landlordId)?.isOnline == true,
                )
            if (!config.shop.archiveCaptureEnabled || (event.stallId == null && event.guildId == null)) transactions.record(transaction)
            else transactions.recordAttributed(transaction, ShopSaleAttribution(event.stallId, event.guildId, event.publicCashEligible, event.awardOwner))
        } catch (e: Exception) {
            if (config.shop.archiveCaptureEnabled) runCatching { transactions.markCaptureFailure() }
            // History is best-effort: a log write must never disturb the completed trade.
            org.bukkit.Bukkit.getLogger().warning("Failed to record shop transaction: ${e.message}")
        }
    }
}
