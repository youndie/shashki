package io.github.youndie.shashki.server.billing

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
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
    /**
     * Which settlement owes it (B-44).
     *
     * **A tip is a second payout for the same ride**, so this is half the primary key. It is not the
     * settlement running twice — that still collides, which is the idempotence the table was built
     * for — it is a different settlement about the same ride.
     */
    val kind: String = FARE,
) {
    public companion object {
        public const val FARE: String = "FARE"
        public const val TIP: String = "TIP"
    }
}

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

    public fun remove(
        rideId: String,
        kind: String = Payout.FARE,
    )

    public fun find(
        rideId: String,
        kind: String = Payout.FARE,
    ): Payout?

    /** Everything owed for a ride — the fare's share and the tip, when there is one. */
    public fun forRide(rideId: String): List<Payout>

    /**
     * What this driver has been owed since [sinceEpochMs], in cents (B-46).
     *
     * **A sum of payout rows and not of fares.** The two agree until the first refund, and then the
     * recomputed figure is the driver's word against the bank's — a tip whose saga rolled back has
     * no row, and a screen adding up journeys would still be showing it.
     */
    public fun sumFor(
        driverId: String,
        sinceEpochMs: Long,
    ): Long

    /** How many fares — not tips — this driver has been paid for since [sinceEpochMs] (B-81). */
    public fun countFor(
        driverId: String,
        sinceEpochMs: Long,
    ): Int

    /** Everything this driver has been owed, by UTC day, newest first (B-81). */
    public fun daysFor(driverId: String): List<PayoutDay>
}

/** One day's payouts, as the repository groups them: the day's start, how many fares, the sum. */
public data class PayoutDay(
    val dayStartEpochMs: Long,
    val trips: Int,
    val amountCents: Long,
    val currency: String,
)

public object PayoutsTable : Table("payouts") {
    public val rideId: Column<String> = varchar("ride_id", 255)
    public val driverId: Column<String> = varchar("driver_id", 255)
    public val amountCents: Column<Long> = long("amount_cents")
    public val currency: Column<String> = varchar("currency", 3)
    public val createdAt: Column<Long> = long("created_at")
    public val kind: Column<String> = varchar("kind", 20)

    override val primaryKey: PrimaryKey = PrimaryKey(rideId, kind)
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
                it[kind] = payout.kind
            }
        }
    }

    override fun remove(
        rideId: String,
        kind: String,
    ) {
        transaction(database) {
            PayoutsTable.deleteWhere { (PayoutsTable.rideId eq rideId) and (PayoutsTable.kind eq kind) }
        }
    }

    override fun find(
        rideId: String,
        kind: String,
    ): Payout? =
        transaction(database) {
            PayoutsTable
                .selectAll()
                .where { (PayoutsTable.rideId eq rideId) and (PayoutsTable.kind eq kind) }
                .singleOrNull()
                ?.toPayout()
        }

    override fun sumFor(
        driverId: String,
        sinceEpochMs: Long,
    ): Long =
        transaction(database) {
            PayoutsTable
                .selectAll()
                .where { (PayoutsTable.driverId eq driverId) and (PayoutsTable.createdAt greaterEq sinceEpochMs) }
                .sumOf { it[PayoutsTable.amountCents] }
        }

    override fun forRide(rideId: String): List<Payout> =
        transaction(database) {
            PayoutsTable
                .selectAll()
                .where { PayoutsTable.rideId eq rideId }
                .map { it.toPayout() }
        }

    override fun countFor(
        driverId: String,
        sinceEpochMs: Long,
    ): Int =
        transaction(database) {
            PayoutsTable
                .selectAll()
                .where {
                    (PayoutsTable.driverId eq driverId) and
                        (PayoutsTable.createdAt greaterEq sinceEpochMs) and
                        (PayoutsTable.kind eq Payout.FARE)
                }.count()
                .toInt()
        }

    /**
     * Grouped here rather than in SQL: a driver's rows are a shift's worth a day, and a day boundary
     * in UTC is one integer division — the same boundary `sumFor`'s callers use.
     */
    override fun daysFor(driverId: String): List<PayoutDay> =
        transaction(database) {
            PayoutsTable
                .selectAll()
                .where { PayoutsTable.driverId eq driverId }
                .map {
                    Triple(
                        it[PayoutsTable.createdAt] / DAY_MS * DAY_MS,
                        it[PayoutsTable.kind],
                        it[PayoutsTable.amountCents] to it[PayoutsTable.currency],
                    )
                }.groupBy { it.first }
                .map { (day, rows) ->
                    PayoutDay(
                        dayStartEpochMs = day,
                        trips = rows.count { it.second == Payout.FARE },
                        amountCents = rows.sumOf { it.third.first },
                        currency = rows.first().third.second,
                    )
                }.sortedByDescending { it.dayStartEpochMs }
        }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}

private fun ResultRow.toPayout() =
    Payout(
        this[PayoutsTable.rideId],
        this[PayoutsTable.driverId],
        this[PayoutsTable.amountCents],
        this[PayoutsTable.currency],
        this[PayoutsTable.kind],
    )
