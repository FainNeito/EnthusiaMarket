package net.badgersmc.em.infrastructure.listeners

import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWaypoint
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class FinderWaypointPrivacy(
    private val read: (PacketSendEvent) -> WrapperPlayServerWaypoint = ::WrapperPlayServerWaypoint,
) : PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
    // Packet callbacks run off-thread. Never access Bukkit or mutable navigation sessions here.
    private val destinations = ConcurrentHashMap<UUID, UUID>()

    fun select(viewer: UUID, destination: UUID) { destinations[viewer] = destination }
    fun forget(viewer: UUID) { destinations.remove(viewer) }

    override fun onPacketSend(event: PacketSendEvent) {
        if (event.packetType != PacketType.Play.Server.WAYPOINT) return
        val packet = try {
            read(event)
        } catch (_: RuntimeException) {
            event.isCancelled = true
            return
        }
        if (packet.operation == WrapperPlayServerWaypoint.Operation.UNTRACK) return
        if (!selected(event.user.uuid, packet.waypoint.identifier)) event.isCancelled = true
    }

    private fun selected(viewer: UUID, identifier: com.github.retrooper.packetevents.util.Either<UUID, String>): Boolean {
        val destination = destinations[viewer]
        return destination != null && identifier.isLeft && identifier.left == destination
    }
}
