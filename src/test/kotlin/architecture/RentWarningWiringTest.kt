@file:Suppress("InvalidPackageDeclaration")
package net.badgersmc.em.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class RentWarningWiringTest {
    @Test fun `warning listener construction follows authoritative stall repository registration`() {
        val source = Files.readString(Path.of("src/main/kotlin/net/badgersmc/em/EnthusiaMarket.kt"))
        val repository = source.indexOf("ctx.registerBean(\"stallRepository\"")
        val listener = source.indexOf("rentWarnings = ctx.getBean<")
        assertTrue(repository >= 0 && listener > repository,
            "Rent warnings depend on StallRepository and must not be constructed before manual repository registration")
    }
}
