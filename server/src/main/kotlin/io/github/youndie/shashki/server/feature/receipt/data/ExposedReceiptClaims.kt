package io.github.youndie.shashki.server.feature.receipt.data

import io.github.youndie.petich.PetichClock
import io.github.youndie.shashki.server.feature.receipt.domain.ReceiptClaims
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** The claim ledger's one table — see [ReceiptClaims] for why it is written before the mail. */
public object ReceiptClaimsTable : Table("receipt_claims") {
    public val claimKey: org.jetbrains.exposed.v1.core.Column<String> = varchar("claim_key", 255)
    public val rideId: org.jetbrains.exposed.v1.core.Column<String> = varchar("ride_id", 255)
    public val claimedAt: org.jetbrains.exposed.v1.core.Column<Long> = long("claimed_at")

    override val primaryKey: PrimaryKey = PrimaryKey(claimKey)

    // DECLARED HERE AS WELL AS IN THE MIGRATION, because `SchemaTest` compares the two and an index
    // that lives only in the database is a rule the application cannot see — the same reason V3 had
    // to drop a default it had just added. It is what a prune of old claims would read.
    init {
        index("receipt_claims_claimed_at", false, claimedAt)
    }
}

/**
 * **The primary key is the arbiter, not a read-then-write.**
 *
 * Asking "is it claimed" and then claiming it is two statements and a race: two instances re-running
 * one settlement both read nothing and both send. `insertIgnore` makes the database decide, and the
 * row count is the answer — the same shape `payouts` uses to make a settlement that ran twice
 * collide instead of paying twice.
 */
public class ExposedReceiptClaims(
    private val database: Database,
    private val clock: PetichClock,
) : ReceiptClaims {
    override suspend fun claim(
        key: String,
        rideId: String,
    ): Boolean =
        transaction(database) {
            ReceiptClaimsTable
                .insertIgnore {
                    it[claimKey] = key
                    it[ReceiptClaimsTable.rideId] = rideId
                    it[claimedAt] = clock.nowEpochMs()
                }.insertedCount > 0
        }
}
