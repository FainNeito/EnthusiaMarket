package net.badgersmc.em.application

import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.stall.GuildStallAccessRules
import net.badgersmc.em.domain.stall.GuildStallMemberView
import net.badgersmc.em.domain.stall.GuildStallView
import net.badgersmc.em.domain.stall.OwnerType
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallRepository
import java.util.UUID

/** Fresh read-only inventory for a current member; failures remain explicit. */
class GuildStallQueryService(
    private val stalls: StallRepository,
    private val guilds: GuildProvider,
    private val config: EnthusiaMarketConfig,
) {
    fun read(guildId: UUID, viewer: UUID): List<GuildStallView> {
        val id = guildId.toString()
        check(guilds.guildById(id) != null) { "Guild provider unavailable" }
        if (!guilds.isMember(viewer, id)) throw SecurityException("Not a current guild member")
        val roster = guilds.memberIds(id)
        check(viewer in roster) { "Guild membership read unavailable" }
        return stalls.all().filter { it.owner.type == OwnerType.GUILD && it.owner.id == id }
            .sortedBy { it.id.value }.map { view(it, roster) }
    }

    private fun view(stall: Stall, roster: Set<UUID>): GuildStallView = GuildStallView(
        stall.id.value, stall.regionId, stall.world, stall.state.name,
        stall.rentTerms.dailyRent(stall.winningBid),
        RentTimingPolicy.collectionInterval(config).seconds,
        RentTimingPolicy.effectiveNextRentAt(stall, config),
        RentTimingPolicy.graceEndsAt(stall, config),
        roster.sortedBy(UUID::toString).mapNotNull { member(stall, it) },
    )

    private fun member(stall: Stall, actor: UUID): GuildStallMemberView? {
        val rights = GuildProvider.GuildPermission.entries.filter {
            GuildStallAccessRules.allows(stall, actor, guilds, it)
        }.map { it.name }.toSet()
        return if (rights.isEmpty()) null else GuildStallMemberView(actor, rights)
    }
}
