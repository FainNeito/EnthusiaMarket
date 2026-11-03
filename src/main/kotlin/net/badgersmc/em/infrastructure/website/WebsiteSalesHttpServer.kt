package net.badgersmc.em.infrastructure.website

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.badgersmc.em.domain.shop.WebsitePublicSaleReader
import net.badgersmc.em.websync.WebsiteSyncJson
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Opt-in loopback reader. It must be exposed only through a protected HTTPS proxy. */
internal class WebsiteSalesHttpServer(private val port: Int, private val token: String,
                                      private val reader: WebsitePublicSaleReader) : AutoCloseable {
    private val executor = ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue(16),
        { task -> Thread(task, "EnthusiaMarket-SalesRead").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    private var server: HttpServer? = null

    fun start() {
        require(port in 1..65535 && token.length in 32..8192 && token.none { it == '\r' || it == '\n' })
        check(server == null)
        val listener = HttpServer.create(InetSocketAddress("127.0.0.1", port), 16)
        try {
            listener.createContext("/api/community/sales", ::handle)
            listener.executor = executor; listener.start(); server = listener
        } catch (failure: Exception) { listener.stop(0); throw failure }
    }

    private fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestURI.path != "/api/community/sales") { respond(exchange, 404, mapOf("error" to "not_found")); return }
            if (exchange.requestMethod != "GET") { respond(exchange, 405, mapOf("error" to "method_not_allowed")); return }
            val supplied = exchange.requestHeaders.getFirst("Authorization") ?: ""
            if (!MessageDigest.isEqual(supplied.toByteArray(Charsets.UTF_8), "Bearer $token".toByteArray(Charsets.UTF_8))) {
                respond(exchange, 401, mapOf("error" to "unauthorized")); return
            }
            val query = exchange.requestURI.rawQuery
            val after = if (query.isNullOrEmpty()) 0L else {
                if (!query.matches(Regex("after=[0-9]{1,19}"))) { respond(exchange, 400, mapOf("error" to "invalid_cursor")); return }
                query.substringAfter('=').toLongOrNull() ?: run { respond(exchange, 400, mapOf("error" to "invalid_cursor")); return }
            }
            val page = reader.readAfter(after)
            require(page.items.size <= 250 && page.checkpointCursor >= after)
            require(page.items.zipWithNext().all { (left, right) -> left.id < right.id })
            require(page.items.all { it.id > after && it.id <= page.checkpointCursor })
            require(page.checkpointCursor == (page.items.lastOrNull()?.id ?: after))
            require(page.nextCursor == null || page.nextCursor == page.checkpointCursor)
            val items = page.items.map { sale ->
                require(sale.quantity in 1..1_000_000_000 && sale.gold in 1..1_000_000_000_000L)
                require(sale.stall.matches(Regex("[A-Za-z0-9_.:-]{1,80}")) && sale.item.matches(Regex("[a-z0-9_]{1,80}")))
                require(sale.guild.length <= 80)
                mapOf("id" to "market-${sale.id}", "stall" to sale.stall, "guild" to sale.guild,
                    "item" to sale.item, "quantity" to sale.quantity, "gold" to sale.gold,
                    "occurredAt" to Instant.ofEpochMilli(sale.occurredAt).toString(), "direction" to "SELL", "currency" to "RAW_GOLD")
            }
            respond(exchange, 200, mapOf("items" to items, "complete" to false, "nextCursor" to page.nextCursor?.toString(),
                "checkpointCursor" to page.checkpointCursor.toString()))
        } catch (_: Exception) {
            try { respond(exchange, 503, mapOf("error" to "sales_unavailable")) } catch (_: Exception) { /* Client disconnected. */ }
        } finally { exchange.close() }
    }

    private fun respond(exchange: HttpExchange, status: Int, payload: Any) {
        val bytes = WebsiteSyncJson.bytes(payload)
        require(bytes.size <= 150_000)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    override fun close() { server?.stop(0); server = null; executor.shutdownNow() }
}
