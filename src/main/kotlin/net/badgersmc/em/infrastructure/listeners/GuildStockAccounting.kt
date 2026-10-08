package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.StallAccountingObservation
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.annotations.Component
import java.security.MessageDigest
import java.util.UUID
import java.util.logging.Logger

/** Captures current provenance; no inventory or player objects cross into SQL delivery. */
@Component
open class GuildStockAccounting(private val stalls: StallRepository, private val guilds: GuildProvider, private val storage: StallAccountingStorage) {
    fun stock(shop: Shop, before: Int, after: Int, actor: UUID?) {
        val stall = stalls.findById(StallId(shop.stallId)) ?: return
        if (!stall.isActiveGuildStall() || shop.adminShop) return
        val contributor = actor?.takeIf { guilds.isMember(it, stall.owner.id) && guilds.hasShopPermission(it, stall.owner.id, GuildProvider.GuildPermission.EDIT_SHOP_STOCK) }
        write(observation(shop, stall.owner.id, before, after)?.copy(contributor = contributor))
    }

    fun sale(shop: Shop, event: net.badgersmc.em.events.PostShopTransactionEvent, after: Int) {
        val guild = event.guildId ?: return
        if (!saleEligible(shop, event.quantity)) return
        val captured = observation(shop, guild.toString(), Math.addExact(after, event.quantity), after) ?: return
        write(captured.copy(saleQuantity = event.quantity, grossRevenue = event.grossPayment ?: event.pricePaid.toLong()))
    }

    private fun saleEligible(shop: Shop, quantity: Int): Boolean = !shop.adminShop && quantity > 0

    private fun observation(shop: Shop, guild: String, before: Int, after: Int): StallAccountingObservation? {
        val item = ItemStackSerializer.deserialize(shop.sellItem) ?: return null
        val key = MessageDigest.getInstance("SHA-256").digest(shop.sellItem.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return StallAccountingObservation(UUID.randomUUID(), guild, shop.stallId, shop.id,
            key, item.type.name.lowercase(), null, before, after, createdAt = System.currentTimeMillis())
    }

    private fun write(observation: StallAccountingObservation?) {
        if (observation == null) return
        try { storage.record(observation) } catch (failure: Exception) {
            Logger.getLogger(javaClass.name).warning("Could not capture stall accounting; future stock is unattributed: ${failure.message}")
        }
    }
}
