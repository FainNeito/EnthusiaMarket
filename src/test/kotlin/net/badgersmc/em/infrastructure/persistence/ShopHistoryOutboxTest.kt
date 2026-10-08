package net.badgersmc.em.infrastructure.persistence

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import net.badgersmc.em.domain.shop.SignDirection
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShopHistoryOutboxTest {
    @TempDir lateinit var directory: Path
    private val sale = ShopTransaction(shopId = 42, owner = UUID.randomUUID(), buyer = UUID.randomUUID(),
        direction = SignDirection.SELL, item = "diamond", quantity = 3, totalPrice = 300, createdAt = 12345)

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
