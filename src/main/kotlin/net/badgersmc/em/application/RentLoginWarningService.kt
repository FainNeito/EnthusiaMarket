package net.badgersmc.em.application

import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.EconomyProvider
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.stall.OwnerType
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallState
import net.badgersmc.nexus.annotations.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Evaluate on the server thread: companion caches and economy are synchronous contracts. */
@Service
class RentLoginWarningService(
    private val config: EnthusiaMarketConfig,
    private val economy: EconomyProvider,
    private val guilds: GuildProvider,
) {
    data class Warning(
        val stallId: String, val deadline: Instant, val grace: Boolean,
        val amount: Long, val guildPayer: Boolean, val insufficientFunds: Boolean,
    )

    fun warnings(stalls: List<Stall>, actor: UUID, now: Instant): List<Warning> {
        if (!config.rentWarnings.enabled) return emptyList()
        val window = Duration.ofHours(config.rentWarnings.warningWindowHours.coerceIn(0, MAX_WINDOW_HOURS))
        return stalls.mapNotNull { stall -> warning(stall, actor, now.plus(window)) }
            .sortedWith(compareBy(Warning::deadline, Warning::stallId))
    }

    private fun warning(stall: Stall, actor: UUID, horizon: Instant): Warning? {
        if (stall.state !in ACTIVE_STATES || !canRenew(stall, actor)) return null
        val grace = stall.state == StallState.GRACE
        val deadline = if (grace) RentTimingPolicy.graceEndsAt(stall, config)
            else RentTimingPolicy.effectiveNextRentAt(stall, config)
        if (deadline == null || (!grace && deadline.isAfter(horizon))) return null
        val calculated = stall.rentTerms.dailyRent(stall.winningBid)
        val amount = if (stall.winningBid > 0) maxOf(1, calculated) else calculated.coerceAtLeast(0)
        val guildPayer = stall.owner.type == OwnerType.GUILD
        val insufficient = config.rentWarnings.insufficientFundsEnabled && amount > 0 &&
            cannotAfford(stall, actor, amount, guildPayer)
        return Warning(stall.id.value, deadline, grace, amount, guildPayer, insufficient)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun canRenew(stall: Stall, actor: UUID): Boolean = try {
        when (stall.owner.type) {
            OwnerType.NONE -> false
            OwnerType.SOLO -> stall.owner.id == actor.toString() || actor in stall.members
            OwnerType.GUILD -> guilds.isMember(actor, stall.owner.id) &&
                guilds.hasShopPermission(actor, stall.owner.id, GuildProvider.GuildPermission.MANAGE_SHOPS)
        }
    } catch (_: Exception) { false }

    @Suppress("TooGenericExceptionCaught")
    private fun cannotAfford(stall: Stall, actor: UUID, amount: Long, guildPayer: Boolean): Boolean = try {
        val balance = if (guildPayer) guilds.bankBalance(stall.owner.id) else economy.balance(actor)
        balance < amount
    } catch (_: Exception) {
        false // An unavailable balance does not establish insufficient funds.
    }

    companion object {
        private const val MAX_WINDOW_HOURS = 8760L
        private val ACTIVE_STATES = setOf(StallState.OWNED, StallState.GRACE)
    }
}
