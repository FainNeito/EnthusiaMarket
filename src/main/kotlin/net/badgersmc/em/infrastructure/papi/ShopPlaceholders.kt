package net.badgersmc.em.infrastructure.papi

import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import net.badgersmc.nexus.annotations.Component
import net.badgersmc.nexus.papi.PapiExpansion
import net.badgersmc.nexus.papi.PlaceholderResolver
import org.bukkit.OfflinePlayer

/** %enthusiamarket_<key>% placeholders (ItemShops parity SP6). */
@PapiExpansion(identifier = "enthusiamarket")
@Component
class ShopPlaceholders(
    private val shops: ShopRepository,
    private val transactions: ShopTransactionRepository,
) : PlaceholderResolver {
    internal val stallWeekDisplay = net.badgersmc.em.infrastructure.website.StallWeekDisplay()
    internal val guildStallWeekDisplay = net.badgersmc.em.infrastructure.website.StallWeekDisplay.guild()

    override fun resolve(player: OfflinePlayer?, params: String): String? = when (params.lowercase()) {
        "shops_total" -> shops.countAll().toString()
        "shops_owned" -> player?.let { shops.countByOwner(it.uniqueId).toString() }
        "sales_unseen" -> player?.let { transactions.countUnnotified(it.uniqueId).toString() }
        "stall_of_the_week" -> stallWeekDisplay.line()
        "guild_stall_of_the_week" -> guildStallWeekDisplay.line()
        else -> null
    }
}
