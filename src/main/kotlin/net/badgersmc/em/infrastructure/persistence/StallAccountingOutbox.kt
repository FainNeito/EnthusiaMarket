package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.shop.StallAccountingObservation
import net.badgersmc.em.domain.shop.StallAccountingRepository
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.security.MessageDigest
import java.util.UUID

/** Forced immutable observations replay in capture order and retain uncertain deliveries. */
internal class StallAccountingOutbox(private val directory: Path, private val repository: StallAccountingRepository) {
    private var sequence: Long
    init {
        Files.createDirectories(directory)
        sequence = Files.list(directory).use { paths -> paths.map { it.fileName.toString().substringBefore('.') }
            .map { it.toLongOrNull() ?: 0 }.max(Long::compareTo).orElse(0) }
    }

    @Synchronized fun append(o: StallAccountingObservation) {
        val payload = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeInt(1); out.writeUTF(o.recordingId.toString()); out.writeUTF(o.guildId)
            out.writeUTF(o.stallId); out.writeLong(o.shopId); out.writeUTF(o.itemKey); out.writeUTF(o.itemName)
            out.writeUTF(o.contributor?.toString().orEmpty()); out.writeInt(o.before); out.writeInt(o.after)
            out.writeInt(o.saleQuantity); out.writeLong(o.grossRevenue); out.writeLong(o.createdAt)
        } }.toByteArray()
        val stem = (++sequence).toString().padStart(20, '0')
        val temporary = directory.resolve("$stem.tmp")
        FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            val bytes = ByteBuffer.wrap(payload + digest(payload))
            while (bytes.hasRemaining()) channel.write(bytes)
            channel.force(true)
        }
        Files.move(temporary, directory.resolve("$stem.pending"), StandardCopyOption.ATOMIC_MOVE)
    }

    fun drain() {
        val pending = Files.list(directory).use { paths -> paths.filter { it.fileName.toString().endsWith(".pending") }.sorted().limit(100).toList() }
        pending.forEach { path ->
            // A corrupt earlier observation blocks later attribution rather than silently dropping it.
            repository.apply(read(path))
            Files.delete(path)
        }
    }

    private fun read(path: Path): StallAccountingObservation {
        require(Files.size(path) in 33..4096) { "Invalid accounting record size: $path" }
        val bytes = Files.readAllBytes(path); val payload = bytes.copyOf(bytes.size - 32)
        require(MessageDigest.isEqual(digest(payload), bytes.copyOfRange(payload.size, bytes.size))) { "Corrupt accounting record: $path" }
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            require(input.readInt() == 1)
            val id = UUID.fromString(input.readUTF()); val guild = input.readUTF(); val stall = input.readUTF()
            val shop = input.readLong(); val key = input.readUTF(); val item = input.readUTF()
            val contributor = input.readUTF().takeIf(String::isNotEmpty)?.let(UUID::fromString)
            val result = StallAccountingObservation(id, guild, stall, shop, key, item, contributor,
                input.readInt(), input.readInt(), input.readInt(), input.readLong(), input.readLong())
            require(input.available() == 0)
            result
        }
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
}
