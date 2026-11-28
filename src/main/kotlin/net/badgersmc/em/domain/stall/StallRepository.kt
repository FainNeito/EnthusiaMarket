package net.badgersmc.em.domain.stall

import java.time.Instant

data class RentPayment(
    val payer: OwnerRef,
    val amount: Long,
    val paidAt: Instant,
    val coveredFrom: Instant,
)

interface StallRepository {
    enum class DeleteResult { DELETED, NOT_FOUND, NOT_UNOWNED, REFERENCED }
    fun findById(id: StallId): Stall?
    fun findByIds(ids: Collection<StallId>): Map<StallId, Stall> =
        ids.mapNotNull { id -> findById(id)?.let { id to it } }.toMap()
    fun findByRegion(world: String, regionId: String): Stall?
    fun all(): List<Stall>
    fun byState(state: StallState): List<Stall>
    fun save(stall: Stall)
    /** A failed refund may reinstate the exact same tenancy. */
    fun savePendingSellback(stall: Stall) = save(stall)
    fun finishSellbackAccess(previous: Stall) = Unit
    fun savePaidExtension(stall: Stall, payment: RentPayment)
    /** Persist the guild award and consume its opening-round win in one transaction. */
    fun saveOpeningRoundGuildAward(stall: Stall, roundId: String)
    /** Notify projections after another repository commits a stall state change. */
    fun onExternalStateCommit(id: StallId) = Unit
    fun create(stall: Stall)
    /** Remove a vacant stall only when no persisted records still reference it. */
    fun deleteIfUnowned(id: StallId): DeleteResult = DeleteResult.REFERENCED
}
