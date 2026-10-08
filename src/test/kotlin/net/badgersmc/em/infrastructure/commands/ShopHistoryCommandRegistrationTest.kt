package net.badgersmc.em.infrastructure.commands

import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import kotlin.test.Test
import kotlin.test.assertTrue

class ShopHistoryCommandRegistrationTest {
    @Test fun `history keeps legacy paging and exposes today all and range`() {
        WebsiteSyncSecretArgumentRegistration.register()
        val definitions = PaperCommandScanner().scanCommands(
            "net.badgersmc.em.infrastructure.commands", javaClass.classLoader,
        )
        val shop = definitions.single { it.annotation.name == "shop" }
        val paths = shop.subcommands.map { it.path.joinToString(" ") }.toSet()
        assertTrue("history" in paths)
        assertTrue("history today" in paths, "Missing today filter")
        assertTrue("history all" in paths, "Missing retained-history filter")
        assertTrue("history range" in paths, "Missing date-range filter")
    }
}
