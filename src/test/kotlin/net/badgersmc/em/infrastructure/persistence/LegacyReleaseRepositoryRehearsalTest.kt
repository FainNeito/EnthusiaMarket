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
            if (!name.startsWith("net.badgersmc.em.")) return super.loadClass(name, resolve)
            synchronized(getClassLoadingLock(name)) {
                val loaded = findLoadedClass(name) ?: findClass(name)
                if (resolve) resolveClass(loaded)
                return loaded
            }
        }
    }
}
