package net.badgersmc.em.application

import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.RegionMemberSync
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.annotations.Service

/** Replaces the projection, including an empty roster, so departures revoke stale rights. */
@Service
class GuildStallAccessSync(
    private val stalls: StallRepository,
    private val guilds: GuildProvider,
    private val regions: RegionMemberSync,
) {
    fun refresh(guildId: String) {
        val members = guilds.memberIds(guildId)
        for (stall in stalls.all().filter { it.isActiveGuildStall() && it.owner.id == guildId }) {
            regions.syncGuildMembers(stall.world, stall.regionId, members)
        }
    }
}
