package net.badgersmc.em.infrastructure.commands

import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import net.badgersmc.em.domain.shop.ShopHistoryWindow
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.command.CommandSender
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class ShopHistorySelection(val window: ShopHistoryWindow?, val command: String, val label: String)

internal class ShopHistoryMessages(
    private val lang: LangService, private val zone: ZoneId, private val transactions: ShopTransactionRepository,
) {
    fun read(sender: CommandSender, page: Int, selection: ShopHistorySelection) {
        val player = sender as? Player ?: run { sender.sendMessage(lang.msg("shop.cmd.players_only")); return }
        val safePage = page.coerceIn(1, MAX_PAGE)
        val offset = (safePage - 1) * PAGE_SIZE
        val window = selection.window
        val rows = if (window == null) transactions.findByOwnerOrBuyer(player.uniqueId, PAGE_SIZE + 1, offset)
            else transactions.findByOwnerOrBuyer(player.uniqueId, PAGE_SIZE + 1, offset, window)
        show(player, rows, safePage, selection)
    }

    private fun show(player: Player, rows: List<ShopTransaction>, page: Int, selection: ShopHistorySelection) {
        player.sendMessage(lang.msg("shop.history.selection", "filter" to selection.label, "zone" to zone.id))
        if (rows.isEmpty()) {
            player.sendMessage(lang.msg("shop.history.empty_filtered"))
            return
        }
        player.sendMessage(lang.msg("shop.history.header", "page" to page))
        rows.take(PAGE_SIZE).forEach { showRow(player, it) }
        if (rows.size > PAGE_SIZE && page < MAX_PAGE) {
            player.sendMessage(lang.msg("shop.history.filtered_more", "command" to "${selection.command} ${page + 1}"))
        }
    }

    private fun showRow(player: Player, row: ShopTransaction) {
        val whenText = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(zone)
            .format(Instant.ofEpochMilli(row.createdAt))
        val key: String
        val nameKey: String
        val counterpart: java.util.UUID
        if (row.owner == player.uniqueId) {
            key = "shop.history.sold"
            nameKey = "buyer"
            counterpart = row.buyer
        } else {
            key = "shop.history.bought"
            nameKey = "seller"
            counterpart = row.owner
        }
        player.sendMessage(lang.msg(
            key, "when" to whenText, "qty" to row.quantity, "item" to row.item,
            "price" to row.totalPrice, nameKey to (Bukkit.getOfflinePlayer(counterpart).name ?: "Unknown"),
        ))
    }

    companion object {
        const val PAGE_SIZE = 10
        const val MAX_PAGE = 1000
    }
}
