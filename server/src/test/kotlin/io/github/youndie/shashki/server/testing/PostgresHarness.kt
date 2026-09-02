package io.github.youndie.shashki.server.testing

import io.github.youndie.shashki.server.db.DatabaseConfig
import io.github.youndie.shashki.server.db.DatabaseFactory
import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

/**
 * One Postgres for the whole test run, migrated once. Tests that write share it and truncate
 * between themselves — a container per test class is a minute of startup per class, and the
 * schema is the same in every one.
 */
object PostgresHarness {
    private const val IMAGE = "postgres:18-alpine"

    private val container: PostgreSQLContainer<Nothing> =
        PostgreSQLContainer<Nothing>(DockerImageName.parse(IMAGE)).apply {
            withDatabaseName("shashki")
            withUsername("shashki")
            withPassword("shashki")
            start()
        }

    val dataSource: DataSource by lazy {
        DatabaseFactory
            .dataSource(DatabaseConfig(container.jdbcUrl, container.username, container.password, maximumPoolSize = 4))
            .also { source ->
                val applied = DatabaseFactory.migrate(source)
                check(applied > 0) { "Flyway applied no migrations — is db/migration on the classpath?" }
            }
    }

    val database: Database by lazy { DatabaseFactory.connect(dataSource) }

    fun truncateAll() {
        dataSource.connection.use { connection ->
            // **Every table the server writes, not the two it used to have.** A payout left behind
            // by one test made the next one fail on a primary key and report it as a systemic saga
            // failure — a message about petich for a fact about the fixture (B-37).
            connection.createStatement().use {
                it.execute("TRUNCATE TABLE petiches, outbox_events, trips, payouts")
            }
            connection.commit()
        }
    }
}
