package io.github.youndie.shashki.server.feature.settlement.saga

import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.workinprogress.petich.PetichPayload

/**
 * What the settlement saga starts with: everything the order saga already established, copied.
 *
 * **Copied and not looked up**, and the reason is the same one that makes a saga a saga. Reading the
 * order saga's row at every phase would make this saga's behaviour depend on a row somebody else may
 * still be writing — a rider cancelling, a sweeper rolling back. What is owed was decided at the
 * moment the trip ended, and a settlement that changed its mind halfway is not one.
 *
 * `@SerialName` is the polymorphic discriminator in the `payload` column. Moving this file without
 * it would make every settlement on disk unreadable — `OrderPayload` carries the same note.
 */
@Serializable
@SerialName("settlement")
public class SettlementPayload(
    public val rideId: String,
    public val riderId: String,
    public val driverId: String,
    public val holdId: String,
    public val quote: Quote,
    public val rideClass: RideClass,
    public val kind: Kind,
    /** Where the receipt goes, when anybody knows. `null` is a running configuration — see the step. */
    public val riderEmail: String? = null,
    public val pickup: String,
    public val dropoff: String,
) : PetichPayload() {
    /**
     * Which of the two settlements this is.
     *
     * **Research §1.4c's "same word, two mechanisms", as a field.** Before `ASSIGNED` a cancellation
     * is the order saga compensating from the middle — the hold released, the driver freed, nobody
     * charged. After it, the trip ended early and somebody is owed something anyway: that is this
     * saga with [FEE] instead of [FARE], and it is the same five phases.
     */
    public enum class Kind { FARE, FEE }
}

/** Keys into the settlement's enriched payload — what each step leaves for the ones after it. */
public object Settled {
    public const val CHARGE_AMOUNT: String = "charge.amountCents"
    public const val PAYOUT_AMOUNT: String = "payout.amountCents"
    public const val CURRENCY: String = "charge.currency"
    public const val RECEIPT: String = "receipt.sent"
}

/** The saga's `type` column, and the only string that names it. */
public const val SETTLEMENT_SAGA_TYPE: String = "settlement"
