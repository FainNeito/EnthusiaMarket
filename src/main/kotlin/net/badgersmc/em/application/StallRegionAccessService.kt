package net.badgersmc.em.application

import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.MarketMutationGate
import net.badgersmc.em.domain.ports.RegionMemberSync
import net.badgersmc.em.domain.stall.OwnerType
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallState
import java.util.UUID

/** Rebuilds only the access projection; never guesses which persisted members are stale. */
class StallRegionAccessService(
    private val guilds: GuildProvider,
    private val regions: RegionMemberSync,
    private val gate: MarketMutationGate,
) {
    /** False means deliberately preserved. Provider failures propagate to the caller's report. */
    fun sync(stall: Stall): Boolean {
        if (gate.isStallLocked(stall.id.value) || stall.state == StallState.MODERATION_HOLD) return false
        return when (stall.state) {
            StallState.OWNED, StallState.GRACE -> {
                syncActive(stall)
                true
            }
            StallState.UNOWNED, StallState.EMERGENCY_AUCTIONING -> {
                regions.clearOwnersAndMembers(stall.world, stall.regionId)
                true
            }
            StallState.AUCTIONING, StallState.RE_AUCTIONING -> {
                // A seller-owned auction has not transferred ownership yet.
                if (stall.owner.type != OwnerType.NONE) false else {
                    regions.clearOwnersAndMembers(stall.world, stall.regionId)
                    true
                }
            }
            StallState.MODERATION_HOLD -> false
        }
    }

    private fun syncActive(stall: Stall) {
        when (stall.owner.type) {
            OwnerType.SOLO -> {
                val owner = UUID.fromString(stall.owner.id)
                // setOwner replaces BOTH domains, including name/group entries.
                regions.setOwner(stall.world, stall.regionId, owner)
                stall.members.forEach { regions.addMember(stall.world, stall.regionId, it) }
            }
            OwnerType.GUILD -> regions.syncGuildMembers(
                stall.world, stall.regionId, guilds.memberIds(stall.owner.id),
            )
            OwnerType.NONE -> regions.clearOwnersAndMembers(stall.world, stall.regionId)
        }
    }
}
