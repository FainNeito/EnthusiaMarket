package net.badgersmc.em.application

import kotlin.test.*

class SpecialItemMatchTest {
    private fun encodedBucket(uuid: String, variant: Int): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(java.util.zip.GZIPOutputStream(bytes)).use { out ->
            fun tag(type: Int, name: String) { out.writeByte(type); out.writeUTF(name) }
            tag(10, "")
            tag(10, "components")
            tag(10, "minecraft:bucket_entity_data")
            tag(8, "UUID"); out.writeUTF(uuid)
            tag(3, "Variant"); out.writeInt(variant)
            tag(3, "HuntingCooldown"); out.writeInt(uuid.length)
            out.writeByte(0)
            tag(8, "minecraft:custom_name"); out.writeUTF("Axolotl")
            out.writeByte(0); out.writeByte(0)
        }
        return bytes.toByteArray()
    }
    @Test fun `compressed NBT stock matches identity changes and rejects variant changes`() {
        assertTrue(SpecialItemMatch.matches(encodedBucket("one", 1), encodedBucket("another", 1)))
        assertFalse(SpecialItemMatch.matches(encodedBucket("one", 1), encodedBucket("another", 2)))
    }
    private fun bucket(uuid: String, variant: Int = 1) = mapOf("components" to mapOf(
        "minecraft:bucket_entity_data" to mapOf("UUID" to uuid, "Variant" to variant, "HuntingCooldown" to uuid.length),
        "minecraft:custom_name" to "Axolotl",
    ))
    @Test fun `identity and cooldown do not distinguish creature stock`() {
        assertEquals(SpecialItemMatch.normalized(bucket("one")), SpecialItemMatch.normalized(bucket("another")))
    }
    @Test fun `variant and item custom name remain significant`() {
        assertNotEquals(SpecialItemMatch.normalized(bucket("one", 1)), SpecialItemMatch.normalized(bucket("one", 2)))
        assertNotEquals(SpecialItemMatch.normalized(bucket("one")), SpecialItemMatch.normalized(mapOf("components" to mapOf("minecraft:custom_name" to "Different"))))
    }
    @Test fun `hive occupancy is preserved while residence timer varies`() {
        fun hive(timer: Int, count: Int) = mapOf("components" to mapOf("minecraft:bees" to List(count) {
            mapOf("entity_data" to mapOf("UUID" to "bee-$timer", "id" to "minecraft:bee"), "ticks_in_hive" to timer)
        }))
        assertEquals(SpecialItemMatch.normalized(hive(10, 3)), SpecialItemMatch.normalized(hive(100, 3)))
        assertNotEquals(SpecialItemMatch.normalized(hive(10, 3)), SpecialItemMatch.normalized(hive(10, 0)))
    }
    @Test fun `malformed item bytes fail closed`() {
        assertFalse(SpecialItemMatch.matches(byteArrayOf(1), byteArrayOf(1)))
    }
    @Test fun `custom data fields named like entity data remain significant`() {
        fun item(uuid: String) = mapOf("components" to mapOf("minecraft:custom_data" to mapOf("entity_data" to mapOf("UUID" to uuid))))
        assertNotEquals(SpecialItemMatch.normalized(item("one")), SpecialItemMatch.normalized(item("two")))
    }
    @Test fun `age matching groups babies while preserving baby adult distinction`() {
        fun item(age: Int) = mapOf("components" to mapOf("minecraft:bucket_entity_data" to mapOf("Age" to age, "Variant" to 1)))
        assertEquals(SpecialItemMatch.normalized(item(-100)), SpecialItemMatch.normalized(item(-200)))
        assertNotEquals(SpecialItemMatch.normalized(item(-100)), SpecialItemMatch.normalized(item(0)))
    }
}
