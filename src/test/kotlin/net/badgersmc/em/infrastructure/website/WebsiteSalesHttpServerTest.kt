package net.badgersmc.em.infrastructure.website

import net.badgersmc.em.domain.shop.WebsitePublicSale
import net.badgersmc.em.domain.shop.WebsitePublicSalePage
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class WebsiteSalesHttpServerTest {
    @Test fun `missing or short token cannot start the listener`() {
        val server = WebsiteSalesHttpServer(8124, "short", { UUID.randomUUID() }) { WebsitePublicSalePage(emptyList(), null, 0) }
        try { assertFailsWith<IllegalArgumentException> { server.start() } } finally { server.close() }
    }
    @Test fun `read only feed requires token and exposes only bounded sale projection`() {
        val port = ServerSocket(0).use { it.localPort }
        val token = "test-only-" + "a".repeat(32)
        val generation = UUID.randomUUID()
        val recording = UUID.randomUUID()
        val server = WebsiteSalesHttpServer(port, token, { generation }) { after ->
            WebsitePublicSalePage(listOf(WebsitePublicSale(after + 1, "stall1", "Guild", "stone", 2, 5, 1_700_000_000_000, recording)), null, after + 1)
        }
        server.start()
        try {
            val client = HttpClient.newHttpClient()
            val base = "http://127.0.0.1:$port/api/community/sales"
            fun get(path: String, auth: Boolean = true): HttpResponse<String> {
                val request = HttpRequest.newBuilder(URI.create(base + path))
                if (auth) request.header("Authorization", "Bearer $token")
                return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString())
            }
            assertEquals(401, get("", false).statusCode())
            assertEquals(400, get("").statusCode(), "legacy consumers must fail closed")
            assertEquals(400, get("?protocol=2&after=-1&generation=$generation").statusCode())
            assertEquals(400, get("?protocol=2&after=10&generation=$generation&extra=1").statusCode())
            val discovery = get("?protocol=2")
            assertEquals(200, discovery.statusCode())
            assertTrue(discovery.body().contains("\"generation\":\"$generation\""))
            assertFalse(discovery.body().contains("items"))
            assertFalse(discovery.body().contains("checkpointCursor"))
            val mismatch = get("?protocol=2&after=10&generation=${UUID.randomUUID()}")
            assertEquals(409, mismatch.statusCode())
            assertFalse(mismatch.body().contains("items"))
            assertFalse(mismatch.body().contains("checkpointCursor"))
            val post = HttpRequest.newBuilder(URI.create(base)).header("Authorization", "Bearer $token")
                .POST(HttpRequest.BodyPublishers.noBody()).build()
            assertEquals(405, client.send(post, HttpResponse.BodyHandlers.ofString()).statusCode())
            val result = get("?protocol=2&after=10&generation=$generation")
            assertEquals(200, result.statusCode())
            assertTrue(result.body().contains("\"id\":\"market-$recording\""))
            assertTrue(result.body().contains("\"checkpointCursor\":\"11\""))
            assertTrue(result.body().contains("\"complete\":false"))
            assertFalse(result.body().contains("buyer"))
            assertFalse(result.body().contains("owner"))
        } finally { server.close() }
    }

    @Test fun `invalid provider cursors fail closed`() {
        val port = ServerSocket(0).use { it.localPort }
        val token = "test-only-" + "a".repeat(32)
        val generation = UUID.randomUUID()
        val server = WebsiteSalesHttpServer(port, token, { generation }) {
            WebsitePublicSalePage(listOf(WebsitePublicSale(1, "stall1", "", "stone", 1, 5, 1_700_000_000_000, UUID.randomUUID())), 7, 8)
        }
        server.start()
        try {
            val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/api/community/sales?protocol=2&after=0&generation=$generation"))
                .header("Authorization", "Bearer $token").GET().build()
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(503, response.statusCode())
            assertFalse(response.body().contains("stall1"))
        } finally { server.close() }
    }

    @Test fun `rotation during page read refuses the entire page`() {
        val port = ServerSocket(0).use { it.localPort }
        val token = "test-only-" + "a".repeat(32)
        val first = UUID.randomUUID()
        val current = AtomicReference(first)
        val server = WebsiteSalesHttpServer(port, token, current::get) { after ->
            current.set(UUID.randomUUID())
            WebsitePublicSalePage(listOf(WebsitePublicSale(after + 1, "stall1", "", "stone", 1, 5,
                1_700_000_000_000, UUID.randomUUID())), null, after + 1)
        }
        server.start()
        try {
            val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/api/community/sales?protocol=2&after=0&generation=$first"))
                .header("Authorization", "Bearer $token").GET().build()
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(409, response.statusCode())
            assertFalse(response.body().contains("items"))
            assertFalse(response.body().contains("checkpointCursor"))
        } finally { server.close() }
    }
}
