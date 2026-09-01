package io.github.youndie.shashki.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

/** Where the database is. From the environment, and the server does not start without it. */
public data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maximumPoolSize: Int = 10,
) {
    public companion object {
        public fun fromEnv(): DatabaseConfig =
            DatabaseConfig(
                url = requireEnv("DB_URL"),
                user = requireEnv("DB_USER"),
                password = requireEnv("DB_PASSWORD"),
                maximumPoolSize = System.getenv("DB_POOL_SIZE")?.toIntOrNull() ?: 10,
            )

        private fun requireEnv(name: String): String =
            System.getenv(name) ?: error("$name is not set — the server cannot start without a database")
    }
}

/**
 * The pool, the migrations and the Exposed handle, in that order and nowhere else.
 *
 * Flyway rather than `SchemaUtils.create`: the latter is a shortcut that works until the first
 * column changes, and then it is a migration strategy nobody chose. `validateOnMigrate` so a script
 * edited after it ran fails the start rather than silently diverging from every other database.
 */
public object DatabaseFactory {
    public fun dataSource(config: DatabaseConfig): DataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = config.maximumPoolSize
                initializationFailTimeout = 10_000
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
            },
        )

    public fun migrate(dataSource: DataSource): Int =
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .validateOnMigrate(true)
            .cleanDisabled(true)
            .load()
            .migrate()
            .migrationsExecuted

    public fun connect(dataSource: DataSource): Database = Database.connect(dataSource)
}
