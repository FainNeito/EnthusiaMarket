package net.badgersmc.em.interaction

import net.badgersmc.nexus.i18n.LangService
import java.util.logging.Level
import java.util.logging.Logger

/** Bind the existing locale/logger at plugin startup without changing command/menu constructors. */
internal object StallAccessUi {
    @Volatile private var language: LangService? = null
    @Volatile private var diagnostics = Logger.getLogger("EnthusiaMarket.StallAccess")

    val labels: StallAccessLabels get() = StallAccessLabels { key ->
        val qualified = "stall_access.$key"
        language?.raw(qualified)?.takeUnless { it == qualified }
    }

    fun configure(lang: LangService, logger: Logger) {
        language = lang
        diagnostics = logger
    }

    fun reset() {
        language = null
        diagnostics = Logger.getLogger("EnthusiaMarket.StallAccess")
    }

    fun failure(context: String, failure: Throwable): String {
        diagnostics.log(Level.WARNING, "Stall setting could not be confirmed: $context", failure)
        return labels.text("error.setting_failed")
    }
}
