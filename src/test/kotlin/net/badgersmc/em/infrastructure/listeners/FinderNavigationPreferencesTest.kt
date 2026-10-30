package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.FinderNavigationMode
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.nexus.config.ConfigLoader
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Path
import kotlin.test.*

class FinderNavigationPreferencesTest {
    @TempDir lateinit var directory: Path

    @Test fun `player preferences roundtrip through PDC and invalid input preserves prior value`() {
        val server = MockBukkit.mock()
        try {
            val player = server.addPlayer()
            assertEquals(FinderNavigationMode.BOTH, FinderNavigationPreferences.get(player, "BOTH"))
            assertTrue(FinderNavigationPreferences.set(player, "locator"))
            assertEquals(FinderNavigationMode.LOCATOR, FinderNavigationPreferences.get(player, "particles"))
            assertFalse(FinderNavigationPreferences.set(player, "broken"))
            assertEquals(FinderNavigationMode.LOCATOR, FinderNavigationPreferences.get(player, "both"))
            assertTrue(FinderNavigationPreferences.set(player, "default"))
            assertEquals(FinderNavigationMode.PARTICLES, FinderNavigationPreferences.get(player, "particles"))
        } finally { MockBukkit.unmock() }
    }

    @Test fun `Nexus roundtrip retains locator settings without losing outline or lifetime`() {
        val loader = ConfigLoader(directory)
        val config = EnthusiaMarketConfig()
        config.finderTrail.durationSeconds = 42
        config.finderTrail.mode = "LOCATOR"
        config.finderTrail.showDistance = false
        config.finderTrail.arrivalSound = false
        config.finderTrail.locator.style = "enthusiamarket:stall"
        config.finderTrail.locator.requireResourcePack = false
        config.finderTrail.outline.color = "#ABCDEF"
        loader.save(config)
        val loaded = loader.load(EnthusiaMarketConfig::class)
        assertEquals(42L, loaded.finderTrail.durationSeconds)
        assertEquals("LOCATOR", loaded.finderTrail.mode)
        assertFalse(loaded.finderTrail.showDistance)
        assertFalse(loaded.finderTrail.arrivalSound)
        assertEquals("enthusiamarket:stall", loaded.finderTrail.locator.style)
        assertFalse(loaded.finderTrail.locator.requireResourcePack)
        assertEquals("#ABCDEF", loaded.finderTrail.outline.color)
    }
}
