package net.badgersmc.em.infrastructure.commands

import io.mockk.every
import io.mockk.Called
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import net.badgersmc.em.domain.shop.ShopHistoryWindow
import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.ShopTransactionRepository
import net.badgersmc.em.domain.shop.SignDirection
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test

class ShopHistoryCommandsTest {
    private val repo = mockk<ShopTransactionRepository>()
    private val lang = mockk<LangService>().also {
        every { it.msg(any(), *anyVararg()) } returns Component.empty()
    }
    private val viewer = UUID.randomUUID()
    private val player = mockk<Player>(relaxed = true).also { every { it.uniqueId } returns viewer }
    private val commands = ShopCommands(
        mockk(), mockk(), mockk(), repo, mockk(), mockk(), mockk(), lang,
        mockk(), mockk(), mockk(), mockk(),
    ).also { it.historyZone = ZoneOffset.UTC }

    @AfterTest fun cleanUp() = unmockkAll()

    @Test fun `range passes bounded window and preserves dates on next page`() {
        val window = ShopHistoryWindow(
            Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(),
            Instant.parse("2026-10-09T00:00:00Z").toEpochMilli(),
        )
        val row = ShopTransaction(1, 1, viewer, UUID.randomUUID(), SignDirection.SELL, "diamond", 1, 10, window.fromMs)
        every { repo.findByOwnerOrBuyer(viewer, 11, 10, window) } returns List(11) { row }
        mockkStatic(Bukkit::class)
        val counterpart = mockk<OfflinePlayer>().also { every { it.name } returns "Buyer" }
        every { Bukkit.getOfflinePlayer(row.buyer) } returns counterpart
        commands.historyRange(player, "2026-10-01", "2026-10-08", 2)
        verify { lang.msg("shop.history.filtered_more", "command" to "/shop history range 2026-10-01 2026-10-08 3") }
        verify(exactly = 0) { repo.markNotified(any()) }
    }

    @Test fun `today uses current server calendar date`() {
        commands.historyZone = ZoneId.of("America/New_York")
        commands.historyClock = Clock.fixed(Instant.parse("2026-10-08T02:00:00Z"), ZoneOffset.UTC)
        val window = ShopHistoryWindow(
            Instant.parse("2026-10-07T04:00:00Z").toEpochMilli(),
            Instant.parse("2026-10-08T04:00:00Z").toEpochMilli(),
        )
        every { repo.findByOwnerOrBuyer(viewer, 11, 0, window) } returns emptyList()
        commands.historyToday(player)
        verify { repo.findByOwnerOrBuyer(viewer, 11, 0, window) }
        verify { lang.msg("shop.history.empty_filtered") }
    }

    @Test fun `invalid ranges never query storage`() {
        commands.historyRange(player, "2026-02-29", "2026-03-01")
        commands.historyRange(player, "2026-10-09", "2026-10-08")
        commands.historyRange(player, "bad", "date")
        verify { repo wasNot Called }
        verify(exactly = 3) { lang.msg("shop.history.invalid_dates") }
    }

    @Test fun `legacy and all keep unbounded query and existing page clamp`() {
        every { repo.findByOwnerOrBuyer(viewer, 11, 0) } returns emptyList()
        every { repo.findByOwnerOrBuyer(viewer, 11, 9990) } returns emptyList()
        commands.history(player, -1)
        commands.historyAll(player, Int.MAX_VALUE)
        verify { repo.findByOwnerOrBuyer(viewer, 11, 0) }
        verify { repo.findByOwnerOrBuyer(viewer, 11, 9990) }
        verify(exactly = 0) { repo.findByOwnerOrBuyer(any(), any(), any(), any()) }
    }

    @Test fun `console cannot read a player's history`() {
        commands.historyAll(mockk<CommandSender>(relaxed = true))
        verify { repo wasNot Called }
        verify { lang.msg("shop.cmd.players_only") }
    }
}
