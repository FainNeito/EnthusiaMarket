package net.badgersmc.em.infrastructure.listeners

import io.mockk.*
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.RegionProvider
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.bukkit.persistence.PersistentDataType
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.entity.PlayerDeathEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class FinderOutlineRenderingTest {
    private val player = mockk<Player>(relaxed = true)
    private val world = mockk<World>(relaxed = true)
    private val regions = mockk<RegionProvider>()
    private val plugin = mockk<Plugin>(relaxed = true)
    private val lang = mockk<LangService>()
    private val config = EnthusiaMarketConfig()
    private lateinit var service: FinderTrailService
    private val id = UUID.randomUUID()

    @BeforeTest fun setup() {
        mockkStatic(Bukkit::class)
        every { player.uniqueId } returns id
        every { player.world } returns world
        every { player.location } returns Location(world, 12.0, 65.0, 4.0)
        every { player.isOnline } returns true
        every { player.isDead } returns false
        every { player.persistentDataContainer.get(any<NamespacedKey>(), PersistentDataType.STRING) } returns null
        every { world.name } returns "world"
        every { world.isChunkLoaded(any(), any()) } returns true
        every { Bukkit.getPlayer(id) } returns player
        val scheduler = mockk<BukkitScheduler>()
        every { plugin.server.scheduler } returns scheduler
        every { scheduler.runTaskTimer(plugin, any<Runnable>(), any<Long>(), any<Long>()) } returns mockk<BukkitTask>(relaxed = true)
        every { lang.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
        every { regions.footprint("world", "stall") } returns RegionProvider.Footprint(listOf(
            RegionProvider.Vertex(0.0, 0.0), RegionProvider.Vertex(8.0, 0.0),
            RegionProvider.Vertex(8.0, 8.0), RegionProvider.Vertex(0.0, 8.0)), 0.0, 256.0)
        service = FinderTrailService(config, lang, plugin, regions)
    }
    @AfterTest fun cleanup() { service.close(); unmockkStatic(Bukkit::class) }

    @Test fun `outline particles are player only and footprint is cached for the selected guide`() {
        service.start(player, shop())
        service.render()
        service.render()
        verify(exactly = 1) { regions.footprint("world", "stall") }
        verify(atLeast = 1) { player.spawnParticle(Particle.DUST, any<Double>(), any<Double>(), any<Double>(),
            1, 0.0, 0.0, 0.0, 0.0, any<Particle.DustOptions>()) }
        service.stop(player)
        clearMocks(player, answers = false)
        service.render()
        verify(exactly = 0) { player.spawnParticle(Particle.DUST, any<Double>(), any<Double>(), any<Double>(),
            any<Int>(), any<Double>(), any<Double>(), any<Double>(), any<Double>(), any<Particle.DustOptions>()) }
    }

    @Test fun `unloaded chunks produce no particles or chunk load requests`() {
        every { world.isChunkLoaded(any(), any()) } returns false
        service.start(player, shop())
        service.render()
        verify(exactly = 0) { player.spawnParticle(Particle.DUST, any<Double>(), any<Double>(), any<Double>(),
            any<Int>(), any<Double>(), any<Double>(), any<Double>(), any<Double>(), any<Particle.DustOptions>()) }
        verify(exactly = 0) { world.getChunkAt(any<Int>(), any<Int>()) }
    }

    @Test fun `waypoint and particle sessions end on every explicit lifecycle event`() {
        val locator = mockk<FinderLocator>(relaxed = true)
        every { locator.show(player, any(), any()) } returns true
        service.navigation.locator = locator
        val events = listOf<() -> Unit>(
            { service.onQuit(PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty(), null)) },
            { service.onWorldChange(PlayerChangedWorldEvent(player, world)) },
            { val event = mockk<PlayerDeathEvent>(); every { event.player } returns player; service.onDeath(event) },
            { service.stop(player) },
            { service.close() },
        )
        events.forEach { finish ->
            service.start(player, shop())
            service.render()
            clearMocks(locator, answers = false)
            finish()
            verify { locator.clear(player) }
            clearMocks(locator, answers = false)
            service.render()
            verify(exactly = 0) { locator.show(any(), any(), any()) }
        }
    }

    @Test fun `expiry disable and arrival clear selected locator without changing the existing outline budget`() {
        val locator = mockk<FinderLocator>(relaxed = true)
        every { locator.show(player, any(), any()) } returns true
        service.navigation.locator = locator
        val now = Instant.parse("2026-10-18T00:00:00Z")
        service.clock = Clock.fixed(now, ZoneOffset.UTC)
        config.finderTrail.durationSeconds = 1
        service.start(player, shop())
        service.render()
        clearMocks(locator, answers = false)
        service.clock = Clock.fixed(now.plusSeconds(2), ZoneOffset.UTC)
        service.render()
        verify { locator.clear(player) }
        service.start(player, shop())
        service.render()
        clearMocks(locator, answers = false)
        config.finderTrail.enabled = false
        service.render()
        verify { locator.clear(player) }
        config.finderTrail.enabled = true
        service.start(player, shop())
        every { player.location } returns Location(world, 4.5, 65.0, 4.5)
        clearMocks(locator, answers = false)
        service.render()
        verify { locator.clear(player) }
        verify(exactly = 0) { locator.show(any(), any(), any()) }
    }

    private fun shop() = Shop(stallId = "stall", owner = id, signWorld = "world", signX = 4, signY = 65,
        signZ = 4, containerWorld = "world", containerX = 4, containerY = 65, containerZ = 4,
        sellItem = "item", sellAmount = 1, costItem = "cost", costAmount = 1)
}
