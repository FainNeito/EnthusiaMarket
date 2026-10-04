package net.badgersmc.em.infrastructure.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import net.badgersmc.em.interaction.gui.CreateShopMenu
import net.badgersmc.em.interaction.gui.PurchaseBulkMenu
import net.badgersmc.nexus.annotations.Component
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/** Intercepts chat for custom price input after the player clicks
 *  the "Custom Price" button in [CreateShopMenu].
 *
 *  Claims legacy chat before broadcasters such as RoseChat, with
 *  [AsyncChatEvent] as the Paper-only fallback. Cancels chat and schedules the
 *  actual menu opening on the main thread because [CreateShopMenu.open]
 *  fires [org.bukkit.event.inventory.InventoryOpenEvent] synchronously. */
@net.badgersmc.nexus.paper.listeners.Listener
@Component
open class ChatPriceListener(
    private val lang: LangService,
    private val plugin: Plugin,
) : Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onLegacyChat(event: AsyncPlayerChatEvent) {
        val task = inputTask(event.player, event.message) ?: return
        event.isCancelled = true
        Bukkit.getScheduler().runTask(plugin, task)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val message = PlainTextComponentSerializer.plainText().serialize(event.message())
        val task = inputTask(event.player, message) ?: return
        event.isCancelled = true
        Bukkit.getScheduler().runTask(plugin, task)
    }

    private fun inputTask(player: Player, message: String): Runnable? {
        // Check whether the player is waiting for a custom-price input.
        // handleChat removes from the pending set on first call, so we
        // must NOT call it on the async thread (would race with scheduling).
        return when {
            CreateShopMenu.isWaiting(player.uniqueId) -> Runnable {
                CreateShopMenu.handleChat(player, message, lang)
            }
            PurchaseBulkMenu.isWaiting(player.uniqueId) -> Runnable {
                PurchaseBulkMenu.handleChat(player, message, lang)
            }
            else -> null
        }
    }
}
