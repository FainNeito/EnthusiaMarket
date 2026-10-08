package net.badgersmc.em.application

import net.badgersmc.em.domain.ports.GuildSaleIntent
import net.badgersmc.em.domain.ports.GuildSaleJournal
import net.badgersmc.em.domain.ports.GuildSaleRewards
import net.badgersmc.em.domain.ports.GuildShopXpGateway
import java.util.UUID

/** Completed trades can retry forever; prepared/aborted trades never enter the replay queue. */
class GuildSaleRewardService(
    private val journal: GuildSaleJournal,
    private val gateway: GuildShopXpGateway,
    private val failure: (UUID?, Exception) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) : GuildSaleRewards {
    override fun prepare(guild: UUID, buyer: UUID, shopId: Long): UUID {
        val sale = GuildSaleIntent(UUID.randomUUID(), guild, buyer, shopId, now())
        journal.prepare(sale)
        check(gateway.prepare(sale.id, guild, buyer, sale.occurredAt) == "PREPARED") { "Unsupported XP preparation response" }
        return sale.id
    }

    override fun completed(id: UUID) {
        // A journal write failure must not turn delivered goods into an apparent failed purchase.
        // PREPARED is then ambiguous and requires operator investigation; never auto-award it.
        try { journal.completed(id) } catch (e: Exception) { failure(id, e) }
    }

    override fun aborted(id: UUID) {
        try { journal.aborted(id) } catch (e: Exception) { failure(id, e) }
    }

    fun replay(limit: Int = 100) {
        val pending = try { journal.pending(limit) } catch (e: Exception) { failure(null, e); return }
        for (sale in pending) {
            try {
                val result = gateway.complete(sale.id)
                check(isTerminal(result)) { "Unsupported XP completion response" }
                journal.acknowledged(sale.id, result)
            } catch (e: Exception) {
                failure(sale.id, e)
                // Preserve queue order under transient failure and avoid hammering a missing provider.
                break
            }
        }
    }

    private fun isTerminal(result: String): Boolean = result in setOf("OWN_GUILD", "DISABLED", "COOLDOWN", "CAPPED", "STALE_RUN") ||
        (result.startsWith("AWARDED:") && result.substringAfter(':').toIntOrNull()?.let { it > 0 } == true)
}
