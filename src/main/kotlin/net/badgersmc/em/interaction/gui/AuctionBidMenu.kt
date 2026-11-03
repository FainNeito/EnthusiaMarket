package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import com.github.stefvanschie.inventoryframework.pane.util.Slot
import net.badgersmc.em.application.AuctionLifecycleService
import net.badgersmc.em.application.AuctionResult
import net.badgersmc.em.domain.auction.Auction
import net.badgersmc.em.interaction.AuctionBidPresentation
import net.badgersmc.em.interaction.AuctionBidSession
import net.badgersmc.em.interaction.AuctionBidUi
import net.badgersmc.em.interaction.Menu
import net.badgersmc.em.interaction.bedrock.BedrockAuctionBidForm
import net.badgersmc.em.interaction.blockItemTheft
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AuctionBidMenu(private val auction: Auction, private val auctionService: AuctionLifecycleService,
    private val lang: LangService,
) : Menu {
    private var guildTarget: String? = null
    private var actor: UUID? = null
    private var viewVersion = 0L

    override fun open(player: Player) {
        if (!allowed(player)) return
        cancelPrompt(player.uniqueId)
        val current = auctionService.auctionForBid(auction.id) ?: return unavailable(player)
        val guilds = auctionService.eligibleGuilds(player.uniqueId)
        val selected = guilds.firstOrNull { it.id == guildTarget }
        val payer = if (guildTarget == null) text("personal") else selected?.let {
            text("guild", "name" to AuctionBidPresentation.name(it.name))
        } ?: text("guild_unavailable")
        val minimum = AuctionBidPresentation.minimum(current.startingBid, current.highBid?.amount)
        val summary = summary(current, payer, minimum)
        val actions = mutableListOf<Pair<String, () -> Unit>>(
            text("select_personal") to { guildTarget = null; open(player) },
            text("choose_guild") to { chooseGuild(player, 0) },
        )
        actions += text("custom") to { prompt(player, current) }
        if (minimum != null) AuctionBidPresentation.amounts(minimum).forEach { amount ->
            actions += text("amount", "amount" to amount) to { review(player, amount, guildTarget) }
        }
        actions += text("close") to { player.closeInventory() }
        show(player, text("title", "stall" to current.stallId.value), summary, actions)
    }

    private fun chooseGuild(player: Player, requested: Int) {
        if (!allowed(player)) return
        val guilds = auctionService.eligibleGuilds(player.uniqueId).sortedBy { it.id }
        val pages = maxOf(1, (guilds.size - 1).coerceAtLeast(0) / 45 + 1)
        val page = requested.coerceIn(0, pages - 1)
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        val slots = mutableListOf<Int>()
        guilds.drop(page * 45).take(45).forEachIndexed { index, guild ->
            actions += AuctionBidPresentation.name(guild.name) to { guildTarget = guild.id; open(player) }
            slots += index
        }
        if (page > 0) { actions += text("previous") to { chooseGuild(player, page - 1) }; slots += 45 }
        if (page + 1 < pages) { actions += text("next") to { chooseGuild(player, page + 1) }; slots += 51 }
        actions += text("back") to { open(player) }; slots += 53
        show(player, text("choose_guild"), listOf(text("page", "page" to page + 1, "pages" to pages)), actions, slots)
    }

    private fun summary(current: Auction, payer: String, minimum: Long?): List<String> = listOf(
        payer,
        text(if (guildTarget == null) "personal_funding" else "guild_funding"),
        text(if (current.highBid == null) "starting" else "current", "amount" to (current.highBid?.amount ?: current.startingBid)),
        text("minimum", "amount" to minimumLabel(minimum)),
        text("time", "time" to timeDescription(current)),
    )

    private fun minimumLabel(minimum: Long?): String = minimum?.toString() ?: text("no_higher_bid")

    private fun timeDescription(current: Auction): String = AuctionBidPresentation.time(
        Instant.now(), current.endAt, current.highBid == null && !current.auctionDuration.isZero)

    private fun review(player: Player, amount: Long, payer: String?) {
        if (!allowed(player)) return
        val session = AuctionBidSession(player.uniqueId, auction.id, auctionService)
        when (val result = session.review(amount, payer)) {
            is AuctionBidSession.Rejected -> { player.sendMessage(Component.text(result.reason)); open(player) }
            is AuctionBidSession.Ready -> showReview(player, session, result)
        }
    }

    private fun showReview(player: Player, session: AuctionBidSession, ready: AuctionBidSession.Ready) {
        val payer = ready.guildName?.let { text("guild", "name" to it) } ?: text("personal")
        val lines = summary(ready.auction, payer, AuctionBidPresentation.minimum(ready.auction.startingBid, ready.auction.highBid?.amount)) + listOf(
            text("full_bid", "amount" to ready.amount),
            text("destination", "stall" to ready.auction.stallId.value, "payer" to payer), text("charge_note"))
        show(player, text("review_title"), lines, listOf(
            text("confirm") to {
                if (allowed(player)) {
                    val result = session.confirm(ready, player.address?.address?.hostAddress ?: "unknown")
                    player.sendMessage(formatBidResult(result, ready.amount, lang))
                    player.closeInventory()
                }
            },
            text("back") to { open(player) },
        ))
    }

    private fun allowed(player: Player): Boolean {
        if (actor == null) actor = player.uniqueId
        if (actor == player.uniqueId && player.hasPermission(BID_PERMISSION)) return true
        player.sendMessage(lang.msg("gui.auction_bid.no_permission"))
        player.closeInventory()
        return false
    }

    private fun unavailable(player: Player) {
        player.sendMessage(lang.msg("admin.bid.not_found"))
        player.closeInventory()
    }

    private fun show(player: Player, title: String, lines: List<String>, actions: List<Pair<String, () -> Unit>>, slotPositions: List<Int>? = null) {
        val version = ++viewVersion
        val epoch = AuctionBidUi.generation
        val guarded = actions.map { (label, action) -> label to {
            if (version == viewVersion && epoch == AuctionBidUi.generation && player.isOnline) action()
        } }
        if (AuctionBidUi.useBedrock(player)) {
            BedrockAuctionBidForm(player, title, lines.joinToString("\n"), guarded, lang).open(player)
            return
        }
        val rows = if (slotPositions == null) 3 else 6
        val gui = ChestGui(rows, ComponentHolder.of(Component.text(title)))
        val pane = StaticPane(9, rows)
        pane.addItem(GuiItem(icon(Material.PAPER, if (slotPositions == null) title else lines.joinToString(" "), lines)) { it.isCancelled = true }, 4, if (slotPositions == null) 0 else 5)
        for ((index, action) in guarded.withIndex()) {
            val slot = if (slotPositions == null) 9 + index else slotPositions[index]
            pane.addItem(GuiItem(icon(Material.GOLD_INGOT, action.first)) { event ->
                event.isCancelled = true
                if ((event.whoClicked as? Player)?.uniqueId == actor) action.second()
            }, slot % 9, slot / 9)
        }
        gui.addPane(Slot.fromXY(0, 0), pane)
        gui.blockItemTheft()
        gui.show(player)
    }

    private fun icon(material: Material, label: String, lore: List<String> = emptyList()): ItemStack =
        ItemStack(material).also { item ->
            item.itemMeta = item.itemMeta?.apply {
                displayName(Component.text(label).decoration(TextDecoration.ITALIC, false))
                lore(lore.map { Component.text(it) })
            }
        }

    private fun text(key: String, vararg args: Pair<String, Any>): String {
        val values = args.toMap()
        return lang.raw("auction_payer.$key").replace(Regex("\\{(\\w+)\\}")) {
            values[it.groupValues[1]]?.toString() ?: it.value
        }
    }

    private fun prompt(player: Player, current: Auction) {
        if (!allowed(player)) return
        player.closeInventory()
        synchronized(promptLock) {
            pendingCustomBids[player.uniqueId] = current to Instant.now()
            promptTokens[player.uniqueId] = UUID.randomUUID()
            promptPlayers[player.uniqueId] = player
            if (guildTarget == null) pendingGuildTargets.remove(player.uniqueId)
            else pendingGuildTargets[player.uniqueId] = guildTarget!!
        }
        player.sendMessage(lang.msg("gui.auction_bid.custom_prompt", "stall" to current.stallId.value))
    }

    companion object {
        const val BID_PERMISSION = "enthusiamarket.auction.bid"
        private const val CUSTOM_BID_TIMEOUT_SEC = 30L
        val pendingCustomBids: ConcurrentHashMap<UUID, Pair<Auction, Instant>> = ConcurrentHashMap()
        private val pendingGuildTargets = ConcurrentHashMap<UUID, String>()
        private val promptTokens = ConcurrentHashMap<UUID, UUID>()
        private val promptPlayers = ConcurrentHashMap<UUID, Player>()
        private val promptLock = Any()

        private fun cancelPrompt(actor: UUID) = synchronized(promptLock) {
            pendingCustomBids.remove(actor)
            pendingGuildTargets.remove(actor)
            promptTokens.remove(actor)
            promptPlayers.remove(actor)
            Unit
        }

        fun cancelPending(player: Player) = synchronized(promptLock) {
            if (promptPlayers[player.uniqueId] === player) cancelPrompt(player.uniqueId)
        }

        fun clearPending() = synchronized(promptLock) {
            pendingCustomBids.clear(); pendingGuildTargets.clear(); promptTokens.clear(); promptPlayers.clear()
        }

        private data class ChatClaim(
            val promptedPlayer: Player,
            val entry: Pair<Auction, Instant>,
            val token: UUID,
        )

        /** Claim once on the chat thread; the returned continuation must run on main. */
        internal fun claimChat(player: Player, message: String, lang: LangService,
            auctionService: AuctionLifecycleService): (() -> Unit)? = synchronized(promptLock) {
            val entry = pendingCustomBids.remove(player.uniqueId) ?: return@synchronized null
            val promptedPlayer = promptPlayers.getOrPut(player.uniqueId) { player }
            val guild = pendingGuildTargets.remove(player.uniqueId)
            val token = promptTokens.getOrPut(player.uniqueId) { UUID.randomUUID() }
            val continuation: () -> Unit = {
                completeChat(player, ChatClaim(promptedPlayer, entry, token), lang) {
                    reviewChatAmount(player, message, AuctionBidMenu(entry.first, auctionService, lang), guild)
                }
            }
            continuation
        }

        private fun completeChat(player: Player, claim: ChatClaim, lang: LangService, review: () -> Unit) {
            if (!promptTokens.remove(player.uniqueId, claim.token)) return
            promptPlayers.remove(player.uniqueId, claim.promptedPlayer)
            if (claim.promptedPlayer !== player || !player.isOnline) return
            if (java.time.Duration.between(claim.entry.second, Instant.now()).seconds > CUSTOM_BID_TIMEOUT_SEC) {
                player.sendMessage(lang.msg("gui.auction_bid.custom_timeout", "stall" to claim.entry.first.stallId.value))
                return
            }
            review()
        }

        private fun reviewChatAmount(player: Player, message: String, menu: AuctionBidMenu, guild: String?) {
            val amount = message.trim().toLongOrNull()?.takeIf { it > 0 }
            if (amount == null) {
                player.sendMessage(menu.lang.msg("gui.auction_bid.custom_invalid", "input" to message))
                return
            }
            menu.guildTarget = guild
            menu.review(player, amount, guild)
        }

        /** Compatible entrypoint; only claims/schedules, never opens inventories on async. */
        fun handleChat(player: Player, message: String, lang: LangService, auctionService: AuctionLifecycleService): Boolean {
            val action = claimChat(player, message, lang, auctionService) ?: return false
            AuctionBidUi.onMain(action)
            return true
        }

        private fun formatBidResult(result: AuctionResult, amount: Long, lang: LangService) = when (result) {
            is AuctionResult.Success -> lang.msg("admin.bid.success", "amount" to (result.auction.highBid?.amount ?: amount),
                "stall" to result.auction.stallId.value)
            is AuctionResult.Failure -> lang.msg("admin.bid.failure", "reason" to result.reason)
            is AuctionResult.NotFound -> lang.msg("admin.bid.not_found")
        }
    }
}
