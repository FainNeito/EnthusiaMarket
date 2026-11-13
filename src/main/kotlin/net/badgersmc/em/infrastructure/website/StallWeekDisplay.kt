package net.badgersmc.em.infrastructure.website

import com.google.gson.JsonParser
import java.net.URI
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.net.ssl.HttpsURLConnection

/** Fetches the same closed-week award the website publishes. Placeholder reads are memory-only. */
internal class StallWeekDisplay(
    private val clock: Clock = Clock.systemUTC(),
    private val readAward: () -> String = ::readPublicAward,
) {
    private data class Award(val week: LocalDate, val line: String)

    @Volatile private var award: Award? = null

    fun refresh() {
        award = runCatching { parse(readAward(), previousWeek()) }.getOrNull()
    }

    fun line(): String = award?.takeIf { it.week == previousWeek() }?.line ?: "Results unavailable"

    private fun previousWeek(): LocalDate = LocalDate.now(clock.withZone(ZoneId.of("America/New_York")))
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)

    private fun parse(body: String, week: LocalDate): Award? {
        val response = JsonParser.parseString(body).asJsonObject
        if (response.get("week")?.asString != week.toString()) return null
        val coverage = response.getAsJsonPrimitive("coverageComplete") ?: return null
        if (!coverage.isBoolean) return null
        if (!coverage.asBoolean) return Award(week, "Awaiting verified sales")
        val winner = response.get("winner") ?: return null
        if (winner.isJsonNull) return Award(week, "No qualifying sales")
        val name = winner.asJsonObject.getAsJsonPrimitive("name") ?: return null
        if (!name.isString || !name.asString.matches(Regex("[A-Za-z0-9_-]{1,48}"))) return null
        val stall = Regex("stall([0-9]+)", RegexOption.IGNORE_CASE).matchEntire(name.asString)
        return Award(week, stall?.let { "Stall ${it.groupValues[1]}" } ?: name.asString)
    }
}

private fun readPublicAward(): String {
    val configuredEndpoint = System.getenv("ENTHUSIA_MARKET_STALL_WEEK_URL")
    val endpoint = URI(configuredEndpoint ?: "https://enthusia.info/api/community/insights?view=stall-week")
    require(endpoint.scheme == "https" && endpoint.host != null &&
        endpoint.userInfo == null && endpoint.fragment == null)
    val connection = endpoint.toURL().openConnection() as HttpsURLConnection
    connection.connectTimeout = 3_000
    connection.readTimeout = 3_000
    connection.instanceFollowRedirects = false
    try {
        awardAccessHeaders(configuredEndpoint, System.getenv()).forEach(connection::setRequestProperty)
        check(connection.responseCode == 200)
        return connection.inputStream.use { input ->
            val bytes = input.readNBytes(2049)
            check(bytes.size <= 2048)
            bytes.toString(Charsets.UTF_8)
        }
    } finally {
        connection.disconnect()
    }
}

internal fun awardAccessHeaders(configuredEndpoint: String?, environment: Map<String, String>): Map<String, String> {
    val id = environment["ENTHUSIA_MARKET_STALL_WEEK_ACCESS_CLIENT_ID"]
    val secret = environment["ENTHUSIA_MARKET_STALL_WEEK_ACCESS_CLIENT_SECRET"]
    if (id == null && secret == null) return emptyMap()
    val credential = Regex("[!-~]{1,512}")
    require(configuredEndpoint != null && id != null && secret != null &&
        credential.matches(id) && credential.matches(secret))
    return mapOf("CF-Access-Client-Id" to id, "CF-Access-Client-Secret" to secret)
}
