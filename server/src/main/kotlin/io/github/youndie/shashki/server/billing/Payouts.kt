package io.github.youndie.shashki.server.billing

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** What a driver is owed for one ride. */
public data class Payout(
    val rideId: String,
    val driverId: String,
    val amountCents: Long,
    val currency: String,
)

/**
 * The payout ledger, such as it is.
 *
 * **A row and not a transfer**, because a payout in a real system is a batch somebody runs against a
 * bank and this product is not going to pretend otherwise. What it does model honestly is that the
 * settlement saga's EXECUTION step has something to undo: the row is written there and removed by
 * its compensation, which is what makes the phase a phase rather than a name.
 *
 * One row per ride, so the primary key is the idempotence: a settlement that somehow ran twice
 * collides instead of paying twice.
 */
public interface PayoutRepository {
    public fun record(payout: Payout)

    public fun remove(rideId: String)

    public fun find(rideId: String): Payout?
}

public object PayoutsTable : Table("payouts") {
    public val rideId: Column<String> = varchar("ride_id", 255)
    public val driverId: Column<String> = varchar("driver_id", 255)
    public val amountCents: Column<Long> = long("amount_cents")
    public val currency: Column<String> = varchar("currency", 3)
    public val createdAt: Column<Long> = long("created_at")

    override val primaryKey: PrimaryKey = PrimaryKey(rideId)
}

public class ExposedPayoutRepository(
    private val database: Database,
    private val now: () -> Long = { System.currentTimeMillis() },
) : PayoutRepository {
    override fun record(payout: Payout) {
        transaction(database) {
            PayoutsTable.insert {
                it[rideId] = payout.rideId
                it[driverId] = payout.driverId
                it[amountCents] = payout.amountCents
                it[currency] = payout.currency
                it[createdAt] = now()
            }
        }
    }

    override fun remove(rideId: String) {
        transaction(database) { PayoutsTable.deleteWhere { PayoutsTable.rideId eq rideId } }
    }

    override fun find(rideId: String): Payout? =
        transaction(database) {
            PayoutsTable
                .selectAll()
                .where { PayoutsTable.rideId eq rideId }
                .singleOrNull()
                ?.let {
                    Payout(
                        it[PayoutsTable.rideId],
                        it[PayoutsTable.driverId],
                        it[PayoutsTable.amountCents],
                        it[PayoutsTable.currency],
                    )
                }
        }
}
