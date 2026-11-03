package net.badgersmc.em.testing

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.util.UUID

/** Owns a disposable per-class schema on native loopback MariaDB, or a normal test container. */
class MarketMariaDbFixture(
    private val fixtureName: String,
    private val containerDatabaseName: String = "enthusia_market_test",
) : ExecutionCondition, BeforeAllCallback, AfterAllCallback {
    private lateinit var endpoint: Endpoint
    private var cleanup: (() -> Unit)? = null

    val jdbcUrl: String get() = endpoint.jdbcUrl
    val username: String get() = endpoint.username
    val password: String get() = endpoint.password

    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult = when {
        nativeRequested() -> ConditionEvaluationResult.enabled("Explicit native MariaDB fixture")
        DockerClientFactory.instance().isDockerAvailable -> ConditionEvaluationResult.enabled("Docker is available")
        else -> ConditionEvaluationResult.disabled("Docker is unavailable and no native MariaDB fixture is configured")
    }

    override fun beforeAll(context: ExtensionContext) {
        try {
            require(fixtureName.matches(Regex("[a-z][a-z0-9_]{0,19}"))) { "Invalid MariaDB fixture name" }
            if (nativeRequested()) startNative() else startContainer()
        } catch (failure: Throwable) {
            closeFixture(failure)
            throw failure
        }
    }

    override fun afterAll(context: ExtensionContext) {
        closeFixture(context.executionException.orElse(null))
    }

    private fun startNative() {
        val portText = System.getenv(PORT_VARIABLE)
        val port = portText?.takeIf { it.matches(Regex("[0-9]+")) }?.toIntOrNull()
        require(port != null && port in 1024..65535 && port != 3306) {
            "$PORT_VARIABLE must be a disposable loopback port in 1024..65535 other than 3306"
        }
        val user = System.getenv(USER_VARIABLE)
        val secret = System.getenv(PASSWORD_VARIABLE)
        require(!user.isNullOrBlank()) { "$USER_VARIABLE must be configured and nonblank" }
        require(!secret.isNullOrBlank()) { "$PASSWORD_VARIABLE must be configured and nonblank" }

        // Host and schema are never supplied by an operator URL or database name.
        val admin = Endpoint("jdbc:mariadb://127.0.0.1:$port/", user, secret)
        val schema = "em_fixture_${fixtureName}_${UUID.randomUUID().toString().replace("-", "")}"
        val owned = NativeSchema(admin, schema)
        cleanup = owned::close
        owned.create()
        endpoint = Endpoint(admin.jdbcUrl + schema, user, secret)
    }

    private fun startContainer() {
        val container = FixtureContainer()
            .withDatabaseName(containerDatabaseName)
            .withUsername("market_test")
            .withPassword("market_test")
        cleanup = container::stop
        container.start()
        endpoint = Endpoint(container.jdbcUrl, container.username, container.password)
    }

    private fun closeFixture(originalFailure: Throwable?) {
        val close = cleanup ?: return
        cleanup = null
        try {
            close()
        } catch (cleanupFailure: Throwable) {
            if (originalFailure == null) throw cleanupFailure
            if (cleanupFailure !== originalFailure) originalFailure.addSuppressed(cleanupFailure)
        }
    }

    private fun nativeRequested(): Boolean = NATIVE_VARIABLES.any { System.getenv(it) != null }

    private class Endpoint(val jdbcUrl: String, val username: String, val password: String)

    private class NativeSchema(private val admin: Endpoint, private val schema: String) {
        private var created = false

        fun create() {
            DriverManager.getConnection(admin.jdbcUrl, admin.username, admin.password).use { connection ->
                connection.createStatement().use { statement ->
                    // A collision must fail without taking ownership of an existing schema.
                    statement.executeUpdate("CREATE DATABASE `$schema`")
                    created = true
                }
            }
        }

        fun close() {
            if (!created) return
            DriverManager.getConnection(admin.jdbcUrl, admin.username, admin.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate("DROP DATABASE `$schema`")
                    created = false
                }
            }
        }
    }

    private class FixtureContainer : MariaDBContainer<FixtureContainer>(DockerImageName.parse("mariadb:11.8.3"))

    private companion object {
        const val PORT_VARIABLE = "MARKET_TEST_MARIADB_PORT"
        const val USER_VARIABLE = "MARKET_TEST_MARIADB_USER"
        const val PASSWORD_VARIABLE = "MARKET_TEST_MARIADB_PASSWORD"
        val NATIVE_VARIABLES = listOf(PORT_VARIABLE, USER_VARIABLE, PASSWORD_VARIABLE)
    }
}
