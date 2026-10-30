package net.badgersmc.em.infrastructure.listeners

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.PacketEventsAPI
import com.github.retrooper.packetevents.manager.server.ServerVersion
import com.github.retrooper.packetevents.protocol.player.ClientVersion
import com.github.retrooper.packetevents.protocol.player.User
import com.github.retrooper.packetevents.protocol.world.waypoint.Vec3iWaypointInfo
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWaypoint
import io.mockk.*
import net.badgersmc.em.application.FinderTrailTracker.Point
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.test.*

class FinderLocatorTest {
    private val player = mockk<Player>(relaxed = true)
    private val observer = mockk<Player>(relaxed = true)
    private val packets = mutableListOf<Pair<Player, WrapperPlayServerWaypoint>>()
    private lateinit var locator: PacketEventsFinderLocator

    @BeforeTest fun setup() {
        mockkStatic(PacketEvents::class)
        val api = mockk<PacketEventsAPI<*>>(relaxed = true)
        every { PacketEvents.getAPI() } returns api
        every { api.serverManager.version } returns ServerVersion.V_26_2
        every { player.uniqueId } returns UUID.randomUUID()
        every { observer.uniqueId } returns UUID.randomUUID()
        every { player.isOnline } returns true
        locator = PacketEventsFinderLocator({ target, packet -> packets += target to packet }, { true }, { false })
    }

    @AfterTest fun cleanup() { unmockkStatic(PacketEvents::class) }

    @Test fun `track is private preserves artwork color and floors negative block coordinates`() {
        assertTrue(locator.show(player, Point(-2.5, 65.5, 8.5), "enthusiamarket:stall"))
        val (recipient, packet) = packets.single()
        assertSame(player, recipient)
        assertNotSame(observer, recipient)
        assertEquals(WrapperPlayServerWaypoint.Operation.TRACK, packet.operation)
        assertEquals(0xFFFFFF, packet.waypoint.icon.color!!.asRGB())
        assertEquals("enthusiamarket:stall", packet.waypoint.icon.style.toString())
        val position = (packet.waypoint.info as Vec3iWaypointInfo).position
        assertEquals(-3, position.x)
        assertEquals(65, position.y)
        verify(exactly = 0) { player.getAttribute(any()) }
    }

    @Test fun `unchanged waypoint is not resent and replacement and cancel remove only owned identifiers`() {
        val target = Point(3.0, 64.0, 8.0)
        locator.show(player, target, "minecraft:default")
        locator.show(player, target, "minecraft:default")
        assertEquals(1, packets.size)
        val original = packets[0].second.waypoint.identifier
        locator.show(player, target.copy(x = 10.0), "minecraft:default")
        assertEquals(listOf(WrapperPlayServerWaypoint.Operation.TRACK, WrapperPlayServerWaypoint.Operation.UNTRACK,
            WrapperPlayServerWaypoint.Operation.TRACK), packets.map { it.second.operation })
        assertEquals(original, packets[1].second.waypoint.identifier)
        assertNotEquals(original, packets[2].second.waypoint.identifier)
        locator.clear(player)
        locator.clear(player)
        assertEquals(4, packets.size)
        assertEquals(packets[2].second.waypoint.identifier, packets.last().second.waypoint.identifier)
        assertTrue(packets.all { it.first === player })
    }

    @Test fun `unsupported recipient produces no waypoint packets`() {
        val unsupported = PacketEventsFinderLocator({ target, packet -> packets += target to packet }, { false })
        assertFalse(unsupported.show(player, Point(0.0, 0.0, 0.0), "minecraft:default"))
        unsupported.clear(player)
        assertTrue(packets.isEmpty())
    }

    @Test fun `real compatibility gate excludes legacy Java and Floodgate recipients`() {
        val user = mockk<User>()
        every { PacketEvents.getAPI().playerManager.getUser(player) } returns user
        every { user.clientVersion } returns ClientVersion.V_1_21_5
        var bedrock = false
        val actual = PacketEventsFinderLocator(send = { target, packet -> packets += target to packet }, bedrock = { bedrock })
        assertFalse(actual.supported(player))
        every { user.clientVersion } returns ClientVersion.V_26_3
        bedrock = true
        assertFalse(actual.supported(player))
        bedrock = false
        assertTrue(actual.supported(player))
        assertTrue(packets.isEmpty())
    }

    @Test fun `disconnected recipient is forgotten without attempting to send`() {
        locator.show(player, Point(0.0, 65.0, 0.0), "minecraft:default")
        every { player.isOnline } returns false
        locator.clear(player)
        every { player.isOnline } returns true
        locator.clear(player)
        assertEquals(1, packets.size)
    }

    @Test fun `startup removes existing player markers without sending locations`() {
        locator.hideExistingPlayers(listOf(player, observer))
        assertEquals(4, packets.size)
        assertTrue(packets.all { it.second.operation == WrapperPlayServerWaypoint.Operation.UNTRACK })
        assertEquals(setOf(player.uniqueId, observer.uniqueId),
            packets.map { it.second.waypoint.identifier.left }.toSet())
    }

    @Test fun `failed track can be removed and retried instead of reported as successful`() {
        var fail = true
        val adapter = PacketEventsFinderLocator({ target, packet ->
            if (fail) throw IllegalStateException("transport failed")
            packets += target to packet
        }, { true }, { false })
        val point = Point(0.0, 65.0, 0.0)
        assertFailsWith<IllegalStateException> { adapter.show(player, point, "minecraft:default") }
        fail = false
        assertTrue(adapter.show(player, point, "minecraft:default"))
        assertEquals(listOf(WrapperPlayServerWaypoint.Operation.UNTRACK, WrapperPlayServerWaypoint.Operation.TRACK),
            packets.map { it.second.operation })
        adapter.clear(player)
        assertEquals(WrapperPlayServerWaypoint.Operation.UNTRACK, packets.last().second.operation)
    }
}
