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
import java.util.UUID
import kotlin.math.floor

/** Private stall transport with a server-wide, per-recipient waypoint allowlist. */
internal class PacketEventsFinderLocator(
    private val send: (Player, WrapperPlayServerWaypoint) -> Unit = { player, packet ->
        PacketEvents.getAPI().playerManager.sendPacket(player, packet)
    },
    private val compatible: (Player) -> Boolean = { player -> javaClientSupported(player) },
    private val bedrock: (Player) -> Boolean = FinderClientSupport::bedrock,
    internal val privacy: FinderWaypointPrivacy = FinderWaypointPrivacy(),
) : FinderLocator {
    private data class Sent(val waypoint: TrackedWaypoint, val target: FinderTrailTracker.Point, val style: String,
                            var delivered: Boolean = false)
    private val sent = mutableMapOf<UUID, Sent>()

    override fun supported(player: Player): Boolean = compatible(player) && !bedrock(player)

    override fun show(player: Player, target: FinderTrailTracker.Point, style: String): Boolean {
        if (!supported(player)) return false
        val previous = sent[player.uniqueId]
        if (unchanged(previous, target, style)) return true
        clear(player)
        val identifier = UUID.randomUUID()
        val waypoint = TrackedWaypoint(Either.createLeft(identifier),
            WaypointIcon(ResourceLocation(style), if (style == VANILLA_STYLE) Color(GOLD) else Color.WHITE),
            Vec3iWaypointInfo(Vector3i(floor(target.x).toInt(), floor(target.y).toInt(), floor(target.z).toInt())))
        val pending = Sent(waypoint, target, style)
        sent[player.uniqueId] = pending
        privacy.select(player.uniqueId, identifier)
        send(player, WrapperPlayServerWaypoint(WrapperPlayServerWaypoint.Operation.TRACK, waypoint))
        pending.delivered = true
        return true
    }

    private fun unchanged(previous: Sent?, target: FinderTrailTracker.Point, style: String): Boolean =
        previous != null && previous.target == target && previous.style == style && previous.delivered

    override fun clear(player: Player) {
        privacy.forget(player.uniqueId)
        val previous = sent[player.uniqueId] ?: return
        if (player.isOnline) {
            send(player, WrapperPlayServerWaypoint(WrapperPlayServerWaypoint.Operation.UNTRACK, previous.waypoint))
        }
        sent.remove(player.uniqueId)
    }

    /** Clear markers sent before Market registered its filter, without changing server tracking state. */
    fun hideExistingPlayers(players: Collection<Player>) {
        players.filter(::supported).forEach { viewer ->
            players.forEach { target ->
                val waypoint = TrackedWaypoint(Either.createLeft(target.uniqueId),
                    WaypointIcon(ResourceLocation(VANILLA_STYLE), Color.WHITE),
                    Vec3iWaypointInfo(Vector3i(0, 0, 0)))
                send(viewer, WrapperPlayServerWaypoint(WrapperPlayServerWaypoint.Operation.UNTRACK, waypoint))
            }
        }
    }

    override fun close() { PacketEvents.getAPI().eventManager.unregisterListener(privacy) }

    companion object {
        const val VANILLA_STYLE = "minecraft:default"
        private const val GOLD = 0xFFC857

        private fun javaClientSupported(player: Player): Boolean {
            val version = PacketEvents.getAPI().playerManager.getUser(player)?.clientVersion ?: return false
            return version.isNewerThanOrEquals(ClientVersion.V_1_21_6)
        }

    }
}
