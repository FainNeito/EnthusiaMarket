package net.badgersmc.em.application

import net.badgersmc.em.domain.stall.RentPayment
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import java.util.concurrent.ConcurrentHashMap

/** Observe successful ordinary writes without changing authoritative repository reads. */
class StallAccessIndex(private val delegate: StallRepository) : StallRepository by delegate {
    private val snapshots = ConcurrentHashMap<String, Stall>()
    private val byRegion = ConcurrentHashMap<Pair<String, String>, String>()
    private val stale = ConcurrentHashMap.newKeySet<String>()

    private val observers = java.util.concurrent.CopyOnWriteArrayList<(Stall) -> Unit>()
    fun onCommittedStall(observer: (Stall) -> Unit) { observers.add(observer) }

    @Synchronized override fun savePendingSellback(stall: Stall) {
        delegate.savePendingSellback(stall)
        updateSnapshot(stall)
    }

    @Synchronized override fun finishSellbackAccess(previous: Stall) {
        delegate.finishSellbackAccess(previous)
        refresh(previous.id.value)
    }

    fun rebuild() { delegate.all().forEach(::publish) }
    fun currentStalls(): List<Stall> = snapshots.values.filter { isFresh(it.id.value) }
    fun cached(id: String): Stall? = snapshots[id]
    fun cached(world: String, region: String): Stall? = byRegion[world to region]?.let(snapshots::get)
    fun isFresh(id: String): Boolean = id !in stale
    fun invalidate(id: String) { stale.add(id) }
    @Synchronized fun refresh(id: String) { delegate.findById(StallId(id))?.let(::publish) }

    override fun onExternalStateCommit(id: StallId) {
        invalidate(id.value)
        try {
            refresh(id.value)
        } finally {
            delegate.onExternalStateCommit(id)
        }
    }

    @Synchronized override fun create(stall: Stall) { delegate.create(stall); publish(stall) }
    @Synchronized override fun save(stall: Stall) { delegate.save(stall); publish(stall) }

    @Synchronized override fun deleteIfUnowned(id: StallId): StallRepository.DeleteResult {
        val result = delegate.deleteIfUnowned(id)
        if (result == StallRepository.DeleteResult.DELETED) {
            snapshots.remove(id.value)?.let { byRegion.remove(it.world to it.regionId, id.value) }
            stale.remove(id.value)
        }
        return result
    }

    @Synchronized override fun savePaidExtension(stall: Stall, payment: RentPayment) {
        delegate.savePaidExtension(stall, payment)
        publish(stall)
    }

    @Synchronized override fun saveOpeningRoundGuildAward(stall: Stall, roundId: String) {
        delegate.saveOpeningRoundGuildAward(stall, roundId)
        publish(stall)
    }

    private fun publish(stall: Stall) {
        updateSnapshot(stall)
        observers.forEach { it(stall) }
    }

    private fun updateSnapshot(stall: Stall) {
        snapshots[stall.id.value] = stall
        byRegion[stall.world to stall.regionId] = stall.id.value
        stale.remove(stall.id.value)
    }
}
