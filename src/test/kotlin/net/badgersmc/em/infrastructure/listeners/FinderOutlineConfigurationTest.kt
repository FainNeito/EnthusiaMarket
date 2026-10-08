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
}
