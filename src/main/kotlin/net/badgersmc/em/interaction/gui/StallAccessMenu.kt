package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import net.badgersmc.em.application.StallAccessIndex
import net.badgersmc.em.application.StallAccessSettingsService
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.stall.StallAccessSettings
import net.badgersmc.em.domain.stall.StallCapability
import net.badgersmc.em.infrastructure.listeners.StallAccessProjection
import net.badgersmc.em.interaction.blockItemTheft
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffectType

/** Every click revalidates current ownership/rank and durable moderation/revision before writing. */
class StallAccessMenu(
    private val index: StallAccessIndex,
    private val service: StallAccessSettingsService,
    private val guilds: GuildProvider,
    private val projection: StallAccessProjection,
) {
    private data class Choice(val title: String, val select: () -> Unit)

    fun open(player: Player, id: String, section: String = "flags", page: Int = 0) {
        val stall = index.cached(id) ?: return
        if (!service.mayManage(stall, player.uniqueId)) { player.closeInventory(); return }
        val settings = service.current(stall)
        val choices = choices(player, id, section, settings)
        val last = ((choices.size - 1).coerceAtLeast(0)) / 45
        val current = page.coerceIn(0, last)
        val gui = ChestGui(6, "Stall $id: $section")
        val pane = OutlinePane(9, 6)
        choices.drop(current * 45).take(45).forEach { choice ->
            pane.addItem(button(choice.title) { choice.select() })
        }
        addControls(pane, player, id, section, current)
        gui.addPane(com.github.stefvanschie.inventoryframework.pane.util.Slot.fromXY(0, 0), pane)
        gui.blockItemTheft(); gui.show(player)
    }

    private fun addControls(pane: OutlinePane, player: Player, id: String, section: String, current: Int) {
        pane.addItem(button("Previous page") { open(player, id, section, current - 1) })
        pane.addItem(button("Next page") { open(player, id, section, current + 1) })
        pane.addItem(button("Visitor flags") { open(player, id) })
        pane.addItem(button("Blocked potion effects") { open(player, id, "effects") })
        pane.addItem(button("Player blacklist") { open(player, id, "blacklist") })
        pane.addItem(button("Select allied guilds (off by default)") { open(player, id, "allies") })
    }

    private fun choices(player: Player, id: String, section: String, settings: StallAccessSettings): List<Choice> = when {
        section == "effects" -> PotionEffectType.values().sortedBy { it.name }.map { type ->
            Choice("${type.name}: ${if (type.name in settings.blockedEffects) "BLOCKED" else "allowed"}") {
                mutate(player, id, section) { it.copy(blockedEffects = toggle(it.blockedEffects, type.name)) }
            }
        }
        section == "blacklist" -> listOf(Choice("Add: /stallaccess blacklist $id <player> true") {
            player.closeInventory(); player.sendMessage("/stallaccess blacklist $id <player> true")
        }) + settings.blacklist.sortedBy { it.toString() }.map { uuid ->
            Choice("Unblock ${org.bukkit.Bukkit.getOfflinePlayer(uuid).name ?: uuid}") {
                mutate(player, id, section) { it.copy(blacklist = it.blacklist - uuid) }
            }
        }
        section == "allies" -> guilds.listGuilds().filter { guild ->
            val stall = index.cached(id)
            stall != null && (guilds.areAllied(stall.owner.id, guild.id) || guild.id in settings.allies)
        }.map { guild -> Choice("${guild.name}: configure access") { open(player, id, "ally:${guild.id}") } }
        section.startsWith("ally:") -> StallCapability.entries.map { capability ->
            val ally = section.removePrefix("ally:")
            Choice("$capability: ${capability in settings.allies[ally].orEmpty()}") {
                mutate(player, id, section) {
                    val grants = toggle(it.allies[ally].orEmpty(), capability)
                    it.copy(allies = if (grants.isEmpty()) it.allies - ally else it.allies + (ally to grants))
                }
            }
        }
        else -> StallCapability.entries.filter { it !in StallAccessSettings.PRIVILEGED }.map { capability ->
            Choice("Visitors $capability: ${settings.visitorAllows(capability)}") {
                mutate(player, id, section) { it.copy(visitorFlags = it.visitorFlags + (capability to !it.visitorAllows(capability))) }
            }
        } + Choice("Incoming splash/lingering potions: ${settings.allowIncomingPotions}") {
            mutate(player, id, section) { it.copy(allowIncomingPotions = !it.allowIncomingPotions) }
        }
    }

    private fun mutate(player: Player, id: String, section: String, change: (StallAccessSettings) -> StallAccessSettings) {
        runCatching { service.edit(player.uniqueId, id, change) }
            .onSuccess { policy -> index.cached(id)?.let { projection.apply(it, policy) } }
            .onFailure { player.sendMessage("Could not confirm stall setting: ${it.message}") }
        open(player, id, section)
    }

    private fun button(title: String, select: () -> Unit): GuiItem {
        val item = ItemStack(Material.PAPER)
        item.editMeta { it.displayName(Component.text(title)) }
        return GuiItem(item) { event -> event.isCancelled = true; if (event.isLeftClick) select() }
    }

    private fun <T> toggle(values: Set<T>, value: T): Set<T> = if (value in values) values - value else values + value
}
