package net.badgersmc.em.application

import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.MarketMutationGate
import net.badgersmc.em.domain.ports.RegionMemberSync
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.annotations.Service
import java.util.UUID

/**
 * Orchestrates per-stall member-roster mutations.
 *
 * - Authorises the actor against [Stall.canManage] (REQ-202).
 * - Applies the pure domain operation on the [Stall] aggregate.
 * - Rejects durable moderation reservations before mutations.
 * - Persists additions before [RegionMemberSync] grants build/interact
 *   rights (REQ-203); repeating a persisted addition retries its projection.
 * - Revokes external access before persisting member removals.
 */
@Service
class StallMemberService(
    private val stalls: StallRepository,
    private val regionMembers: RegionMemberSync,
    private val guildProvider: GuildProvider,
    private val mutationGate: MarketMutationGate = MarketMutationGate.Open,
) {

    sealed interface Result {
        data class Success(val stall: Stall) : Result
        data object NotFound : Result
        data object NotAuthorised : Result
        data class Rejected(val reason: String) : Result
    }

    fun addMember(stallId: StallId, actor: UUID, target: UUID): Result {
        val stall = stalls.findById(stallId) ?: return Result.NotFound
        if (mutationGate.isStallLocked(stallId.value)) return Result.Rejected("Stall is reserved for moderation")
        if (!stall.canManage(actor, guildProvider)) return Result.NotAuthorised
        val updated = try {
            stall.addMember(target)
        } catch (e: IllegalStateException) {
            return Result.Rejected(e.message ?: "member cap reached")
        }
        // Repeating an already-persisted grant retries a failed projection without
        // adding the owner to the delegated roster or requiring another DB write.
        if (updated === stall) {
            if (target in stall.members) regionMembers.addMember(stall.world, stall.regionId, target)
            return Result.Success(stall)
        }
        // A rejected/fenced save must never leave an unrecorded WorldGuard grant.
        stalls.save(updated)
        regionMembers.addMember(stall.world, stall.regionId, target)
        return Result.Success(updated)
    }

    fun removeMember(stallId: StallId, actor: UUID, target: UUID): Result {
        val stall = stalls.findById(stallId) ?: return Result.NotFound
        if (mutationGate.isStallLocked(stallId.value)) return Result.Rejected("Stall is reserved for moderation")
        if (!stall.canManage(actor, guildProvider)) return Result.NotAuthorised
        val updated = stall.removeMember(target)
        // Short-circuit when the roster didn't change (not a member)
        if (updated === stall) return Result.Success(stall)
        regionMembers.removeMember(stall.world, stall.regionId, target)
        stalls.save(updated)
        return Result.Success(updated)
    }

    fun listMembers(stallId: StallId, actor: UUID): Result {
        val stall = stalls.findById(stallId) ?: return Result.NotFound
        if (!stall.canManage(actor, guildProvider)) return Result.NotAuthorised
        return Result.Success(stall)
    }
}
