package net.badgersmc.em.application

import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import java.util.concurrent.ConcurrentHashMap

/** Observe successful ordinary writes without changing authoritative repository reads. */
class StallAccessIndex(private val delegate: StallRepository) : StallRepository by delegate {
    private val snapshots = ConcurrentHashMap<String, Stall>()
    private val byRegion = ConcurrentHashMap<Pair<String, String>, String>()
    private val stale = ConcurrentHashMap.newKeySet<String>()

    fun rebuild() { delegate.all().forEach(::publish) }
    fun cached(id: String): Stall? = snapshots[id]
    fun cached(world: String, region: String): Stall? = byRegion[world to region]?.let(snapshots::get)
    fun isFresh(id: String): Boolean = id !in stale
    fun invalidate(id: String) { stale.add(id) }
    @Synchronized fun refresh(id: String) { delegate.findById(StallId(id))?.let(::publish) }

    @Synchronized override fun create(stall: Stall) { delegate.create(stall); publish(stall) }
    @Synchronized override fun save(stall: Stall) { delegate.save(stall); publish(stall) }

    private fun publish(stall: Stall) {
        snapshots[stall.id.value] = stall
        byRegion[stall.world to stall.regionId] = stall.id.value
        stale.remove(stall.id.value)
    }
}
