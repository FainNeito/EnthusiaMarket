package net.badgersmc.em.application

import org.bukkit.Material
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Publish runtime-derived material membership for the interactive review preview. */
class SearchCatalogPreviewTest {
    @Test fun `every category has supported item members and exports its actual catalog`() {
        val items = Material.entries.filter { !it.isLegacy && it.isItem }.sortedBy { it.name }
        val categories = SearchCategory.entries.joinToString(",") { category ->
            val members = items.filter { SearchCategoryMaterials.matches(category, it) }
            assertTrue(members.isNotEmpty(), category.name)
            "\"${category.name}\":{\"aliases\":${json(category.aliases.toList())},\"items\":${json(members.map { it.name })}}"
        }
        val output = Path.of("build/reports/market-search-catalog.json")
        Files.createDirectories(output.parent)
        Files.writeString(output, "{\"categories\":{$categories},\"items\":${json(items.map { it.name })}}")
    }

    private fun json(names: List<String>) = names.joinToString(",", "[", "]") { "\"$it\"" }
}
