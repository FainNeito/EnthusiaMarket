package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import net.badgersmc.em.domain.shop.SignDirection
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

/** Immutable forced records. Only acknowledged SQL delivery permits deletion. */
internal class ShopHistoryOutbox(
    private val directory: Path,
    private val repository: ShopTransactionRepository,
    private val reportCorruption: (Exception) -> Unit = {},
    private val acknowledge: (Path) -> Unit = { Files.delete(it) },
) {
    init { Files.createDirectories(directory) }

    fun append(tx: ShopTransaction): UUID {
        val id = UUID.randomUUID()
        val payload = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(1)
                output.writeUTF(id.toString())
                output.writeLong(tx.shopId)
                output.writeUTF(tx.owner.toString())
                output.writeUTF(tx.buyer.toString())
                output.writeUTF(tx.direction.name)
                output.writeUTF(tx.item)
                output.writeInt(tx.quantity)
                output.writeLong(tx.totalPrice)
                output.writeLong(tx.createdAt)
                output.writeBoolean(tx.notified)
            }
        }.toByteArray()
        val bytes = ByteBuffer.wrap(payload + digest(payload))
        val temporary = directory.resolve("$id.tmp")
        FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            while (bytes.hasRemaining()) channel.write(bytes)
            channel.force(true)
        }
        Files.move(temporary, directory.resolve("$id.pending"), StandardCopyOption.ATOMIC_MOVE)
        return id
    }

    fun drain(limit: Int = 100) {
        val records = Files.list(directory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".pending") }.limit(limit.toLong()).toList()
        }
        records.forEach { path ->
            val record = try { read(path) }
            catch (failure: IllegalArgumentException) { quarantine(path, failure) }
            catch (failure: java.io.EOFException) { quarantine(path, failure) }
            catch (failure: java.io.UTFDataFormatException) { quarantine(path, failure) }
            if (record == null) return@forEach
            val (id, tx) = record
            repository.recordOnce(id, tx)
            acknowledge(path)
        }
    }

    private fun quarantine(path: Path, failure: Exception): Pair<UUID, ShopTransaction>? {
        // Keep immutable bytes without letting a poisoned record starve later sales.
        Files.move(path, path.resolveSibling(path.fileName.toString().removeSuffix(".pending") + ".corrupt"),
            StandardCopyOption.ATOMIC_MOVE)
        reportCorruption(failure)
        return null
    }

    private fun read(path: Path): Pair<UUID, ShopTransaction> {
        require(Files.size(path) in 33..4096) { "Invalid history record size: ${path.fileName}" }
        val bytes = Files.readAllBytes(path)
        val payload = bytes.copyOfRange(0, bytes.size - 32)
        require(MessageDigest.isEqual(digest(payload), bytes.copyOfRange(bytes.size - 32, bytes.size))) {
            "History checksum mismatch: ${path.fileName}"
        }
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            require(input.readInt() == 1) { "Unsupported history record version" }
            val id = UUID.fromString(input.readUTF())
            require(path.fileName.toString() == "$id.pending") { "History recording id mismatch" }
            val tx = ShopTransaction(
                shopId = input.readLong(), owner = UUID.fromString(input.readUTF()), buyer = UUID.fromString(input.readUTF()),
                direction = SignDirection.valueOf(input.readUTF()), item = input.readUTF(), quantity = input.readInt(),
                totalPrice = input.readLong(), createdAt = input.readLong(), notified = input.readBoolean(),
            )
            require(input.available() == 0) { "Unexpected trailing history data" }
            id to tx
        }
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
}
