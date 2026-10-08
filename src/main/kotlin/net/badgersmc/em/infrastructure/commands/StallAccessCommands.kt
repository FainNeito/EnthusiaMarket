package net.badgersmc.em.infrastructure.commands

import net.badgersmc.em.application.StallAccessIndex
import net.badgersmc.em.application.StallAccessSettingsService
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.infrastructure.bedrock.PlayerNameResolver
import net.badgersmc.em.infrastructure.listeners.StallAccessProjection
import net.badgersmc.em.interaction.gui.StallAccessMenu
import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import org.bukkit.entity.Player

@Command(name = "stallaccess", description = "Configure your stall access")
class StallAccessCommands(
    private val index: StallAccessIndex,
    private val service: StallAccessSettingsService,
    private val guilds: GuildProvider,
    private val names: PlayerNameResolver,
    private val projection: StallAccessProjection,
) {
    @Subcommand("settings <stall>")
    @Permission("enthusiamarket.shop.use")
    fun settings(@Context player: Player, @Arg("stall") id: String) {
        runCatching { service.refreshIfUncertain(id) }
        StallAccessMenu(index, service, guilds, projection).open(player, id)
    }

    @Subcommand("blacklist <stall> <player> <blocked>")
    @Permission("enthusiamarket.shop.use")
    fun blacklist(@Context actor: Player, @Arg("stall") id: String, @Arg("player") name: String, @Arg("blocked") blocked: String) {
        val stall = index.cached(id) ?: return
        if (!service.mayManage(stall, actor.uniqueId)) { actor.sendMessage("You cannot manage this stall."); return }
        val value = blocked.toBooleanStrictOrNull() ?: run { actor.sendMessage("Use true or false."); return }
        val target = names.resolve(name) ?: run { actor.sendMessage("Player not found."); return }
        persistBlacklist(actor, id, target.uniqueId, value)
    }

    private fun persistBlacklist(actor: Player, id: String, target: java.util.UUID, value: Boolean) {
        runCatching { service.edit(actor.uniqueId, id) {
            it.copy(blacklist = if (value) it.blacklist + target else it.blacklist - target)
        } }.onSuccess { actor.sendMessage("Stall blacklist updated.") }
            .onFailure { actor.sendMessage("Could not confirm stall setting: ${it.message}") }
    }
}
