package net.badgersmc.em.application

import java.util.Locale

enum class FinderNavigationMode {
    LOCATOR, PARTICLES, BOTH;

    companion object {
        fun parse(value: String): FinderNavigationMode? = entries.firstOrNull {
            it.name == value.trim().uppercase(Locale.ROOT)
        }
    }
}
