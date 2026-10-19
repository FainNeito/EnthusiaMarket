package net.badgersmc.em.infrastructure.listeners

import org.bukkit.entity.Player
import org.geysermc.floodgate.api.FloodgateApi

internal object FinderClientSupport {
    fun glow(player: Player): Boolean = !bedrock(player)

    fun bedrock(player: Player): Boolean = try {
        FloodgateApi.getInstance()?.isFloodgatePlayer(player.uniqueId) == true
    } catch (_: LinkageError) {
        false
    } catch (_: IllegalStateException) {
        false
    }
}
