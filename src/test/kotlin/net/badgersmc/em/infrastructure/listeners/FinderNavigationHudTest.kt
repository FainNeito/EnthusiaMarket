package net.badgersmc.em.infrastructure.listeners

import io.mockk.*
import net.badgersmc.em.application.FinderTrailTracker
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import kotlin.test.*

class FinderNavigationHudTest {
    private val player = mockk<Player>(relaxed = true)
    private val locator = mockk<FinderLocator>(relaxed = true)
    private val plugin = mockk<Plugin>(relaxed = true)
    private val lang = mockk<LangService>()
    private val config = EnthusiaMarketConfig()
    private lateinit var hud: FinderNavigationHud
    private val id = UUID.randomUUID()
    private val target = FinderTrailTracker.Point(10.5, 64.5, 8.5)
    private val frame = FinderTrailTracker.Frame(emptyList(), emptyList(), target)

    @BeforeTest fun setup() {
        every { player.uniqueId } returns id
        every { player.location } returns Location(mockk<World>(), 0.0, 64.0, 8.0)
        every { player.isOnline } returns true
        every { player.persistentDataContainer.get(any<NamespacedKey>(), PersistentDataType.STRING) } returns null
        every { lang.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
        every { locator.show(player, any(), any()) } returns true
        hud = FinderNavigationHud(config, lang, plugin)
        hud.locator = locator
        hud.start(player, shop())
        clearMocks(locator, recordedCalls = true, answers = false)
    }

    @AfterTest fun cleanup() { hud.clear() }

    @Test fun `locator mode hides direction only when private waypoint succeeds`() {
        config.finderTrail.mode = "LOCATOR"
        assertFalse(hud.render(player, frame))
        every { locator.show(player, any(), any()) } returns false
        assertTrue(hud.render(player, frame))
        config.finderTrail.locator.enabled = false
        assertTrue(hud.render(player, frame))
        verify(atLeast = 1) { locator.clear(player) }
    }

    @Test fun `custom icon needs loaded pack and invalid style falls back without changing player visibility`() {
        config.finderTrail.locator.style = "enthusiamarket:stall"
        every { player.hasResourcePack() } returns false
        hud.render(player, frame)
        verify { locator.show(player, target, "minecraft:default") }
        every { player.hasResourcePack() } returns true
        hud.render(player, frame)
        verify { locator.show(player, target, "enthusiamarket:stall") }
        config.finderTrail.locator.style = "INVALID STYLE"
        hud.render(player, frame)
        verify(exactly = 2) { locator.show(player, target, "minecraft:default") }
        verify(exactly = 0) { player.getAttribute(any()) }
    }

    @Test fun `arrival clears marker sounds once and retain removes expired guides and actionbar`() {
        hud.render(player, frame)
        hud.render(player, frame.copy(arrived = true))
        hud.render(player, frame.copy(arrived = true))
        verify(exactly = 1) { player.playSound(any<Location>(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.5f) }
        verify(atLeast = 1) { locator.clear(player) }
        hud.retain(emptySet())
        clearMocks(locator, answers = false)
        assertTrue(hud.render(player, frame))
        verify(exactly = 0) { locator.show(any(), any(), any()) }
        verify { player.sendActionBar(Component.empty()) }
    }

    @Test fun `particles mode clears old marker and transport failure preserves particle guidance`() {
        config.finderTrail.mode = "PARTICLES"
        assertTrue(hud.render(player, frame))
        verify(exactly = 0) { locator.show(any(), any(), any()) }
        config.finderTrail.mode = "LOCATOR"
        every { locator.show(player, any(), any()) } throws IllegalStateException("unavailable")
        assertTrue(hud.render(player, frame))
        hud.render(player, frame)
        verify(exactly = 1) { plugin.logger.warning(any<String>()) }
    }

    private fun shop() = Shop(stallId = "stall24", owner = id, signWorld = "world", signX = 10, signY = 64,
        signZ = 8, containerWorld = "world", containerX = 10, containerY = 64, containerZ = 8,
        sellItem = "", sellAmount = 1, costItem = "", costAmount = 1)
}
