package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.StallEntityCounter
import net.badgersmc.em.application.EntityLimitConfig
import net.badgersmc.em.domain.stall.EntityLimitGroup
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class EntityLimitListenerTest {

    private val group = EntityLimitGroup(total = 50, perType = mapOf("villager" to 5))

    @Test fun `configured unlimited frames bypass shared total cap`() {
        for (type in listOf("item_frame", "glow_item_frame")) {
            val limits = EntityLimitGroup(total = 1, perType = mapOf(type to -1))
            val counter = StallEntityCounter()
            counter.recount("stall", mapOf(type to 200))
            assertFalse(EntityLimitListener.decide("stall", type, limits, counter) { mapOf(type to 200) })
        }
    }

    @Test fun `bundled groups allow normal and glow frames beyond total cap`() {
        val yaml = requireNotNull(javaClass.getResource("/entitylimits.yml")).readText()
        for ((kind, limits) in EntityLimitConfig.parse(yaml)) {
            for (type in listOf("item_frame", "glow_item_frame")) {
                val counter = StallEntityCounter()
                counter.recount(kind, mapOf("item_frame" to 200, "glow_item_frame" to 200))
                assertFalse(EntityLimitListener.decide(kind, type, limits, counter) {
                    error("Unlimited frame placement must not require a boundary scan")
                }, "$kind/$type must be unlimited")
            }
        }
    }

    @Test fun `configured finite and zero frame caps are enforced`() {
        for (type in listOf("item_frame", "glow_item_frame")) {
            for (cap in listOf(0, 2)) {
                val limits = EntityLimitConfig.parse("default:\n  _total: 50\n  $type: $cap").getValue("default")
                val counter = StallEntityCounter()
                counter.recount("stall", mapOf(type to cap))
                assertTrue(EntityLimitListener.decide("stall", type, limits, counter) { mapOf(type to cap) })
            }
        }
    }

    @Test fun `finite frames remain subject to total limit`() {
        val limits = EntityLimitGroup(total = 1, perType = mapOf("item_frame" to 10))
        val counter = StallEntityCounter()
        counter.recount("stall", mapOf("armor_stand" to 1))
        assertTrue(EntityLimitListener.decide("stall", "item_frame", limits, counter) { mapOf("armor_stand" to 1) })
    }

    @Test fun `unlimited frames do not disable total limit for other entities`() {
        val limits = EntityLimitGroup(total = 1, perType = mapOf("item_frame" to -1, "villager" to 10))
        val counter = StallEntityCounter()
        counter.recount("stall", mapOf("item_frame" to 1))
        assertTrue(EntityLimitListener.decide("stall", "villager", limits, counter) { mapOf("item_frame" to 1) })
    }

    @Test fun `cancels when type at cap and authoritative confirms`() {
        val counter = StallEntityCounter()
        repeat(5) { counter.increment("stall1", "villager") }
        val cancel = EntityLimitListener.decide(
            "stall1", "villager", group, counter, rescan = { mapOf("villager" to 5) }
        )
        assertTrue(cancel)
    }

    @Test fun `allows when under type cap`() {
        val counter = StallEntityCounter()
        counter.increment("stall1", "villager")
        val cancel = EntityLimitListener.decide(
            "stall1", "villager", group, counter, rescan = { mapOf("villager" to 1) }
        )
        assertFalse(cancel)
    }

    @Test fun `cancels when total at cap even if type under`() {
        val smallTotal = EntityLimitGroup(total = 2, perType = mapOf("villager" to 99))
        val counter = StallEntityCounter()
        counter.increment("stall1", "villager")
        counter.increment("stall1", "armor_stand")
        val cancel = EntityLimitListener.decide(
            "stall1", "villager", smallTotal, counter, rescan = { mapOf("villager" to 1, "armor_stand" to 1) }
        )
        assertTrue(cancel)
    }

    @Test fun `allows on increment when accepted`() {
        val counter = StallEntityCounter()
        val cancel = EntityLimitListener.decide(
            "stall1", "villager", group, counter, rescan = { emptyMap() }
        )
        assertFalse(cancel)
        // Accepted spawn bumps the cache.
        assertTrue(counter.cachedCount("stall1", "villager") >= 1)
    }
}
