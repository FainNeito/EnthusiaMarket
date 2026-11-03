package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.nexus.config.ConfigLoader
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinderOutlineConfigurationTest {
    @TempDir lateinit var directory: Path

    @Test fun `legacy finder configuration gains outline defaults without losing prior settings`() {
        Files.writeString(directory.resolve("enthusiamarket.yaml"), "finderTrail:\n  durationSeconds: 40\n")
        val config = ConfigLoader(directory).load(EnthusiaMarketConfig::class)
        assertEquals(40L, config.finderTrail.durationSeconds)
        assertTrue(config.finderTrail.outline.enabled)
        assertEquals(24.0, config.finderTrail.outline.revealDistance)
        assertEquals(10L, config.finderTrail.outline.arrivalSeconds)
    }

    @Test fun `actual Nexus save load and reload preserve configurable outline fields`() {
        val loader = ConfigLoader(directory)
        val original = EnthusiaMarketConfig()
        original.finderTrail.outline.color = "#123ABC"
        original.finderTrail.outline.revealDistance = 32.0
        original.finderTrail.outline.arrivalSeconds = 7
        original.finderTrail.outline.height = 4.0
        original.finderTrail.outline.spacing = 2.0
        original.finderTrail.outline.maxParticlesPerPlayer = 23
        original.finderTrail.outline.particleSize = 2.0
        original.finderTrail.outline.showShopMarker = false
        loader.save(original)
        val loaded = loader.load(EnthusiaMarketConfig::class)
        assertEquals(FinderOutlineStyle.options(original.finderTrail.outline), FinderOutlineStyle.options(loaded.finderTrail.outline))
        assertEquals(0x123ABC, FinderOutlineStyle.dust(loaded.finderTrail.outline).color.asRGB())
        assertEquals(2f, FinderOutlineStyle.dust(loaded.finderTrail.outline).size)
        original.finderTrail.outline.enabled = false
        loader.save(original)
        loader.reload(loaded)
        assertFalse(loaded.finderTrail.outline.enabled)
    }

    @Test fun `Nexus round trips route node objects glow caps intensity and arrival configuration`() {
        val loader = ConfigLoader(directory)
        val original = EnthusiaMarketConfig()
        original.finderTrail.intensity = "LOW"
        original.finderTrail.pulseSpeed = 3.5
        original.finderTrail.arrivalSoundName = "ENTITY_EXPERIENCE_ORB_PICKUP"
        original.finderTrail.outline.glow = false
        original.finderTrail.outline.beamWidth = 0.08
        original.finderTrail.outline.maxDisplays = 120
        original.finderTrail.routing.nodes += mapOf("id" to "corner", "world" to "market",
            "x" to 12.5, "y" to 65.0, "z" to -30.5, "links" to listOf("entrance"))
        loader.save(original)
        val loaded = loader.load(EnthusiaMarketConfig::class).finderTrail
        assertEquals("LOW", loaded.intensity)
        assertEquals(3.5, loaded.pulseSpeed)
        assertEquals("ENTITY_EXPERIENCE_ORB_PICKUP", loaded.arrivalSoundName)
        assertFalse(loaded.outline.glow)
        assertEquals(0.08, loaded.outline.beamWidth)
        assertEquals(120, loaded.outline.maxDisplays)
        val node = loaded.routing.nodes.single() as Map<*, *>
        assertEquals("corner", node["id"])
        assertEquals("market", node["world"])
        assertEquals(12.5, node["x"])
        assertEquals(-30.5, node["z"])
        assertEquals(listOf("entrance"), node["links"])
    }

    @Test fun `reloaded YAML nodes route by world and malformed coordinates safely fall back`() {
        Files.writeString(directory.resolve("enthusiamarket.yaml"), """
            finderTrail:
              routing:
                attachmentDistance: 3
                nodes:
                  - {id: a, world: market, x: 0, y: 64, z: 0, links: [b]}
                  - {id: b, world: market, x: 0, y: 64, z: 20, links: [c]}
                  - {id: c, world: market, x: 20, y: 64, z: 20, links: []}
        """.trimIndent())
        val config = ConfigLoader(directory).load(EnthusiaMarketConfig::class).finderTrail.routing
        val start = net.badgersmc.em.application.FinderTrailTracker.Point(0.0, 64.0, 0.0)
        val target = start.copy(x = 20.0, z = 20.0)
        assertEquals(3, FinderRouteConfiguration.plan(config, "market", start, target).size)
        assertTrue(FinderRouteConfiguration.plan(config, "other", start, target).isEmpty())
        config.nodes[1] = mapOf("id" to "b", "world" to "market", "x" to "invalid", "links" to listOf("c"))
        assertTrue(FinderRouteConfiguration.plan(config, "market", start, target).isEmpty())
    }
}
