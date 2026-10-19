package net.badgersmc.em.infrastructure.listeners

import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.world.waypoint.TrackedWaypoint
import com.github.retrooper.packetevents.util.Either
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWaypoint
import io.mockk.*
import java.util.UUID
import kotlin.test.*

class FinderWaypointPrivacyTest {
    private val viewer = UUID.randomUUID()
    private val stall = UUID.randomUUID()
    private val event = mockk<PacketSendEvent>(relaxed = true)
    private val packet = mockk<WrapperPlayServerWaypoint>()
    private val waypoint = mockk<TrackedWaypoint>()
    private val filter = FinderWaypointPrivacy { packet }

    @BeforeTest fun setup() {
        every { event.packetType } returns PacketType.Play.Server.WAYPOINT
        every { event.user.uuid } returns viewer
        every { packet.waypoint } returns waypoint
        every { packet.operation } returns WrapperPlayServerWaypoint.Operation.TRACK
        every { waypoint.identifier } returns Either.createLeft(stall)
    }

    @Test fun `idle players cannot receive any tracking waypoint`() {
        filter.onPacketSend(event)
        verify { event.isCancelled = true }
    }

    @Test fun `active destination is private and player updates remain blocked`() {
        filter.select(viewer, stall)
        filter.onPacketSend(event)
        verify(exactly = 0) { event.isCancelled = true }
        every { waypoint.identifier } returns Either.createLeft(UUID.randomUUID())
        every { packet.operation } returns WrapperPlayServerWaypoint.Operation.UPDATE
        filter.onPacketSend(event)
        verify { event.isCancelled = true }
    }

    @Test fun `another viewer cannot receive the selected destination`() {
        filter.select(UUID.randomUUID(), stall)
        filter.onPacketSend(event)
        verify { event.isCancelled = true }
    }

    @Test fun `cancelled destination cannot be retracked but removals always pass`() {
        filter.select(viewer, stall)
        filter.forget(viewer)
        filter.onPacketSend(event)
        verify { event.isCancelled = true }
        clearMocks(event, answers = false)
        every { packet.operation } returns WrapperPlayServerWaypoint.Operation.UNTRACK
        filter.onPacketSend(event)
        verify(exactly = 0) { event.isCancelled = true }
    }

    @Test fun `named and replaced waypoints cannot bypass the allowlist`() {
        filter.select(viewer, stall)
        every { waypoint.identifier } returns Either.createRight("stall")
        filter.onPacketSend(event)
        verify { event.isCancelled = true }
        clearMocks(event, answers = false)
        every { waypoint.identifier } returns Either.createLeft(stall)
        filter.select(viewer, UUID.randomUUID())
        filter.onPacketSend(event)
        verify { event.isCancelled = true }
    }

    @Test fun `non waypoint traffic is untouched`() {
        every { event.packetType } returns PacketType.Play.Server.KEEP_ALIVE
        filter.onPacketSend(event)
        verify(exactly = 0) { event.isCancelled = true }
        verify(exactly = 0) { packet.operation }
    }

    @Test fun `unreadable waypoint fails closed`() {
        FinderWaypointPrivacy { throw IllegalArgumentException("invalid waypoint") }.onPacketSend(event)
        verify { event.isCancelled = true }
    }
}
