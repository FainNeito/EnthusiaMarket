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
import net.badgersmc.em.interaction.StallAccessUi
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
    private val guildNames = mutableMapOf<String, String>()
    private val labels get() = StallAccessUi.labels

    private data class Choice(val title: String, val select: () -> Unit)

    fun open(player: Player, id: String, section: String = "flags", page: Int = 0) {
        val stall = index.cached(id) ?: return
        if (!service.mayManage(stall, player.uniqueId)) { player.closeInventory(); return }
        val settings = service.current(stall)
        val choices = choices(player, id, section, settings)
        val last = ((choices.size - 1).coerceAtLeast(0)) / 45
        val current = page.coerceIn(0, last)
        val gui = ChestGui(6, labels.title(id, section, guildNames[section.removePrefix("ally:")]))
        val pane = OutlinePane(9, 6)
        choices.drop(current * 45).take(45).forEach { choice ->
            pane.addItem(button(choice.title) { choice.select() })
        }
        addControls(pane, player, id, section to current)
        gui.addPane(com.github.stefvanschie.inventoryframework.pane.util.Slot.fromXY(0, 0), pane)
        gui.blockItemTheft(); gui.show(player)
    }

    private fun addControls(pane: OutlinePane, player: Player, id: String, navigation: Pair<String, Int>) {
        val (section, current) = navigation
        pane.addItem(button(labels.text("navigation.previous")) { open(player, id, section, current - 1) })
        pane.addItem(button(labels.text("navigation.next")) { open(player, id, section, current + 1) })
        pane.addItem(button(labels.text("navigation.flags")) { open(player, id) })
        pane.addItem(button(labels.text("navigation.effects")) { open(player, id, "effects") })
        pane.addItem(button(labels.text("navigation.blacklist")) { open(player, id, "blacklist") })
        pane.addItem(button(labels.text("navigation.allies")) { open(player, id, "allies") })
    }

    private fun choices(player: Player, id: String, section: String, settings: StallAccessSettings): List<Choice> = when {
        section == "effects" -> PotionEffectType.values().sortedBy { it.name }.map { type ->
            Choice(labels.text("effect.state", "name" to labels.effect(type.name), "state" to labels.state(type.name !in settings.blockedEffects))) {
                mutate(player, id, section) { it.copy(blockedEffects = toggle(it.blockedEffects, type.name)) }
            }
        }
        section == "blacklist" -> listOf(Choice(labels.text("blacklist.add")) {
            player.closeInventory(); player.sendMessage(labels.text("blacklist.command", "stall" to id))
        }) + settings.blacklist.sortedBy { it.toString() }.mapIndexed { number, uuid ->
            val name = labels.player(org.bukkit.Bukkit.getOfflinePlayer(uuid).name, number + 1)
            Choice(labels.text("player.unblock", "name" to name)) {
                mutate(player, id, section) { it.copy(blacklist = it.blacklist - uuid) }
            }
        }
        section == "allies" -> {
            guildNames.clear()
            guilds.listGuilds().filter { guild ->
                val stall = index.cached(id)
                stall != null && (guilds.areAllied(stall.owner.id, guild.id) || guild.id in settings.allies)
            }.mapIndexed { number, guild ->
                val name = labels.guild(guild.name, number + 1)
                guildNames[guild.id] = name
                Choice(labels.text("guild.configure", "name" to name)) { open(player, id, "ally:${guild.id}") }
            }
        }
        section.startsWith("ally:") -> StallCapability.entries.map { capability ->
            val ally = section.removePrefix("ally:")
            Choice(labels.text("capability.state", "name" to labels.capability(capability.name), "state" to labels.state(capability in settings.allies[ally].orEmpty()))) {
                mutate(player, id, section) {
                    val grants = toggle(it.allies[ally].orEmpty(), capability)
                    it.copy(allies = if (grants.isEmpty()) it.allies - ally else it.allies + (ally to grants))
                }
            }
        }
        else -> StallCapability.entries.filter { it !in StallAccessSettings.PRIVILEGED }.map { capability ->
            Choice(labels.text("visitor.capability", "name" to labels.capability(capability.name), "state" to labels.state(settings.visitorAllows(capability)))) {
                mutate(player, id, section) { it.copy(visitorFlags = it.visitorFlags + (capability to !it.visitorAllows(capability))) }
            }
        } + Choice(labels.text("potions.incoming", "state" to labels.state(settings.allowIncomingPotions))) {
            mutate(player, id, section) { it.copy(allowIncomingPotions = !it.allowIncomingPotions) }
        }
    }

    private fun mutate(player: Player, id: String, section: String, change: (StallAccessSettings) -> StallAccessSettings) {
        runCatching { service.edit(player.uniqueId, id, change) }
            .onSuccess { policy -> index.cached(id)?.let { projection.apply(it, policy) } }
            .onFailure { player.sendMessage(StallAccessUi.failure("menu stall=$id actor=${player.uniqueId}", it)) }
        open(player, id, section)
    }

    private fun button(title: String, select: () -> Unit): GuiItem {
        val item = ItemStack(Material.PAPER)
        item.editMeta { it.displayName(Component.text(title)) }
        return GuiItem(item) { event -> event.isCancelled = true; if (event.isLeftClick) select() }
    }

    private fun <T> toggle(values: Set<T>, value: T): Set<T> = if (value in values) values - value else values + value
}
