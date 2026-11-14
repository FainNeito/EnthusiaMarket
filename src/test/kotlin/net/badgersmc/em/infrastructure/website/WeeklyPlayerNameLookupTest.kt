package net.badgersmc.em.infrastructure.website

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.writeText

class WeeklyPlayerNameLookupTest {
    @TempDir
    lateinit var directory: Path

    private val owner = UUID.fromString("370d7799-0d58-41d9-a89f-fa45cea8f2fc")
    private val other = UUID.fromString("44278957-5f26-413e-8512-9566c2b9ae25")

    @Test
    fun `recovers a last known name by exact UUID when Paper has no live name`() {
        val cache = directory.resolve("usercache.json")
        cache.writeText("""[{"uuid":"$owner","name":"LegacyOwner","expiresOn":"2026-01-24 18:30:03 -0500"}]""")
        var current: String? = null
        val lookup = WeeklyPlayerNameLookup.load(cache, { current })

        assertEquals("LegacyOwner", lookup.nameOf(owner))
        assertNull(lookup.nameOf(other))
        current = "CurrentOwner"
        assertEquals("CurrentOwner", lookup.nameOf(owner))
    }

    @Test
    fun `ignores unsafe and contradictory cache records`() {
        val cache = directory.resolve("usercache.json")
        cache.writeText("""[
            {"uuid":"$owner","name":"First"},
            {"uuid":"$owner","name":"Conflicting"},
            {"uuid":"$other","name":"line\nbreak"},
            {"uuid":"invalid","name":"Unrelated"}
        ]""")
        val lookup = WeeklyPlayerNameLookup.load(cache, { null })

        assertNull(lookup.nameOf(owner))
        assertNull(lookup.nameOf(other))
    }

    @Test
    fun `keeps fresh lookup available when the cache is unreadable`() {
        val cache = directory.resolve("usercache.json")
        cache.writeText("{broken")
        val lookup = WeeklyPlayerNameLookup.load(cache, { "CurrentOwner" })

        assertEquals("CurrentOwner", lookup.nameOf(owner))
    }
}
