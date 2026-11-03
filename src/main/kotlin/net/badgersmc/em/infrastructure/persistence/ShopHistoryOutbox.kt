package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import net.badgersmc.em.domain.shop.ShopSaleAttribution
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

    fun append(tx: ShopTransaction): UUID = write(tx, null)
    fun appendAttributed(tx: ShopTransaction, attribution: ShopSaleAttribution): UUID = write(tx, attribution)

    private fun write(tx: ShopTransaction, attribution: ShopSaleAttribution?): UUID {
        val id = UUID.randomUUID()
        val payload = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(if (attribution == null) 1 else 3)
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
                if (attribution != null) {
                    output.writeBoolean(attribution.stallId != null)
                    if (attribution.stallId != null) output.writeUTF(attribution.stallId)
                    output.writeBoolean(attribution.guildId != null)
                    if (attribution.guildId != null) output.writeUTF(attribution.guildId.toString())
                    output.writeBoolean(attribution.publicCashEligible)
                }
            }
        }.toByteArray()
        val bytes = ByteBuffer.wrap(payload + digest(payload))
        val temporary = directory.resolve("$id.tmp")
        FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            writeForced(channel, bytes)
        }
        Files.move(temporary, directory.resolve("$id.pending"), StandardCopyOption.ATOMIC_MOVE)
        return id
    }

    fun drain(limit: Int = 100) {
        val records = Files.list(directory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".pending") }.limit(limit.toLong()).toList()
        }
        records.forEach(::deliver)
    }

    private fun writeForced(channel: FileChannel, bytes: ByteBuffer) {
        while (bytes.hasRemaining()) channel.write(bytes)
        channel.force(true)
    }

    private fun deliver(path: Path) {
        val (id, tx, attribution) = readPending(path) ?: return
        if (attribution == null) repository.recordOnce(id, tx)
        else repository.recordAttributedOnce(id, tx, attribution)
        acknowledge(path)
    }

    private fun readPending(path: Path): Triple<UUID, ShopTransaction, ShopSaleAttribution?>? = try { read(path) }
        catch (failure: IllegalArgumentException) { quarantine(path, failure) }
        catch (failure: java.io.EOFException) { quarantine(path, failure) }
        catch (failure: java.io.UTFDataFormatException) { quarantine(path, failure) }

    private fun quarantine(path: Path, failure: Exception): Triple<UUID, ShopTransaction, ShopSaleAttribution?>? {
        // Keep immutable bytes without letting a poisoned record starve later sales.
        Files.move(path, path.resolveSibling(path.fileName.toString().removeSuffix(".pending") + ".corrupt"),
            StandardCopyOption.ATOMIC_MOVE)
        reportCorruption(failure)
        return null
    }

    private fun read(path: Path): Triple<UUID, ShopTransaction, ShopSaleAttribution?> {
        require(Files.size(path) in 33..4096) { "Invalid history record size: ${path.fileName}" }
        val bytes = Files.readAllBytes(path)
        val payload = bytes.copyOfRange(0, bytes.size - 32)
        require(MessageDigest.isEqual(digest(payload), bytes.copyOfRange(bytes.size - 32, bytes.size))) {
            "History checksum mismatch: ${path.fileName}"
        }
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            val version = input.readInt()
            require(version in 1..3) { "Unsupported history record version" }
            val id = UUID.fromString(input.readUTF())
            require(path.fileName.toString() == "$id.pending") { "History recording id mismatch" }
            val base = ShopTransaction(
                shopId = input.readLong(), owner = UUID.fromString(input.readUTF()), buyer = UUID.fromString(input.readUTF()),
                direction = SignDirection.valueOf(input.readUTF()), item = input.readUTF(), quantity = input.readInt(),
                totalPrice = input.readLong(), createdAt = input.readLong(), notified = input.readBoolean(),
            )
            val stall = if (version >= 2 && input.readBoolean()) input.readUTF() else null
            val guild = if (version >= 2 && input.readBoolean()) UUID.fromString(input.readUTF()) else null
            val eligible = version >= 3 && input.readBoolean()
            require(!eligible || (stall != null && guild == null)) { "Invalid public attribution" }
            val attribution = if (stall == null && guild == null) null else ShopSaleAttribution(stall, guild, eligible)
            require(input.available() == 0) { "Unexpected trailing history data" }
            Triple(id, base, attribution)
        }
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
}
