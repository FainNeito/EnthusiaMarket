package net.badgersmc.em.interaction

import net.badgersmc.em.domain.stall.StallCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StallAccessLabelsTest {
    private val labels = StallAccessLabels()

    @Test fun `every capability has a friendly explicit label`() {
        for (capability in StallCapability.entries) {
            assertTrue(labels.hasCapability(capability.name))
            assertFalse(labels.capability(capability.name).contains('_'))
        }
        assertEquals("Pick up items", labels.capability("ITEM_PICKUP"))
    }
    @Test fun `effects use readable names with safe fallback`() {
        assertEquals("Invisibility", labels.effect("INVISIBILITY"))
        assertEquals("Trial omen", labels.effect("TRIAL_OMEN"))
        assertEquals("Unknown effect", labels.effect(""))
    }
    @Test fun `states and section titles hide implementation identifiers`() {
        assertEquals("Allowed", labels.state(true))
        assertEquals("Blocked", labels.state(false))
        assertEquals("Visitor access", labels.section("unexpected:secret"))
        assertEquals("Stall market-01: Unknown guild access", labels.title("market-01", "ally:opaque-id"))
        assertEquals("Unknown guild access", labels.section("ally:opaque-id"))
        assertEquals("Renamed guild access", labels.section("ally:opaque-id", "Renamed guild"))
    }
    @Test fun `unknown identities remain distinguishable without an ID fallback`() {
        assertEquals("Unknown player 1", labels.player(null, 1))
        assertEquals("Unknown player 2", labels.player(" ", 2))
        assertEquals("KnownOffline", labels.player("KnownOffline", 2))
        assertEquals("Alice", labels.player("Alice", 1))
        assertEquals("AliceNew", labels.player("AliceNew", 1))
        assertEquals("Unknown player 1", labels.player(null, 1))
    }
    @Test fun `hostile markup is literal and legacy control formatting is stripped`() {
        assertEquals("<red>Name</red>", labels.player("<red>Name</red>", 1))
        assertEquals("Name", labels.player("§kNa\nme§r", 1))
        assertFalse(labels.section("ally:id", "§4<click:run_command:'/op x'>Guild").contains('§'))
    }
    @Test fun `localized templates safely substitute opaque names once`() {
        val translated = StallAccessLabels { key -> when (key) {
            "state.allowed" -> "Permitted"
            "player.unknown" -> "Player missing #{number}"
            "section.ally" -> "Access for {name}"
            else -> null
        } }
        assertEquals("Permitted", translated.state(true))
        assertEquals("Player missing #3", translated.player(null, 3))
        assertEquals("Access for {name}", translated.section("ally:id", "{name}"))
    }
    @Test fun `failure is safe and does not invent rollback success`() {
        assertTrue(labels.text("error.setting_failed").contains("could not confirm", ignoreCase = true))
        assertFalse(labels.text("error.setting_failed").contains("rollback", ignoreCase = true))
    }
}
