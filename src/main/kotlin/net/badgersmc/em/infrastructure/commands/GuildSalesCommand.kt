package net.badgersmc.em.infrastructure.commands

import net.badgersmc.em.application.ShopHistoryDates
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.shop.*
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.commands.annotations.*
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.nio.file.Files
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

/** Read-only manager reporting with current authority checked before query and disclosure. */
@Command(name = "guildsales", description = "Guild stall contribution and gross sales report", aliases = ["stallsales"])
class GuildSalesCommand(private val repository: StallAccountingRepository, private val stalls: StallRepository,
    private val guilds: GuildProvider, private val lang: LangService, private val plugin: Plugin) {
    @Subcommand("")
    @Permission("enthusiamarket.shop.use")
    fun report(@Context sender: CommandSender, @Arg("stall") stallId: String,
        @Arg("period") period: String = "all", @Arg("from") from: String = "", @Arg("to") to: String = "") = execute(sender, stallId, period, from, to, "chat")

    @Subcommand("export")
    @Permission("enthusiamarket.shop.use")
    fun exportReport(@Context sender: CommandSender, @Arg("stall") stallId: String,
        @Arg("period") period: String = "all", @Arg("from") from: String = "", @Arg("to") to: String = "") = execute(sender, stallId, period, from, to, "csv")

    private fun execute(sender: CommandSender, stallId: String, period: String, from: String, to: String, format: String) {
        val player = sender as? Player ?: return
        val guild = authorizedGuild(player, stallId) ?: run { player.sendMessage(lang.msg("accounting.denied")); return }
        val window = parseWindow(period, from, to) ?: run { player.sendMessage(lang.msg("accounting.usage")); return }
        query(player, stallId, guild, window, format)
    }

    private fun parseWindow(period: String, from: String, to: String): ShopHistoryWindow? = try {
        when (period.lowercase()) {
            "all" -> ShopHistoryWindow(0, Long.MAX_VALUE)
            "today" -> ShopHistoryDates.today(Clock.systemUTC(), ZoneId.systemDefault())
            "range" -> ShopHistoryDates.range(from, to, ZoneId.systemDefault())
            else -> null
        }
    } catch (failure: IllegalArgumentException) { null }

    private fun query(player: Player, stallId: String, guild: String, window: ShopHistoryWindow, format: String) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val rows = repository.report(guild, stallId, window)
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (!player.isOnline || Bukkit.getPlayer(player.uniqueId) !== player || authorizedGuild(player, stallId) != guild) return@Runnable
                    player.sendMessage(lang.msg("accounting.header", "stall" to stallId))
                    if (format == "csv") export(player, stallId, guild, rows) else rows.forEach { row ->
                        player.sendMessage(lang.msg("accounting.row", "contributor" to (row.contributor?.let { Bukkit.getOfflinePlayer(it).name ?: it.toString() } ?: lang.raw("accounting.unattributed")),
                            "item" to row.itemName, "stocked" to row.stocked, "sold" to row.quantity, "gross" to row.grossRevenue))
                    }
                })
            } catch (failure: Exception) {
                plugin.logger.warning("Guild sales query failed: ${failure.message}")
                Bukkit.getScheduler().runTask(plugin, Runnable { if (player.isOnline) player.sendMessage(lang.msg("accounting.failed")) })
            }
        })
    }

    private fun authorizedGuild(player: Player, stallId: String): String? {
        val stall = stalls.findById(StallId(stallId)) ?: return null
        val guild = stall.owner.id
        return guild.takeIf { stall.isActiveGuildStall() && guilds.isMember(player.uniqueId, guild) &&
            guilds.hasShopPermission(player.uniqueId, guild, GuildProvider.GuildPermission.MANAGE_SHOPS) }
    }

    private fun export(player: Player, stall: String, guild: String, rows: List<ContributorSales>) {
        val content = "contributor_uuid,item,net_stocked_units,sold_units,gross_customer_payment\n" + rows.joinToString("\n") {
            listOf(it.contributor?.toString() ?: "unattributed", it.itemName, it.stocked.toString(), it.quantity.toString(), it.grossRevenue.toString()).joinToString(",", transform = ::csvCell)
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val folder = plugin.dataFolder.toPath().resolve("accounting-exports"); Files.createDirectories(folder)
                val name = "guild-sales-${UUID.randomUUID()}.csv"
                Files.writeString(folder.resolve(name), content, Charsets.UTF_8)
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (player.isOnline && authorizedGuild(player, stall) == guild) player.sendMessage(lang.msg("accounting.exported", "file" to name))
                })
            } catch (failure: Exception) { plugin.logger.warning("Accounting export failed: ${failure.message}") }
        })
    }

    internal fun csvCell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || value.startsWith('\t') || value.startsWith('\r')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
