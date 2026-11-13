package net.badgersmc.em.infrastructure.persistence

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import net.badgersmc.em.domain.shop.ShopSaleAttribution
import net.badgersmc.em.domain.shop.SignDirection
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.DataInputStream
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShopHistoryOutboxTest {
    @TempDir lateinit var directory: Path
    private val sale = ShopTransaction(shopId = 42, owner = UUID.randomUUID(), buyer = UUID.randomUUID(),
        direction = SignDirection.SELL, item = "diamond", quantity = 3, totalPrice = 300, createdAt = 12345)

    @Test fun `unattributed records retain version one format for older readers`() {
        val repository = mockk<ShopTransactionRepository>()
        val id = ShopHistoryOutbox(directory, repository).append(sale)
        val bytes = Files.readAllBytes(directory.resolve("$id.pending"))
        val payload = bytes.copyOfRange(0, bytes.size - 32)
        assertEquals(true, MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(payload), bytes.takeLast(32).toByteArray()))
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            assertEquals(1, input.readInt())
            assertEquals(id.toString(), input.readUTF())
            assertEquals(sale.shopId, input.readLong())
            assertEquals(sale.owner.toString(), input.readUTF())
            assertEquals(sale.buyer.toString(), input.readUTF())
            assertEquals(sale.direction.name, input.readUTF())
            assertEquals(sale.item, input.readUTF())
            assertEquals(sale.quantity, input.readInt())
            assertEquals(sale.totalPrice, input.readLong())
            assertEquals(sale.createdAt, input.readLong())
            assertEquals(sale.notified, input.readBoolean())
            assertEquals(0, input.available(), "legacy records must not contain trailing attribution")
        }
        every { repository.recordOnce(id, sale) } returns Unit
        ShopHistoryOutbox(directory, repository).drain()
        verify(exactly = 1) { repository.recordOnce(id, sale) }
    }

    @Test fun `sale attribution survives outbox retries`() {
        val repository = mockk<ShopTransactionRepository>()
        val attributed = ShopSaleAttribution(stallId = "stall17", guildId = null, publicCashEligible = true)
        val outbox = ShopHistoryOutbox(directory, repository)
        val id = outbox.appendAttributed(sale, attributed)
        every { repository.recordAttributedOnce(id, sale, attributed) } throws IllegalStateException("offline")
        assertFailsWith<IllegalStateException> { outbox.drain() }
        every { repository.recordAttributedOnce(id, sale, attributed) } returns Unit
        ShopHistoryOutbox(directory, repository).drain()
        verify(exactly = 2) { repository.recordAttributedOnce(id, sale, attributed) }
    }

    @Test fun `legacy version one record remains readable without guessed attribution`() {
        val repository = mockk<ShopTransactionRepository>()
        val id = UUID.randomUUID()
        val payload = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(1); output.writeUTF(id.toString()); output.writeLong(sale.shopId)
                output.writeUTF(sale.owner.toString()); output.writeUTF(sale.buyer.toString())
                output.writeUTF(sale.direction.name); output.writeUTF(sale.item); output.writeInt(sale.quantity)
                output.writeLong(sale.totalPrice); output.writeLong(sale.createdAt); output.writeBoolean(sale.notified)
            }
        }.toByteArray()
        Files.write(directory.resolve("$id.pending"), payload + MessageDigest.getInstance("SHA-256").digest(payload))
        every { repository.recordOnce(id, sale) } returns Unit
        ShopHistoryOutbox(directory, repository).drain()
        verify(exactly = 1) { repository.recordOnce(id, sale) }
    }

    @Test fun `legacy version two attribution stays ineligible for public archive`() {
        val repository = mockk<ShopTransactionRepository>()
        val id = UUID.randomUUID()
        val payload = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(2); output.writeUTF(id.toString()); output.writeLong(sale.shopId)
                output.writeUTF(sale.owner.toString()); output.writeUTF(sale.buyer.toString())
                output.writeUTF(sale.direction.name); output.writeUTF(sale.item); output.writeInt(sale.quantity)
                output.writeLong(sale.totalPrice); output.writeLong(sale.createdAt); output.writeBoolean(sale.notified)
                output.writeBoolean(true); output.writeUTF("stall17"); output.writeBoolean(false)
            }
        }.toByteArray()
        Files.write(directory.resolve("$id.pending"), payload + MessageDigest.getInstance("SHA-256").digest(payload))
        val legacy = ShopSaleAttribution("stall17", null, publicCashEligible = false)
        every { repository.recordAttributedOnce(id, sale, legacy) } returns Unit
        ShopHistoryOutbox(directory, repository).drain()
        verify(exactly = 1) { repository.recordAttributedOnce(id, sale, legacy) }
    }

    @Test fun `SQL outage retains forced record and restart retries identical id and payload`() {
        val repository = mockk<ShopTransactionRepository>()
        val outbox = ShopHistoryOutbox(directory, repository)
        val id = outbox.append(sale)
        every { repository.recordOnce(id, sale) } throws IllegalStateException("offline")
        assertFailsWith<IllegalStateException> { outbox.drain() }
        assertEquals(1, Files.list(directory).use { it.count() })
        every { repository.recordOnce(id, sale) } returns Unit
        ShopHistoryOutbox(directory, repository).drain()
        verify(exactly = 2) { repository.recordOnce(id, sale) }
        assertEquals(0, Files.list(directory).use { it.count() })
    }

    @Test fun `corrupt record retained and never delivered`() {
        val repository = mockk<ShopTransactionRepository>()
        val outbox = ShopHistoryOutbox(directory, repository)
        val id = outbox.append(sale)
        val path = directory.resolve("$id.pending")
        val bytes = Files.readAllBytes(path)
        bytes[10] = (bytes[10].toInt() xor 1).toByte()
        Files.write(path, bytes)
        outbox.drain()
        verify(exactly = 0) { repository.recordOnce(any(), any()) }
        assertEquals(true, Files.exists(directory.resolve("$id.corrupt")))
    }

    @Test fun `acknowledgement failure replays same receipt after restart`() {
        val repository = mockk<ShopTransactionRepository>()
        val outbox = ShopHistoryOutbox(directory, repository, acknowledge = { throw java.io.IOException("read only") })
        val id = outbox.append(sale)
        every { repository.recordOnce(id, sale) } returns Unit
        assertFailsWith<java.io.IOException> { outbox.drain() }
        ShopHistoryOutbox(directory, repository).drain()
        verify(exactly = 2) { repository.recordOnce(id, sale) }
        assertEquals(0, Files.list(directory).use { it.count() })
    }

    @Test fun `corrupt records do not starve valid sales and incomplete temporary files are ignored`() {
        val repository = mockk<ShopTransactionRepository>()
        Files.write(directory.resolve("broken.pending"), byteArrayOf(1))
        Files.write(directory.resolve("incomplete.tmp"), byteArrayOf(1))
        val outbox = ShopHistoryOutbox(directory, repository)
        val id = outbox.append(sale)
        every { repository.recordOnce(id, sale) } returns Unit
        outbox.drain()
        verify(exactly = 1) { repository.recordOnce(id, sale) }
        assertEquals(true, Files.exists(directory.resolve("broken.corrupt")))
        assertEquals(true, Files.exists(directory.resolve("incomplete.tmp")))
    }
}
