package net.badgersmc.em.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import net.badgersmc.em.domain.ports.EconomyProvider
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.GuildSaleRewards
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.SignDirection
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.RentTerms
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.domain.stall.StallState
import org.bukkit.Bukkit
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertTrue

class GuildSaleTradeBoundaryTest {
    private val guild = UUID.randomUUID()
    private val buyer = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val economy = mockk<EconomyProvider>(relaxed = true)
    private val guilds = mockk<GuildProvider>(relaxed = true)
    private val rewards = mockk<GuildSaleRewards>(relaxed = true)
    private val stalls = mockk<StallRepository>()
    private val stock = mockk<Inventory>(relaxed = true)
    private val inventory = mockk<PlayerInventory>(relaxed = true)
    private val stack = mockk<ItemStack>(relaxed = true)
    private lateinit var service: ContainerTradeService
    private val shop = Shop(id = 42, stallId = "guild", owner = UUID.randomUUID(), signWorld = "world", signX = 0, signY = 0, signZ = 0,
        containerWorld = "world", containerX = 0, containerY = 0, containerZ = 0, sellItem = "stock", sellAmount = 1, costItem = "money", costAmount = 10)

    @BeforeEach fun setup() {
        mockkStatic(Bukkit::class)
        val player = mockk<Player>(relaxed = true)
        every { player.inventory } returns inventory
        every { player.uniqueId } returns buyer
        every { Bukkit.getPlayer(buyer) } returns player
        every { Bukkit.getPluginManager() } returns mockk(relaxed = true)
        every { Bukkit.getLogger() } returns java.util.logging.Logger.getAnonymousLogger()
        every { stalls.findById(any()) } returns Stall(StallId("guild"), "guild", "world", StallState.OWNED, OwnerRef.guild(guild.toString()), Instant.now(), 1000, RentTerms.formula(0.01))
        every { economy.balance(buyer) } returns 1000
        every { economy.withdraw(buyer, any()) } returns true
        every { guilds.bankDeposit(guild.toString(), any()) } returns true
        every { stock.contents } returns arrayOf(stack)
        every { stack.isSimilar(any()) } returns true
        every { stack.amount } returns 10
        every { inventory.addItem(any()) } returns hashMapOf()
        every { rewards.prepare(guild, buyer, shop.id) } returns id
        val container = mockk<Container>()
        every { container.inventory } returns stock
        service = ContainerTradeServiceHarness(stalls, economy, guilds, mockItemStack = stack, mockContainer = container, saleRewards = rewards)
    }

    @AfterEach fun close() { unmockkAll() }

    @Test fun `successful sale prepares before payment and completes after actual delivery`() {
        assertTrue(service.executeSell(shop, buyer) is ContainerTradeResult.Success)
        verifyOrder {
            rewards.prepare(guild, buyer, shop.id)
            economy.withdraw(buyer, 10)
            guilds.bankDeposit(guild.toString(), 10)
            inventory.addItem(any())
            rewards.completed(id)
        }
    }

    @Test fun `preparation error prevents stock payment and delivery mutations`() {
        every { rewards.prepare(any(), any(), any()) } throws IllegalStateException("unavailable")
        assertTrue(service.executeSell(shop, buyer) is ContainerTradeResult.Failure)
        verify(exactly = 0) { economy.withdraw(any(), any()); stock.setItem(any(), any()); inventory.addItem(any()); rewards.completed(any()) }
    }

    @Test fun `withdrawal failure aborts reward and restores stock`() {
        every { economy.withdraw(buyer, any()) } returns false
        assertTrue(service.executeSell(shop, buyer) is ContainerTradeResult.Failure)
        verify { rewards.aborted(id); stock.addItem(any()) }
        verify(exactly = 0) { rewards.completed(any()) }
    }

    @Test fun `failed delivery compensation never marks reward completed`() {
        every { inventory.addItem(any()) } returns hashMapOf(0 to stack)
        assertTrue(service.executeSell(shop, buyer) is ContainerTradeResult.CompensationFailed)
        verify { rewards.aborted(id) }
        verify(exactly = 0) { rewards.completed(any()) }
    }

    @Test fun `BUY direction cannot prepare sale reward`() {
        every { guilds.bankBalance(any()) } returns 1000
        every { guilds.bankWithdraw(any(), any()) } returns true
        every { inventory.containsAtLeast(any(), any()) } returns true
        service.executeBuy(shop.copy(direction = SignDirection.BUY), buyer)
        verify(exactly = 0) { rewards.prepare(any(), any(), any()); rewards.completed(any()) }
    }

    @Test fun `personal stall sale does not earn guild XP`() {
        every { stalls.findById(any()) } returns Stall(StallId("guild"), "guild", "world", StallState.OWNED, OwnerRef.solo(shop.owner), Instant.now(), 1000, RentTerms.formula(0.01))
        every { economy.deposit(shop.owner, any()) } returns true
        assertTrue(service.executeSell(shop, buyer) is ContainerTradeResult.Success)
        verify(exactly = 0) { rewards.prepare(any(), any(), any()); rewards.completed(any()) }
    }

    @Test fun `free resolved price cannot earn guild XP`() {
        val policy = mockk<GuildTradePolicyService>()
        every { policy.stanceFor(any(), any(), any()) } returns GuildTradePolicyService.TradeStance.Allowed(0.0)
        val container = mockk<Container>()
        every { container.inventory } returns stock
        service = ContainerTradeServiceHarness(stalls, economy, guilds, tradePolicy = policy, mockItemStack = stack, mockContainer = container, saleRewards = rewards)
        assertTrue(service.executeSell(shop, buyer) is ContainerTradeResult.Success)
        verify(exactly = 0) { rewards.prepare(any(), any(), any()); rewards.completed(any()); economy.withdraw(any(), any()) }
    }
}
