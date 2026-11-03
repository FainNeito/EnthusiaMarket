package net.badgersmc.em.infrastructure.website

import net.badgersmc.em.domain.shop.WebsitePublicSale
import net.badgersmc.em.domain.shop.WebsitePublicSalePage
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class WebsiteSalesHttpServerTest {
    @Test fun `missing or short token cannot start the listener`() {
        val server = WebsiteSalesHttpServer(8124, "short") { WebsitePublicSalePage(emptyList(), null, 0) }
        try { assertFailsWith<IllegalArgumentException> { server.start() } } finally { server.close() }
    }
    @Test fun `read only feed requires token and exposes only bounded sale projection`() {
        val port = ServerSocket(0).use { it.localPort }
        val token = "test-only-" + "a".repeat(32)
        val server = WebsiteSalesHttpServer(port, token) { after ->
            WebsitePublicSalePage(listOf(WebsitePublicSale(after + 1, "stall1", "Guild", "stone", 2, 5, 1_700_000_000_000)), null, after + 1)
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
            assertEquals(400, get("?after=-1").statusCode())
            assertEquals(400, get("?after=10&extra=1").statusCode())
            val post = HttpRequest.newBuilder(URI.create(base)).header("Authorization", "Bearer $token")
                .POST(HttpRequest.BodyPublishers.noBody()).build()
            assertEquals(405, client.send(post, HttpResponse.BodyHandlers.ofString()).statusCode())
            val result = get("?after=10")
            assertEquals(200, result.statusCode())
            assertTrue(result.body().contains("\"id\":\"market-11\""))
            assertTrue(result.body().contains("\"checkpointCursor\":\"11\""))
            assertTrue(result.body().contains("\"complete\":false"))
            assertFalse(result.body().contains("buyer"))
            assertFalse(result.body().contains("owner"))
        } finally { server.close() }
    }

    @Test fun `invalid provider cursors fail closed`() {
        val port = ServerSocket(0).use { it.localPort }
        val token = "test-only-" + "a".repeat(32)
        val server = WebsiteSalesHttpServer(port, token) {
            WebsitePublicSalePage(listOf(WebsitePublicSale(1, "stall1", "", "stone", 1, 5, 1_700_000_000_000)), 7, 8)
        }
        server.start()
        try {
            val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/api/community/sales"))
                .header("Authorization", "Bearer $token").GET().build()
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(503, response.statusCode())
            assertFalse(response.body().contains("stall1"))
        } finally { server.close() }
    }
}
