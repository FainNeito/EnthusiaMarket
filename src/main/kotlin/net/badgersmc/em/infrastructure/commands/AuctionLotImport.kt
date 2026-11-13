package net.badgersmc.em.infrastructure.commands

import net.badgersmc.em.application.AuctionLot
import net.badgersmc.em.application.AuctionLotPlan
import net.badgersmc.em.domain.stall.StallId
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID

internal class AuctionLotImport(private val clock: Clock = Clock.systemUTC()) {
    private data class Preview(val plan: AuctionLotPlan, val token: String, val expiresAt: Instant)

    private val previews = mutableMapOf<String, Preview>()

    @Synchronized
    fun remember(actor: String, plan: AuctionLotPlan): String {
        val now = clock.instant()
        previews.values.removeIf { !now.isBefore(it.expiresAt) }
        val token = UUID.randomUUID().toString()
        previews[actor] = Preview(plan, token, now.plusSeconds(300))
        return token
    }

    @Synchronized
    fun discard(actor: String) {
        previews.remove(actor)
    }

    @Synchronized
    fun consume(actor: String, token: String): AuctionLotPlan {
        val preview = previews[actor]
        require(preview != null && preview.token == token && clock.instant().isBefore(preview.expiresAt)) {
            "No matching active preview for you. Preview the file again."
        }
        previews.remove(actor)
        return preview.plan
    }

    companion object {
        private const val MAX_BYTES = 65_536
        private val identifier = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")
        private val bid = Regex("[0-9]+")

        fun actor(sender: CommandSender): String = when (sender) {
            is Player -> sender.uniqueId.toString()
            is ConsoleCommandSender -> "console"
            else -> throw IllegalArgumentException("Use an in-game staff account or the server console.")
        }

        fun read(directory: Path, file: String): List<AuctionLot> {
            require(file.endsWith(".csv") && identifier.matches(file.removeSuffix(".csv"))) {
                "Supply a CSV filename, such as opening.csv."
            }
            val path = directory.resolve(file)
            require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                "Put the CSV in the plugin's auction-lots folder."
            }
            val bytes = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(MAX_BYTES + 1) }
            require(bytes.size <= MAX_BYTES) { "CSV exceeds 64 KiB." }
            return parse(Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString())
        }

        fun validateRound(round: String) {
            require(identifier.matches(round)) {
                "Use an opening-round ID of up to 64 letters, digits, dots, underscores or hyphens."
            }
        }

        fun parse(csv: String): List<AuctionLot> {
            val rows = csv.removePrefix("\uFEFF").lines()
            require(rows.first().split(',').map(String::trim) == listOf("stall_id", "min_bid")) {
                "CSV must start with stall_id,min_bid."
            }
            val seen = mutableSetOf<String>()
            val lots = rows.drop(1).mapIndexedNotNull { index, row ->
                if (row.isBlank()) return@mapIndexedNotNull null
                val columns = row.split(',').map(String::trim)
                val line = index + 2
                require(columns.size == 2 && identifier.matches(columns[0])) {
                    "Line $line must contain a stall ID and minimum bid."
                }
                val (id, amount) = columns
                require(seen.add(id.lowercase(Locale.ROOT))) { "Duplicate stall $id on line $line." }
                val price = amount.takeIf(bid::matches)?.toLongOrNull()
                require(price != null && price > 0) { "Line $line needs a positive whole minimum bid." }
                AuctionLot(StallId(id), price)
            }
            require(lots.size in 1..50) { "Supply between 1 and 50 auction lots." }
            return lots
        }
    }
}
