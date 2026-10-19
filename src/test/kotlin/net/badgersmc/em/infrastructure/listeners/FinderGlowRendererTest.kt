package net.badgersmc.em.infrastructure.listeners

import io.mockk.*
import net.badgersmc.em.application.FinderTrailTracker
import net.badgersmc.em.config.EnthusiaMarketConfig
import org.bukkit.*
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import kotlin.test.*

class FinderGlowRendererTest {
    private val world = mockk<World>(relaxed = true)
    private val player = mockk<Player>(relaxed = true)
    private val observer = mockk<Player>(relaxed = true)
    private val plugin = mockk<Plugin>(relaxed = true)
    private val config = EnthusiaMarketConfig.FinderOutline()
    private val target = FinderTrailTracker.Point(4.5, 65.5, 4.5)
    private val frame = FinderTrailTracker.Frame(emptyList(), emptyList(), target)
    private val spawned = mutableListOf<BlockDisplay>()
    private val renderer = FinderGlowRenderer(plugin)

    @BeforeTest fun setup() {
        mockkStatic(Bukkit::class)
        every { Bukkit.createBlockData(Material.YELLOW_STAINED_GLASS) } returns mockk(relaxed = true)
        every { player.uniqueId } returns UUID.randomUUID()
        every { player.world } returns world
        every { player.location } returns Location(world, 4.0, 65.0, 4.0)
        every { world.isChunkLoaded(any(), any()) } returns true
        renderer.spawn = { _, initialize ->
            val display = mockk<BlockDisplay>(relaxed = true)
            every { display.uniqueId } returns UUID.randomUUID()
            every { display.isValid } returns true
            initialize.accept(display)
            verify { display.isVisibleByDefault = false; display.isPersistent = false; display.isGlowing = true }
            spawned += display
            display
        }
    }
    @AfterTest fun cleanup() { renderer.close(); unmockkStatic(Bukkit::class) }

    @Test fun `hidden before spawn private recipient cached and cleaned on retain and shutdown`() {
        assertTrue(renderer.render(player, frame, config))
        assertEquals(12, spawned.size)
        spawned.forEach { assertTrue(FinderGlowRenderer.owns(it.uniqueId)) }
        assertTrue(renderer.render(player, frame, config))
        assertEquals(12, spawned.size)
        verify(exactly = 12) { player.showEntity(plugin, any()) }
        verify(exactly = 0) { observer.showEntity(any(), any()) }
        renderer.retain(emptySet())
        spawned.forEach { verify(exactly = 1) { it.remove() }; assertFalse(FinderGlowRenderer.owns(it.uniqueId)) }
        spawned.clear()
        renderer.render(player, frame, config)
        renderer.close()
        spawned.forEach { verify(exactly = 1) { it.remove() } }
    }

    @Test fun `caps unloaded chunks and disabled glow clear scene and return particle fallback`() {
        config.maxDisplaysPerPlayer = 11
        assertFalse(renderer.render(player, frame, config))
        assertTrue(spawned.isEmpty())
        config.maxDisplaysPerPlayer = 48
        config.maxDisplays = 11
        assertFalse(renderer.render(player, frame, config))
        config.maxDisplays = 512
        assertTrue(renderer.render(player, frame, config))
        every { world.isChunkLoaded(any(), any()) } returns false
        assertFalse(renderer.render(player, frame, config))
        spawned.forEach { verify { it.remove() } }
        config.glow = false
        assertFalse(renderer.render(player, frame, config))
        verify(exactly = 0) { world.getChunkAt(any<Int>(), any<Int>()) }
    }

    @Test fun `partial spawn failures remove already created displays`() {
        var attempts = 0
        val original = renderer.spawn
        renderer.spawn = { location, initialize ->
            if (++attempts == 3) error("refused") else original(location, initialize)
        }
        assertFalse(renderer.render(player, frame, config))
        assertEquals(2, spawned.size)
        spawned.forEach { verify(exactly = 1) { it.remove() } }
    }

    @Test fun `unsupported clients retain particles without displays`() {
        renderer.supported = { false }
        assertFalse(renderer.render(player, frame, config))
        assertTrue(spawned.isEmpty())
    }

    @Test fun `global budget spans recipients and is released by cancellation`() {
        every { observer.uniqueId } returns UUID.randomUUID()
        every { observer.world } returns world
        every { observer.location } returns player.location
        config.maxDisplays = 12
        assertTrue(renderer.render(player, frame, config))
        assertFalse(renderer.render(observer, frame, config))
        renderer.clear(player.uniqueId)
        assertTrue(renderer.render(observer, frame, config))
        assertEquals(24, spawned.size)
    }

    @Test fun `unloaded intermediate chunks prevent an otherwise loaded endpoint boundary`() {
        val shape = net.badgersmc.em.domain.ports.RegionProvider.Footprint(listOf(
            net.badgersmc.em.domain.ports.RegionProvider.Vertex(0.0, 0.0),
            net.badgersmc.em.domain.ports.RegionProvider.Vertex(48.0, 0.0),
            net.badgersmc.em.domain.ports.RegionProvider.Vertex(48.0, 8.0),
            net.badgersmc.em.domain.ports.RegionProvider.Vertex(0.0, 8.0)), 64.0, 80.0)
        every { world.isChunkLoaded(1, any()) } returns false
        assertFalse(renderer.render(player, frame.copy(footprint = shape), config))
        assertTrue(spawned.isEmpty())
    }
}
