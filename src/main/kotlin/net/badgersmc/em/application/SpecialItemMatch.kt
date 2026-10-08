package net.badgersmc.em.application

/** Normalize only entity identity/timers in creature containers, preserving all other item data. */
internal object SpecialItemMatch {
    private val incidental = setOf("UUID", "UUIDMost", "UUIDLeast", "Pos", "Motion", "Rotation",
        "FallDistance", "Fire", "Air", "OnGround", "PortalCooldown", "Brain", "HuntingCooldown",
        "TicksSincePollination", "CannotEnterHiveTicks", "TicksSinceSting", "FlowerPos", "HivePos",
        "Health", "HurtTime", "HurtByTimestamp", "DeathTime", "InLove", "LoveCause", "ForcedAge")
        .flatMap { listOf(it, it.lowercase()) }.toSet()
    private val residenceTimers = setOf("ticks_in_hive", "min_ticks_in_hive", "TicksInHive", "MinOccupationTicks")
    private val entityKeys = setOf("entity_data", "EntityData")
    private val ageKeys = setOf("Age", "age")
    private data class Scope(val entity: Boolean = false, val bees: Boolean = false, val components: Boolean = false, val depth: Int = 0)

    fun matches(a: ByteArray, b: ByteArray): Boolean = runCatching {
        normalized(ItemNbtReader.read(a)) == normalized(ItemNbtReader.read(b))
    }.getOrDefault(false)

    internal fun normalized(value: Any): Any = normalize(value, Scope())

    private fun normalize(value: Any, scope: Scope): Any = when (value) {
        is Map<*, *> -> normalizeMap(value, scope)
        is List<*> -> normalizeList(value, scope)
        else -> value
    }

    private fun normalizeList(value: List<*>, scope: Scope): List<Any> {
        val child = scope.copy(components = false, depth = scope.depth + 1)
        return value.map { normalize(requireNotNull(it), child) }
    }

    private fun normalizeMap(value: Map<*, *>, scope: Scope): Map<Any?, Any> {
        val result = linkedMapOf<Any?, Any>()
        for ((key, item) in value) {
            if (ignored(key, scope)) continue
            result[key] = normalizeEntry(key, requireNotNull(item), scope)
        }
        return result
    }

    private fun ignored(key: Any?, scope: Scope): Boolean =
        scope.entity && key in incidental || scope.bees && key in residenceTimers

    private fun normalizeEntry(key: Any?, item: Any, scope: Scope): Any {
        if (scope.entity && key in ageKeys && item is Number) return ageStage(item)
        return normalize(item, childScope(key, scope))
    }

    private fun ageStage(age: Number): Int = if (age.toLong() < 0) -1 else 0

    private fun childScope(key: Any?, scope: Scope): Scope = Scope(
        entity = scope.entity || isEntityChild(key, scope),
        bees = scope.bees || isBeeComponent(key, scope),
        components = scope.depth == 0 && key == "components",
        depth = scope.depth + 1,
    )

    private fun isEntityChild(key: Any?, scope: Scope): Boolean =
        scope.components && key == "minecraft:bucket_entity_data" || scope.bees && key in entityKeys

    private fun isBeeComponent(key: Any?, scope: Scope): Boolean = scope.components && key == "minecraft:bees"
}
