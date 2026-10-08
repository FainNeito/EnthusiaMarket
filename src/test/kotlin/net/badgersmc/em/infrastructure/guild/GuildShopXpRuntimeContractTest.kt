package net.badgersmc.em.infrastructure.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.ServicesManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.URLClassLoader
import java.nio.file.Path
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.assertEquals

class GuildShopXpRuntimeContractTest {
    @Test fun `real companion interface works through isolated plugin classloader using JDK values`() {
        val configured = System.getenv("LUMAGUILDS_JAR")
        assumeTrue(configured != null, "Set LUMAGUILDS_JAR to execute the paired-runtime contract")
        val path = checkNotNull(configured)
        URLClassLoader(arrayOf(Path.of(path).toUri().toURL()), ClassLoader.getPlatformClassLoader()).use { loader ->
            // The main CI release pin is still 3.0.17 until the companion PR is merged/released.
            // Make that limitation explicit; local paired-artifact validation must execute this test.
            val supported = loader.findResource("net/lumalyte/lg/api/GuildShopXpApi.class") != null
            assumeTrue(supported, "Pinned companion has no GuildShopXpApi: paired-runtime release gate remains pending")
            @Suppress("UNCHECKED_CAST")
            val api = Class.forName("net.lumalyte.lg.api.GuildShopXpApi", true, loader) as Class<Any>
            val calls = mutableListOf<String>()
            val provider = Proxy.newProxyInstance(loader, arrayOf(api)) { _, method, args ->
                calls.add(method.name)
                when (method.name) {
                    "apiVersion" -> 1
                    "prepare" -> {
                        assertEquals(4, args.size)
                        assertEquals("java.util.UUID", args[0].javaClass.name)
                        "PREPARED"
                    }
                    "complete" -> "AWARDED:5"
                    else -> error("Unexpected API call")
                }
            }
            val plugin = mockk<Plugin>()
            every { plugin.isEnabled } returns true
            val manager = mockk<ServicesManager>()
            every { manager.knownServices } returns listOf(api)
            every { manager.getRegistration(api) } returns RegisteredServiceProvider(api, provider, ServicePriority.Normal, plugin)
            mockkStatic(Bukkit::class)
            try {
                every { Bukkit.getServicesManager() } returns manager
                val gateway = BukkitGuildShopXpGateway()
                val id = UUID.randomUUID()
                assertEquals("PREPARED", gateway.prepare(id, UUID.randomUUID(), UUID.randomUUID(), 1000))
                assertEquals("AWARDED:5", gateway.complete(id))
                assertEquals(listOf("apiVersion", "prepare", "apiVersion", "complete"), calls)
            } finally { unmockkAll() }
        }
    }
}
