package net.badgersmc.em.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import net.badgersmc.em.application.StallAccessSettingsService
import net.badgersmc.em.domain.stall.*
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class StallEffectGuardTest {
    @AfterTest fun cleanup() { unmockkAll(); if (MockBukkit.isMocked()) MockBukkit.unmock() }

    @Test fun `existing invisibility is suppressed and only original remaining duration returns on exit`() {
        val server = MockBukkit.mock()
        val player = server.addPlayer()
        val plugin = MockBukkit.createMockPlugin()
        val stall = Stall(StallId("s"), "s", "world", StallState.OWNED, OwnerRef.solo(UUID.randomUUID()),
            Instant.now(), 100, RentTerms.formula(1.0))
        val regions = mockk<StallAccessRegions>()
        val access = mockk<StallAccessSettingsService>()
        val permissions = mockk<StallAccessListener>()
        every { regions.at(any()) } returns listOf(stall)
        every { access.current(stall) } returns StallAccessSettings.defaults(stall)
        every { permissions.allowed(any(), any(), any()) } returns true
        val guard = StallEffectGuard(plugin, regions, access, permissions)
        val now = System.currentTimeMillis()
        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 100, 2, true, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 0))
        guard.reconcile(player, now)
        assertNull(player.getPotionEffect(PotionEffectType.INVISIBILITY))
        assertNotNull(player.getPotionEffect(PotionEffectType.SPEED))
        every { regions.at(any()) } returns emptyList()
        guard.reconcile(player, now + 2500)
        val restored = assertNotNull(player.getPotionEffect(PotionEffectType.INVISIBILITY))
        assertEquals(50, restored.duration)
        assertEquals(2, restored.amplifier)
        assertTrue(restored.isAmbient)
        assertFalse(restored.hasParticles())
        guard.close()
    }

    @Test fun `expired invisibility does not become infinite or return after leaving`() {
        val server = MockBukkit.mock()
        val player = server.addPlayer()
        val plugin = MockBukkit.createMockPlugin()
        val stall = Stall(StallId("s"), "s", "world", StallState.OWNED, OwnerRef.solo(UUID.randomUUID()),
            Instant.now(), 100, RentTerms.formula(1.0))
        val regions = mockk<StallAccessRegions>()
        val access = mockk<StallAccessSettingsService>()
        val permissions = mockk<StallAccessListener>()
        every { regions.at(any()) } returns listOf(stall)
        every { access.current(stall) } returns StallAccessSettings.defaults(stall)
        every { permissions.allowed(any(), any(), any()) } returns true
        val guard = StallEffectGuard(plugin, regions, access, permissions)
        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 2, 0))
        val now = System.currentTimeMillis()
        guard.reconcile(player, now)
        every { regions.at(any()) } returns emptyList()
        guard.reconcile(player, now + 150)
        assertNull(player.getPotionEffect(PotionEffectType.INVISIBILITY))
        guard.close()
    }
}
