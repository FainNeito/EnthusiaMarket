package net.badgersmc.em.infrastructure.lumaguilds

import org.bukkit.plugin.ServicePriority
import org.mockbukkit.mockbukkit.MockBukkit
import java.net.URLClassLoader
import java.nio.file.Path
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/** Check the public bridge against the supplied companion JAR without compiling against its new API. */
class OptionalGuildAllianceLookupTest {
    @AfterTest fun cleanup() { if (MockBukkit.isMocked()) MockBukkit.unmock() }

    @Test fun `missing companion denies and supplied public API preserves current alliance decisions`() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        val guild = UUID.randomUUID()
        val ally = UUID.randomUUID()
        assertFalse(OptionalGuildAllianceLookup.areAllied(guild, ally))
        val jar = System.getenv("LUMAGUILDS_JAR")?.takeIf { it.isNotBlank() } ?: return
        URLClassLoader(arrayOf(Path.of(jar).toUri().toURL()), javaClass.classLoader).use { loader ->
            assertCompanion(server.servicesManager, plugin, loader, guild, ally)
        }
    }

    private fun assertCompanion(manager: org.bukkit.plugin.ServicesManager, plugin: org.bukkit.plugin.Plugin,
        loader: ClassLoader, guild: UUID, ally: UUID) {
            val type = runCatching { loader.loadClass("net.lumalyte.lg.api.GuildAllianceLookup") }.getOrNull() ?: return
            assertEquals(Boolean::class.javaPrimitiveType, type.getMethod("areAllied", UUID::class.java, UUID::class.java).returnType)
            var allied = true
            val provider = Proxy.newProxyInstance(loader, arrayOf(type)) { _, method, arguments ->
                if (method.name == "areAllied") allied && arguments[0] == guild && arguments[1] == ally else null
            }
            @Suppress("UNCHECKED_CAST")
            manager.register(type as Class<Any>, provider, plugin, ServicePriority.Normal)
            assertTrue(OptionalGuildAllianceLookup.areAllied(guild, ally))
            allied = false
            assertFalse(OptionalGuildAllianceLookup.areAllied(guild, ally))
            manager.unregisterAll(plugin)
            assertFalse(OptionalGuildAllianceLookup.areAllied(guild, ally))
    }
}
