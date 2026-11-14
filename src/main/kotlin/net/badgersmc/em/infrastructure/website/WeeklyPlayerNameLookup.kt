package net.badgersmc.em.infrastructure.website

import com.google.gson.Gson
import com.google.gson.JsonParseException
import net.badgersmc.em.domain.ports.WeeklyPlayerNames
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.bufferedReader

internal class WeeklyPlayerNameLookup private constructor(
    private val currentName: (UUID) -> String?,
    private val savedNames: Map<UUID, String>,
) : WeeklyPlayerNames {
    override fun nameOf(playerId: UUID): String? =
        currentName(playerId)?.takeIf(::safeName) ?: savedNames[playerId]

    private data class CachedProfile(val uuid: String?, val name: String?) {
        fun identifiedName(): Pair<UUID, String>? {
            val name = name?.takeIf(::safeName) ?: return null
            val id = runCatching { UUID.fromString(uuid) }.getOrNull() ?: return null
            if (!id.toString().equals(uuid, ignoreCase = true)) return null
            return id to name
        }
    }

    companion object {
        fun load(
            cache: Path,
            currentName: (UUID) -> String?,
            warn: (String) -> Unit = {},
        ): WeeklyPlayerNameLookup {
            val profiles = try {
                cache.bufferedReader().use { Gson().fromJson(it, Array<CachedProfile?>::class.java) }.orEmpty()
            } catch (_: NoSuchFileException) {
                emptyArray()
            } catch (_: IOException) {
                warn("Could not read the server's saved player names for weekly stall awards.")
                emptyArray()
            } catch (_: JsonParseException) {
                warn("Could not parse the server's saved player names for weekly stall awards.")
                emptyArray()
            }
            val saved = profiles.mapNotNull { it?.identifiedName() }
                .groupBy({ it.first }, { it.second })
                .mapNotNull { (id, names) -> names.distinct().singleOrNull()?.let { id to it } }
                .toMap()
            return WeeklyPlayerNameLookup(currentName, saved)
        }

        private fun safeName(name: String): Boolean =
            name.isNotBlank() && name.length <= 48 && name.none(Char::isISOControl)
    }
}
