package net.badgersmc.em.application

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.RentTerms
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.domain.stall.StallState
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Read-only guild stall ownership and permission regressions. */
internal class GuildStallQueryTest {
    private val guildId = UUID.randomUUID()
    private val viewer = UUID.randomUUID()
    private val offline = UUID.randomUUID()
    private val stalls = mockk<StallRepository>()
    private val guilds = mockk<GuildProvider>()
    private val config = EnthusiaMarketConfig()
    private val service = GuildStallQueryService(stalls, guilds, config)
    private val stall = Stall(StallId("stall1"), "stall1", "world", StallState.OWNED,
        OwnerRef.guild(guildId.toString()), Instant.EPOCH, 100L, RentTerms.flat(100L))

    init {
        every { guilds.guildById(guildId.toString()) } returns GuildProvider.GuildRef(guildId.toString(), "Guild")
        every { guilds.isMember(viewer, guildId.toString()) } returns true
        every { guilds.isMember(offline, guildId.toString()) } returns true
        every { guilds.memberIds(guildId.toString()) } returns setOf(viewer, offline)
        every { guilds.hasShopPermission(any(), any(), any()) } returns false
    }

    /** Personal stalls and other guilds must not appear. */
    @Test
    fun filtersOwnership() {
        every { stalls.all() } returns listOf(stall,
            stall.copy(id = StallId("solo"), owner = OwnerRef.solo(viewer)),
            stall.copy(id = StallId("other"), owner = OwnerRef.guild(UUID.randomUUID().toString())))
        assertEquals(listOf("stall1"), service.read(guildId, viewer).map { it.id })
    }

    /** Offline members remain visible and permissions refresh after edits. */
    @Test
    fun refreshesOfflineAccess() {
        every { stalls.all() } returns listOf(stall)
        every { guilds.hasShopPermission(offline, guildId.toString(), GuildProvider.GuildPermission.ACCESS_SHOP_CHESTS) } returns true
        assertEquals(setOf("ACCESS_SHOP_CHESTS"), service.read(guildId, viewer).single().members.single().permissions)
        every { guilds.hasShopPermission(offline, guildId.toString(), GuildProvider.GuildPermission.ACCESS_SHOP_CHESTS) } returns false
        assertTrue(service.read(guildId, viewer).single().members.isEmpty())
        every { guilds.hasShopPermission(offline, guildId.toString(), GuildProvider.GuildPermission.ACCESS_SHOP_CHESTS) } returns true
        every { guilds.isMember(offline, guildId.toString()) } returns false
        assertTrue(service.read(guildId, viewer).single().members.isEmpty())
    }

    /** A broken full-roster read must not be presented as a real no-stall response. */
    @Test
    fun rejectsIncompleteRoster() {
        every { guilds.memberIds(guildId.toString()) } returns emptySet()
        assertFailsWith<IllegalStateException> { service.read(guildId, viewer) }
    }

    /** Moderation holds cannot claim usable shop permissions. */
    @Test
    fun heldStallDeniesAccess() {
        every { stalls.all() } returns listOf(stall.copy(state = StallState.MODERATION_HOLD))
        every { guilds.hasShopPermission(any(), any(), any()) } returns true
        assertTrue(service.read(guildId, viewer).single().members.isEmpty())
    }

    /** Missing membership or storage is a failed read, not an empty result. */
    @Test
    fun failsClosed() {
        every { stalls.all() } throws IllegalStateException("offline")
        assertFailsWith<IllegalStateException> { service.read(guildId, viewer) }
        every { guilds.isMember(viewer, guildId.toString()) } returns false
        assertFailsWith<SecurityException> { service.read(guildId, viewer) }
    }

    /** Due-date fallbacks use the same interpretation as rent collection. */
    @Test
    fun preservesRentTruth() {
        every { stalls.all() } returns listOf(stall)
        val result = service.read(guildId, viewer).single()
        assertEquals(100L, result.rent)
        assertEquals(RentTimingPolicy.effectiveNextRentAt(stall, config), result.nextRentAt)
    }
}
