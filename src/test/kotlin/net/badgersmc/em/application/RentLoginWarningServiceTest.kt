package net.badgersmc.em.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.EconomyProvider
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.RentTerms
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallState
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RentLoginWarningServiceTest {
    private val actor = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val now = Instant.parse("2026-10-08T12:00:00Z")
    private val config = EnthusiaMarketConfig()
    private val economy = mockk<EconomyProvider>(relaxed = true)
    private val guilds = mockk<GuildProvider>(relaxed = true)
    private val service = RentLoginWarningService(config, economy, guilds)
    private val stall = Stall(StallId("stall1"), "stall1", "world", StallState.OWNED,
        OwnerRef.solo(actor), now.minusSeconds(86400), 1000, RentTerms.flat(100),
        nextRentAt = now.plusSeconds(86400))

    @Test fun `window is inclusive and warning is informational`() {
        every { economy.balance(actor) } returns 99
        val notice = service.warnings(listOf(stall), actor, now).single()
        assertTrue(notice.insufficientFunds)
        assertEquals(100, notice.amount)
        assertFalse(notice.grace)
        assertTrue(service.warnings(listOf(stall.copy(nextRentAt = now.plusSeconds(86401))), actor, now).isEmpty())
        verify(exactly = 0) { economy.withdraw(any(), any()) }
        verify(exactly = 0) { economy.deposit(any(), any()) }
    }

    @Test fun `grace uses original due date plus configured grace`() {
        every { economy.balance(actor) } returns 100
        val notice = service.warnings(listOf(stall.copy(state = StallState.GRACE,
            nextRentAt = now.minusSeconds(86400))), actor, now).single()
        assertTrue(notice.grace)
        assertEquals(now.plusSeconds(172800), notice.deadline)
        assertFalse(notice.insufficientFunds)
    }

    @Test fun `legacy deadline disabled warnings and inactive stalls`() {
        assertEquals(now, service.warnings(listOf(stall.copy(nextRentAt = null)), actor, now).single().deadline)
        assertTrue(service.warnings(listOf(stall.copy(state = StallState.EMERGENCY_AUCTIONING)), actor, now).isEmpty())
        config.rentWarnings.enabled = false
        assertTrue(service.warnings(listOf(stall), actor, now).isEmpty())
    }

    @Test fun `only eligible members receive personal affordability warning`() {
        val member = UUID.randomUUID()
        assertTrue(service.warnings(listOf(stall), member, now).isEmpty())
        every { economy.balance(member) } returns 50
        assertTrue(service.warnings(listOf(stall.copy(members = setOf(member))), member, now).single().insufficientFunds)
        verify(exactly = 0) { economy.balance(actor) }
    }

    @Test fun `guild manager checks guild payer and never actor balance`() {
        val guild = stall.copy(owner = OwnerRef.guild("guild1"))
        every { guilds.isMember(actor, "guild1") } returns true
        assertTrue(service.warnings(listOf(guild), actor, now).isEmpty())
        every { guilds.hasShopPermission(actor, "guild1", GuildProvider.GuildPermission.MANAGE_SHOPS) } returns true
        every { guilds.bankBalance("guild1") } returns 99
        val notice = service.warnings(listOf(guild), actor, now).single()
        assertTrue(notice.guildPayer && notice.insufficientFunds)
        verify(exactly = 0) { economy.balance(any()) }
        verify(exactly = 0) { guilds.bankWithdraw(any(), any()) }
    }

    @Test fun `unknown balance and free rent never claim insufficient funds`() {
        every { economy.balance(actor) } throws IllegalStateException("unavailable")
        assertFalse(service.warnings(listOf(stall), actor, now).single().insufficientFunds)
        assertFalse(service.warnings(listOf(stall.copy(winningBid = 0, rentTerms = RentTerms.flat(0))),
            actor, now).single().insufficientFunds)
        config.rentWarnings.insufficientFundsEnabled = false
        assertFalse(service.warnings(listOf(stall), actor, now).single().insufficientFunds)
    }
}
