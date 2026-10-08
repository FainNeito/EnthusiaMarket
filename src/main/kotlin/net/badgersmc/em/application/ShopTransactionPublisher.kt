package net.badgersmc.em.application

import org.bukkit.Bukkit
import java.util.logging.Logger

/** Best-effort history/notification event, separate from the durable reward journal. */
internal fun publishShopTransaction(data: TransactionEventData) {
    val log = Logger.getLogger(ContainerTradeService::class.java.name)
    log.info(
        "TRADE shop=${data.shopId} dir=${data.direction} buyer=${data.player.uniqueId} " +
        "item=${data.item.type} qty=${data.quantity} cost=${data.cost} " +
        "owner=${data.ownerUuid}"
    )
    Bukkit.getPluginManager().callEvent(
        net.badgersmc.em.events.PostShopTransactionEvent(
            buyer = data.player, landlordId = data.ownerUuid,
            item = data.item, quantity = data.quantity, pricePaid = data.cost.toDouble(),
            shopId = data.shopId, direction = data.direction
        ).apply { guildId = data.guildId; grossPayment = data.cost }
    )
}
