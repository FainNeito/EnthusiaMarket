package net.badgersmc.em.domain.ports

import net.badgersmc.em.domain.stall.StallCapability
import java.util.UUID

interface StallAccessPolicy {
    fun allows(stallId: String, actor: UUID, capability: StallCapability): Boolean
    /** Explicit allied grants only; never inferred from ordinary visitor access. */
    fun alliedAllows(stallId: String, actor: UUID, capability: StallCapability): Boolean
    fun blacklistDenies(stallId: String, actor: UUID): Boolean = false

    object Open : StallAccessPolicy {
        override fun allows(stallId: String, actor: UUID, capability: StallCapability): Boolean = true
        override fun alliedAllows(stallId: String, actor: UUID, capability: StallCapability): Boolean = false
    }
}
