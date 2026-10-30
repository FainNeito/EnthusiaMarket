package net.badgersmc.em.interaction

import net.badgersmc.nexus.scheduler.NexusScheduler
import org.bukkit.entity.Player

/** Explicit startup binding keeps existing menu/listener constructors compatible. */
internal object AuctionBidUi {
    @Volatile var generation: Long = 0
        private set
    @Volatile private var scheduler: NexusScheduler? = null
    @Volatile private var bedrock: (Player) -> Boolean = { false }

    fun configure(value: NexusScheduler, factory: MenuFactory) {
        generation++
        scheduler = value
        bedrock = factory::shouldUseBedrockMenus
    }

    fun useBedrock(player: Player): Boolean = bedrock(player)

    fun onMain(action: () -> Unit): Boolean {
        val current = scheduler ?: return false
        val epoch = generation
        current.runOnMain { if (scheduler === current && generation == epoch) action() }
        return true
    }

    fun reset() {
        generation++
        scheduler = null
        bedrock = { false }
    }
}
