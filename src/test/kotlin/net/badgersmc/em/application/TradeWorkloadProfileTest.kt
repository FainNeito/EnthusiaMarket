package net.badgersmc.em.application

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.RentTerms
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.domain.stall.StallState
import net.badgersmc.em.infrastructure.vault.VaultEconomyProvider
import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.block.Container
import org.bukkit.inventory.ItemStack
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Measures adapter calls, not production throughput. Real service/inventory transfers execute. */
class TradeWorkloadProfileTest {
    private lateinit var fixture: SaleFixture
    @BeforeTest fun setUp() { fixture = SaleFixture() }
    @AfterTest fun tearDown() { fixture.close() }

    @Test fun `individual sales conserve value and repeat provider calls`() {
        val f = fixture
        val start = System.nanoTime()
        repeat(100) { assertIs<ContainerTradeResult.Success>(f.service.executeSell(f.shop, f.buyer)) }
        f.assertConserved()
        assertEquals(100, f.balanceReads)
        assertEquals(100, f.withdrawals)
        assertEquals(100, f.deposits)
        println("sale-profile mode=individual trades=100 balance-reads=${f.balanceReads} " +
            "elapsed-ms=${(System.nanoTime() - start) / 1_000_000.0}")
    }

    @Test fun `bulk sales conserve the same value with one provider operation per phase`() {
        val f = fixture
        val start = System.nanoTime()
        assertIs<ContainerTradeResult.Success>(f.service.executeSellBatch(f.shop, f.buyer, 100))
        f.assertConserved()
        assertEquals(1, f.balanceReads)
        assertEquals(1, f.withdrawals)
        assertEquals(1, f.deposits)
        println("sale-profile mode=batch trades=100 balance-reads=${f.balanceReads} " +
            "elapsed-ms=${(System.nanoTime() - start) / 1_000_000.0}")
    }
}

private class SaleFixture : AutoCloseable {
    private val server = MockBukkit.mock()
    private val player = server.addPlayer()
    val buyer: UUID = player.uniqueId
    private val owner = UUID.randomUUID()
    private val vault = mockk<Economy>()
    private val balances = mutableMapOf(buyer to 1_000L, owner to 0L)
    private val stallRepository = mockk<StallRepository>()
    private val world = server.addSimpleWorld("world")
    private val chest: Container
    var balanceReads = 0
    var withdrawals = 0
    var deposits = 0
    val shop = Shop(1, "profile", owner, "world", 0, 65, 0,
        "world", 0, 64, 0, "stone", 1, "money", 1, stockCount = 100)
    val service: ContainerTradeService

    init {
        world.getBlockAt(0, 64, 0).type = Material.CHEST
        chest = world.getBlockAt(0, 64, 0).state as Container
        chest.inventory.addItem(ItemStack(Material.STONE, 64), ItemStack(Material.STONE, 36))
        configureVault()
        every { stallRepository.findById(StallId("profile")) } returns Stall(
            StallId("profile"), "profile", "world", StallState.OWNED,
            OwnerRef.solo(owner), null, 0, RentTerms.flat(100))
        service = ContainerTradeServiceHarness(stallRepository, VaultEconomyProvider(server, vault),
            mockItemStack = ItemStack(Material.STONE), mockContainer = chest)
    }

    private fun configureVault() {
        every { vault.getBalance(any<OfflinePlayer>()) } answers {
            balanceReads++
            balances.getValue(firstArg<OfflinePlayer>().uniqueId).toDouble()
        }
        every { vault.withdrawPlayer(any<OfflinePlayer>(), any()) } answers {
            withdrawals++
            changeBalance(firstArg(), -secondArg<Double>().toLong())
        }
        every { vault.depositPlayer(any<OfflinePlayer>(), any()) } answers {
            deposits++
            changeBalance(firstArg(), secondArg<Double>().toLong())
        }
    }

    private fun changeBalance(account: OfflinePlayer, amount: Long): EconomyResponse {
        balances[account.uniqueId] = balances.getValue(account.uniqueId) + amount
        return EconomyResponse(kotlin.math.abs(amount).toDouble(), balances.getValue(account.uniqueId).toDouble(),
            EconomyResponse.ResponseType.SUCCESS, "")
    }

    fun assertConserved() {
        assertEquals(900L, balances.getValue(buyer))
        assertEquals(100L, balances.getValue(owner))
        assertEquals(0, chest.inventory.contents.filterNotNull().sumOf { it.amount })
        assertEquals(100, player.inventory.contents.filterNotNull().sumOf { it.amount })
    }

    override fun close() { MockBukkit.unmock() }
}
