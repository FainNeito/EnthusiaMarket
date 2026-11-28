package net.badgersmc.em.infrastructure.bedrock

import net.badgersmc.nexus.annotations.Component
import org.bukkit.entity.Player
import org.geysermc.floodgate.api.FloodgateApi

/**
 * Detects whether a player is on Bedrock edition via Floodgate API.
 */
@Component
class PlatformDetectionService {

    fun isBedrockPlayer(player: Player): Boolean {
        return detectFloodgatePlayer {
            FloodgateApi.getInstance().isFloodgatePlayer(player.uniqueId)
        }
    }

    internal fun detectFloodgatePlayer(detection: () -> Boolean): Boolean {
        return try {
            detection()
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            // Floodgate is an optional companion plugin. Missing API classes
            // must fall back to the Java menu instead of breaking sign use.
            false
        }
    }

    fun isCumulusAvailable(): Boolean {
        return try {
            Class.forName("org.geysermc.cumulus.form.Form")
            true
        } catch (_: Exception) {
            false
        }
    }
}
