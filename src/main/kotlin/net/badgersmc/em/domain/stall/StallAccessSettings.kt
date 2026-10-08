package net.badgersmc.em.domain.stall

import java.util.UUID

enum class StallCapability {
    ENTRY, TRADE, ANVIL, LECTERN, DOORS, BUTTONS, LEVERS, ITEM_PICKUP,
    CHESTS, STOCK, PRICES,
}

data class StallAccessSettings(
    val stallId: String,
    val ownershipKey: String,
    val visitorFlags: Map<StallCapability, Boolean> = emptyMap(),
    val blacklist: Set<UUID> = emptySet(),
    val blockedEffects: Set<String> = setOf("INVISIBILITY"),
    val allowIncomingPotions: Boolean = false,
    val allies: Map<String, Set<StallCapability>> = emptyMap(),
    val revision: Long = 0,
) {
    init {
        require(blacklist.size <= 256 && allies.size <= 64 && blockedEffects.size <= 64)
        require(revision >= 0)
        require(visitorFlags.keys.none { it in PRIVILEGED })
        require(allies.keys.all { UUID.fromString(it).toString() == it })
        require(blockedEffects.all { it.matches(Regex("[A-Z0-9_]{1,64}")) })
    }

    fun visitorAllows(capability: StallCapability): Boolean =
        visitorFlags[capability] ?: (capability !in PRIVILEGED)

    companion object {
        val PRIVILEGED = setOf(StallCapability.CHESTS, StallCapability.STOCK, StallCapability.PRICES)
        fun ownershipKey(stall: Stall): String =
            "${stall.owner.type}:${stall.owner.id}:${stall.ownerSince?.toEpochMilli()}"
        fun defaults(stall: Stall) = StallAccessSettings(stall.id.value, ownershipKey(stall))
    }
}

interface StallAccessSettingsRepository {
    fun all(): List<StallAccessSettings>
    /** Save one revision while the expected ownership and moderation revision remain current. */
    fun save(settings: StallAccessSettings, expected: Stall): StallAccessSettings
}
