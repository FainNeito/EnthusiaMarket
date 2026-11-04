package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.shop.ShopSaleAttribution
import net.badgersmc.em.domain.shop.ShopTransaction
import net.badgersmc.em.domain.shop.SignDirection
import net.badgersmc.nexus.persistence.DatabaseFactory
import net.badgersmc.nexus.persistence.DatabaseSpec
import net.badgersmc.nexus.persistence.MigrationRunner
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Repository-level rehearsal only: this does not start Paper or prove server downgrade compatibility. */
class LegacyReleaseRepositoryRehearsalTest {
    @TempDir lateinit var directory: Path

    @Test fun `official 1 0 52 repository records legacy sale while newer pending sale survives and replays`() {
        val jar = System.getenv("MARKET_LEGACY_RELEASE_JAR")?.takeIf { it.isNotBlank() }
        assumeTrue(jar != null, "Set MARKET_LEGACY_RELEASE_JAR to run the separately provisioned binary rehearsal")
        val path = Path.of(jar!!)
        val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
            .joinToString("") { "%02x".format(it) }
        assertEquals("1c7dee48dbe060a826a00ca85d828b656bd690e88387e9a49da031e7425d0483", hash)

        val ds = DatabaseFactory.open(DatabaseSpec.Sqlite(directory.resolve("rehearsal.db").toFile()))
        try {
            MigrationRunner(ds, resourcePrefix = "migrations", classLoader = javaClass.classLoader).runAll()
            val owner = UUID.randomUUID()
            val currentSale = ShopTransaction(shopId = 42, owner = owner, buyer = UUID.randomUUID(),
                direction = SignDirection.SELL, item = "diamond", quantity = 3, totalPrice = 300, createdAt = 12345)
            val repository = ShopTransactionRepositorySql(ds)
            val outboxPath = directory.resolve("outbox")
            val outbox = ShopHistoryOutbox(outboxPath, repository)
            val receipt = outbox.appendAttributed(currentSale, ShopSaleAttribution("stall17", null, true))
            val pending = outboxPath.resolve("$receipt.pending")
            val before = Files.readAllBytes(pending)

            LegacyClassLoader(path.toUri().toURL(), javaClass.classLoader).use { loader ->
                val runnerClass = loader.loadClass("net.badgersmc.nexus.persistence.MigrationRunner")
                assertEquals(loader, runnerClass.classLoader, "migration runner must come from the verified release")
                val runner = runnerClass.getConstructor(DataSource::class.java, String::class.java, ClassLoader::class.java)
                    .newInstance(ds, "migrations", loader)
                val discovered = runnerClass.getMethod("discover").invoke(runner) as List<*>
                val versions = discovered.map { entry ->
                    entry!!.javaClass.getMethod("getVersion").invoke(entry) as Int
                }
                assertTrue(versions.isNotEmpty(), "the legacy runner must discover its bundled migrations")
                assertEquals(29, versions.maxOrNull(), "legacy discovery must not use newer classpath migrations")
                val applied = runnerClass.getMethod("runAll").invoke(runner) as List<*>
                assertTrue(applied.isEmpty(), "older runner must not rerun migrations on upgraded database")
                ds.connection.use { connection -> connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT COUNT(*) FROM schema_migration WHERE version >= 30").use { rows ->
                        assertTrue(rows.next())
                        assertTrue(rows.getInt(1) > 0, "older runner must preserve newer migration records")
                    }
                } }
                val directionClass = loader.loadClass("net.badgersmc.em.domain.shop.SignDirection")
                val sell = directionClass.enumConstants.single { (it as Enum<*>).name == "SELL" }
                val saleClass = loader.loadClass("net.badgersmc.em.domain.shop.ShopTransaction")
                val sale = saleClass.getConstructor(java.lang.Long.TYPE, java.lang.Long.TYPE, UUID::class.java,
                    UUID::class.java, directionClass, String::class.java, java.lang.Integer.TYPE,
                    java.lang.Long.TYPE, java.lang.Long.TYPE, java.lang.Boolean.TYPE)
                    .newInstance(0L, 43L, owner, UUID.randomUUID(), sell, "stone", 1, 5L, 12346L, false)
                val legacyClass = loader.loadClass("net.badgersmc.em.infrastructure.persistence.ShopTransactionRepositorySql")
                assertEquals(loader, legacyClass.classLoader, "legacy class must come from the verified release")
                val legacy = legacyClass.getConstructor(DataSource::class.java).newInstance(ds)
                legacyClass.getMethod("record", saleClass).invoke(legacy, sale)
            }

            assertTrue(Files.readAllBytes(pending).contentEquals(before), "legacy repository must not consume pending bytes")
            assertTrue(WebsiteSalesArchiveSql(ds).readAfter(0).items.isEmpty(), "legacy sale has no public archive identity")
            outbox.drain()
            val page = WebsiteSalesArchiveSql(ds).readAfter(0)
            assertEquals(listOf(receipt), page.items.map { it.recordingId })
            assertEquals(2, repository.findByOwner(owner, 10, 0).size)
            assertTrue(Files.notExists(pending))
        } finally { (ds as? AutoCloseable)?.close() }
    }

    private class LegacyClassLoader(url: URL, parent: ClassLoader) : URLClassLoader(arrayOf(url), parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (!name.startsWith("net.badgersmc.em.") &&
                !name.startsWith("net.badgersmc.nexus.persistence.MigrationRunner")) return super.loadClass(name, resolve)
            synchronized(getClassLoadingLock(name)) {
                val loaded = findLoadedClass(name) ?: findClass(name)
                if (resolve) resolveClass(loaded)
                return loaded
            }
        }

        override fun getResources(name: String): java.util.Enumeration<URL> =
            if (name == "migrations" || name.startsWith("migrations/")) findResources(name) else super.getResources(name)

        override fun getResource(name: String): URL? =
            if (name == "migrations" || name.startsWith("migrations/")) findResource(name) else super.getResource(name)
    }
}
