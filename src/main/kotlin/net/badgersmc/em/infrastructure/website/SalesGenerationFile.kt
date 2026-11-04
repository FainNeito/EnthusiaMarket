package net.badgersmc.em.infrastructure.website

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

/** Read external control state. Operations must rotate it on restore; this cannot detect an omitted rotation. */
internal class SalesGenerationFile(private val path: Path) {
    fun read(): UUID {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) in 36..64)
        val bytes = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
            .use { it.readNBytes(65) }
        require(bytes.size in 36..64)
        val text = String(bytes, Charsets.US_ASCII).trim()
        val value = UUID.fromString(text)
        require(value.toString() == text)
        return value
    }
}
