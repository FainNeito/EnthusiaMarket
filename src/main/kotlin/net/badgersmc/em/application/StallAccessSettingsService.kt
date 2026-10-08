package net.badgersmc.em.application

import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.MarketMutationGate
import net.badgersmc.em.domain.ports.StallAccessPolicy
import net.badgersmc.em.domain.stall.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Server-thread policy decisions; cached persistence/ownership observations are thread-safe. */
class StallAccessSettingsService(
    private val index: StallAccessIndex,
    private val repository: StallAccessSettingsRepository,
    private val guilds: GuildProvider,
    private val gate: MarketMutationGate,
) : StallAccessPolicy {
    private val settings = ConcurrentHashMap<String, StallAccessSettings>()
    private val uncertain = ConcurrentHashMap.newKeySet<String>()
    init { repository.all().forEach { settings[it.stallId] = it } }

    fun current(stall: Stall): StallAccessSettings = settings[stall.id.value]
        ?.takeIf { it.ownershipKey == StallAccessSettings.ownershipKey(stall) }
        ?: StallAccessSettings.defaults(stall)

    fun mayManage(stall: Stall, actor: UUID): Boolean = stall.id.value !in uncertain && index.isFresh(stall.id.value) &&
        !gate.isStallLocked(stall.id.value) && stall.state in ACTIVE_STATES && when (stall.owner.type) {
            OwnerType.NONE -> false
            OwnerType.SOLO -> soloMember(stall, actor)
            OwnerType.GUILD -> guilds.isMember(actor, stall.owner.id) &&
                guilds.hasShopPermission(actor, stall.owner.id, GuildProvider.GuildPermission.MANAGE_SHOPS)
        }

    fun edit(actor: UUID, id: String, change: (StallAccessSettings) -> StallAccessSettings): StallAccessSettings {
        val stall = index.cached(id) ?: error("Stall missing")
        check(mayManage(stall, actor)) { "Not authorized or stall reserved" }
        val previous = current(stall)
        val next = change(previous)
        check(next.stallId == previous.stallId && next.ownershipKey == previous.ownershipKey && next.revision == previous.revision)
        check(next.blacklist.none { mayManage(stall, it) }) { "An authorized stall manager cannot be blacklisted" }
        check(next.allies.isEmpty() || stall.owner.type == OwnerType.GUILD) { "Allied access requires a guild stall" }
        next.allies.filter { (ally, grants) -> grants != previous.allies[ally] }.keys.forEach { ally ->
            check(guilds.areAllied(stall.owner.id, ally)) { "Guild is not currently allied" }
        }
        return try {
            repository.save(next, stall).also { settings[id] = it }
        } catch (failure: Exception) {
            uncertain.add(id)
            index.invalidate(id)
            runCatching { refreshIfUncertain(id) }
            throw failure
        }
    }

    private fun soloMember(stall: Stall, actor: UUID): Boolean = stall.owner.id == actor.toString() || actor in stall.members

    /** Command-only reconciliation after an uncertain commit; never invoked by event checks. */
    fun refreshIfUncertain(id: String) {
        if (id !in uncertain) return
        val policy = repository.all().firstOrNull { it.stallId == id }
        if (policy == null) settings.remove(id) else settings[id] = policy
        index.refresh(id)
        uncertain.remove(id)
    }

    override fun allows(stallId: String, actor: UUID, capability: StallCapability): Boolean {
        val stall = index.cached(stallId) ?: return false
        if (stallId in uncertain || !index.isFresh(stallId)) return false
        if (stall.state !in ACTIVE_STATES) return capability !in StallAccessSettings.PRIVILEGED
        return activeAllows(stall, actor, capability)
    }

    private fun activeAllows(stall: Stall, actor: UUID, capability: StallCapability): Boolean {
        val policy = current(stall)
        if (actor in policy.blacklist) return false
        return memberAllows(stall, actor, capability) || policy.visitorAllows(capability) ||
            alliedAllows(stall.id.value, actor, capability)
    }

    override fun blacklistDenies(stallId: String, actor: UUID): Boolean = index.cached(stallId)?.let {
        it.state in ACTIVE_STATES && actor in current(it).blacklist
    } ?: false

    private fun memberAllows(stall: Stall, actor: UUID, capability: StallCapability): Boolean {
        if (!index.isFresh(stall.id.value) || gate.isStallLocked(stall.id.value)) return false
        return when (stall.owner.type) {
            OwnerType.SOLO -> soloMember(stall, actor)
            OwnerType.GUILD -> guildMemberAllows(stall, actor, capability)
            OwnerType.NONE -> false
        }
    }

    private fun guildMemberAllows(stall: Stall, actor: UUID, capability: StallCapability): Boolean {
        if (!guilds.isMember(actor, stall.owner.id)) return false
        val permission = permission(capability) ?: return true
        return guilds.hasShopPermission(actor, stall.owner.id, permission)
    }

    private fun permission(capability: StallCapability): GuildProvider.GuildPermission? = when (capability) {
            StallCapability.CHESTS -> GuildProvider.GuildPermission.ACCESS_SHOP_CHESTS
            StallCapability.STOCK -> GuildProvider.GuildPermission.EDIT_SHOP_STOCK
            StallCapability.PRICES -> GuildProvider.GuildPermission.MODIFY_SHOP_PRICES
            else -> null
        }

    override fun alliedAllows(stallId: String, actor: UUID, capability: StallCapability): Boolean {
        val stall = index.cached(stallId) ?: return false
        if (stall.owner.type != OwnerType.GUILD) return false
        if (stallId in uncertain || !index.isFresh(stallId) || gate.isStallLocked(stallId)) return false
        val policy = current(stall)
        if (actor in policy.blacklist) return false
        return policy.allies.any { (ally, capabilities) ->
            capability in capabilities && guilds.isMember(actor, ally) && guilds.areAllied(stall.owner.id, ally)
        }
    }

    companion object { private val ACTIVE_STATES = setOf(StallState.OWNED, StallState.GRACE) }
}
