package net.badgersmc.em.infrastructure.listeners

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.color.Color
import com.github.retrooper.packetevents.protocol.player.ClientVersion
import com.github.retrooper.packetevents.protocol.world.waypoint.TrackedWaypoint
import com.github.retrooper.packetevents.protocol.world.waypoint.Vec3iWaypointInfo
import com.github.retrooper.packetevents.protocol.world.waypoint.WaypointIcon
import com.github.retrooper.packetevents.resources.ResourceLocation
import com.github.retrooper.packetevents.util.Either
import com.github.retrooper.packetevents.util.Vector3i
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWaypoint
import net.badgersmc.em.application.FinderTrailTracker
import org.bukkit.entity.Player
import org.geysermc.floodgate.api.FloodgateApi
import java.util.UUID
import kotlin.math.floor

/** No listeners, broadcasts, entities, or changes to player tracking are required. */
internal class PacketEventsFinderLocator(
    private val send: (Player, WrapperPlayServerWaypoint) -> Unit = { player, packet ->
        PacketEvents.getAPI().playerManager.sendPacket(player, packet)
    },
    private val compatible: (Player) -> Boolean = { player -> javaClientSupported(player) },
    private val bedrock: (Player) -> Boolean = { player -> floodgatePlayer(player) },
) : FinderLocator {
    private data class Sent(val waypoint: TrackedWaypoint, val target: FinderTrailTracker.Point, val style: String)
    private val sent = mutableMapOf<UUID, Sent>()

    override fun supported(player: Player): Boolean = compatible(player) && !bedrock(player)

    override fun show(player: Player, target: FinderTrailTracker.Point, style: String): Boolean {
        if (!supported(player)) return false
        val previous = sent[player.uniqueId]
        if (previous?.target == target && previous.style == style) return true
        clear(player)
        val waypoint = TrackedWaypoint(Either.createLeft(UUID.randomUUID()),
            WaypointIcon(ResourceLocation(style), if (style == VANILLA_STYLE) Color(GOLD) else Color.WHITE),
            Vec3iWaypointInfo(Vector3i(floor(target.x).toInt(), floor(target.y).toInt(), floor(target.z).toInt())))
        send(player, WrapperPlayServerWaypoint(WrapperPlayServerWaypoint.Operation.TRACK, waypoint))
        sent[player.uniqueId] = Sent(waypoint, target, style)
        return true
    }

    override fun clear(player: Player) {
        val previous = sent[player.uniqueId] ?: return
        if (player.isOnline) {
            send(player, WrapperPlayServerWaypoint(WrapperPlayServerWaypoint.Operation.UNTRACK, previous.waypoint))
        }
        sent.remove(player.uniqueId)
    }

    companion object {
        const val VANILLA_STYLE = "minecraft:default"
        private const val GOLD = 0xFFC857

        private fun javaClientSupported(player: Player): Boolean {
            val version = PacketEvents.getAPI().playerManager.getUser(player)?.clientVersion ?: return false
            return version.isNewerThanOrEquals(ClientVersion.V_1_21_6)
        }

        private fun floodgatePlayer(player: Player): Boolean =
            try {
                FloodgateApi.getInstance().isFloodgatePlayer(player.uniqueId)
            } catch (_: LinkageError) {
                false
            } catch (_: IllegalStateException) {
                false
            }
    }
}
