package io.github.youndie.shashki.server.db

import io.github.youndie.shashki.server.feature.ride.saga.sagaJson
import io.github.youndie.shashki.server.testing.PostgresHarness
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import ru.workinprogress.petich.postgres.OutboxEventsTable
import ru.workinprogress.petich.postgres.PetichTable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * V1 is hand-written and the tables are petich's; this is the only thing that says they agree.
 * A column type wrong in V1 otherwise surfaces as a saga that cannot be written, in whichever
 * request runs one first.
 */
class SchemaTest {
    private val tables: List<Table> = listOf(PetichTable(sagaJson()), OutboxEventsTable())

    @Test
    fun `the migrated schema needs no further DDL for petich's tables`() {
        val required =
            transaction(PostgresHarness.database) {
                MigrationUtils.statementsRequiredForDatabaseMigration(*tables.toTypedArray())
            }
        assertEquals(
            emptyList(),
            required,
            "V1 does not match the Exposed tables; still required:\n" + required.joinToString("\n"),
        )
    }

    @Test
    fun `the schema test is looking at something`() {
        // The guard on the guard: an empty list is also what an empty table list produces.
        assertEquals(2, tables.size)
    }
}
