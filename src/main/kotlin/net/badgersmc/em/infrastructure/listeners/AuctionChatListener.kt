package net.badgersmc.em.infrastructure.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import net.badgersmc.em.application.AuctionLifecycleService
import net.badgersmc.em.interaction.AuctionBidUi
import net.badgersmc.em.interaction.gui.AuctionBidMenu
import net.badgersmc.nexus.annotations.Component
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventPriority
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/** Intercepts chat for custom auction bid amounts after the player clicks
 *  the "custom amount" button in [AuctionBidMenu]. */
@net.badgersmc.nexus.paper.listeners.Listener
@Component
open class AuctionChatListener(
    private val lang: LangService,
    private val auctionService: AuctionLifecycleService,
) : Listener {

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) { AuctionBidMenu.cancelPending(event.player) }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val message = PlainTextComponentSerializer.plainText().serialize(event.message())
        val continuation = AuctionBidMenu.claimChat(event.player, message, lang, auctionService) ?: return
        event.isCancelled = true
        AuctionBidUi.onMain(continuation)
    }
}
