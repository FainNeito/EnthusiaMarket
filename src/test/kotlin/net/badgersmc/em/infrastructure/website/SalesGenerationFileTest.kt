package net.badgersmc.em.infrastructure.website

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFails

class SalesGenerationFileTest {
    @TempDir lateinit var directory: Path

    @Test fun `external generation is stable and explicit rotation is observed`() {
        val path = directory.resolve("generation")
        val first = UUID.randomUUID()
        Files.writeString(path, "$first\n")
        val control = SalesGenerationFile(path)
        assertEquals(first, control.read())
        assertEquals(first, control.read())
        val second = UUID.randomUUID()
        Files.writeString(path, "$second\n")
        assertEquals(second, control.read())
    }

    @Test fun `missing malformed and oversized control state fails closed`() {
        val path = directory.resolve("generation")
        val control = SalesGenerationFile(path)
        assertFails { control.read() }
        for (content in listOf("bad", "x".repeat(65), UUID.randomUUID().toString().uppercase())) {
            Files.writeString(path, content)
            assertFails { control.read() }
        }
    }
}
