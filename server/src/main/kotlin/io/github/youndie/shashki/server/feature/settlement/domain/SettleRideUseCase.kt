package io.github.youndie.shashki.server.feature.settlement.domain

import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.server.common.UseCase
import io.github.youndie.shashki.server.common.suspendRunCatching
import io.github.youndie.shashki.server.feature.ride.saga.Enriched
import io.github.youndie.shashki.server.feature.ride.saga.OrderPayload
import io.github.youndie.shashki.server.feature.settlement.saga.SETTLEMENT_SAGA_TYPE
import io.github.youndie.shashki.server.feature.settlement.saga.SettlementPayload
import ru.workinprogress.petich.Petich
import ru.workinprogress.petich.PetichEngine
import ru.workinprogress.petich.PetichRepository
import ru.workinprogress.petich.PetichResult
import ru.workinprogress.petich.PetichStatus
import ru.workinprogress.petich.SimpleEnrichedPayload

/**
 * Start the settlement for a ride, and run it as far as it goes in one pass.
 *
 * **Everything it needs is already on the order saga's row**, which is why this reads that row once
 * and copies rather than passing a dozen arguments down from a route: the hold, the quote, the
 * driver and the rider were all established by the saga that took them, and a settlement assembled
 * from a request body would be a settlement a caller could lie to.
 *
 * **The settlement's id is not the ride's.** petich keys a saga by its row id, and the order saga
 * already occupies `rideId`; a settlement written under the same key would overwrite the record of
 * how the ride was assigned. `<rideId>:settlement` keeps both, and makes the settlement's row
 * findable from the ride without an index.
 */
public class SettleRideUseCase(
    private val engine: PetichEngine,
    private val sagas: PetichRepository,
) : UseCase<SettleRideUseCase.Params, Unit> {
    override suspend fun invoke(params: Params): Result<Unit> =
        suspendRunCatching {
            val order = sagas.findById(params.rideId) ?: throw NothingToSettleException(params.rideId, "no ride")
            val payload = order.payload as? OrderPayload ?: throw NothingToSettleException(params.rideId, "not a ride")
            val data = (order.enrichedPayload as? SimpleEnrichedPayload)?.data.orEmpty()

            val hold = data[Enriched.HOLD_ID] ?: throw NothingToSettleException(params.rideId, "no hold was taken")
            val driver = data[Enriched.DRIVER_ID] ?: throw NothingToSettleException(params.rideId, "no driver")
            val quote = data.quote() ?: throw NothingToSettleException(params.rideId, "no quote")

            val settlement =
                SettlementPayload(
                    rideId = params.rideId,
                    riderId = payload.riderId,
                    driverId = driver,
                    holdId = hold,
                    quote = quote,
                    rideClass = payload.rideClass,
                    kind = params.kind,
                    riderEmail = payload.riderEmail,
                    pickup = payload.pickup.asText(),
                    dropoff = payload.dropoff.asText(),
                )
            val id = settlementId(params.rideId)
            when (
                val result =
                    engine.process(
                        Petich(id = id, type = SETTLEMENT_SAGA_TYPE, status = PetichStatus.DRAFT, payload = settlement),
                    )
            ) {
                is PetichResult.Success, is PetichResult.ActionRequired, is PetichResult.Error -> Unit
                is PetichResult.SystemFailure -> error("settlement $id failed systemically: ${result.details}")
            }
        }

    public class Params(
        public val rideId: String,
        public val kind: SettlementPayload.Kind,
    )

    private fun Map<String, String>.quote(): Quote? {
        val distance = this[Enriched.QUOTE_DISTANCE]?.toIntOrNull() ?: return null
        val duration = this[Enriched.QUOTE_DURATION]?.toIntOrNull() ?: return null
        val amount = this[Enriched.QUOTE_AMOUNT]?.toLongOrNull() ?: return null
        val currency = this[Enriched.QUOTE_CURRENCY] ?: return null
        return Quote(distance, duration, amount, currency)
    }

    /**
     * Coordinates as words, because a receipt has to say where somebody went and nothing in this
     * product geocodes. Four decimals is about eleven metres — the client draws them the same way
     * and for the same reason (B-30).
     */
    private fun io.github.youndie.shashki.protocol.GeoPoint.asText(): String =
        "${(lat * PLACES).toLong() / PLACES}, ${(lon * PLACES).toLong() / PLACES}"

    public companion object {
        /** The settlement's row id for a ride. One string, in one place, used by both sides. */
        public fun settlementId(rideId: String): String = "$rideId:settlement"

        private const val PLACES = 10_000.0
    }
}

/** Asked to settle something that cannot be settled. A 409 rather than a 500. */
public class NothingToSettleException(
    public val rideId: String,
    public val why: String,
) : RuntimeException("ride $rideId cannot be settled: $why")
