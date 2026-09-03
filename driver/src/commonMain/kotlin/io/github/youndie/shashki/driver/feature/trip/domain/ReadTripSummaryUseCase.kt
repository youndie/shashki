package io.github.youndie.shashki.driver.feature.trip.domain

import io.github.youndie.shashki.driver.DriverIdentity
import io.github.youndie.shashki.driver.UseCase
import io.github.youndie.shashki.driver.suspendRunCatching
import io.github.youndie.shashki.protocol.TripSummaryView

/**
 * D5's numbers (B-70). A read and not a loop: the payout is written once, when the trip ends.
 *
 * The failure here is a state the screen has — the settlement is a saga and may be a moment behind
 * the `COMPLETED` that opened this screen — so the screen asks again rather than giving up.
 */
public class ReadTripSummaryUseCase(
    private val trips: TripRepository,
    private val identity: DriverIdentity,
) : UseCase<String, TripSummaryView> {
    override suspend fun invoke(params: String): Result<TripSummaryView> =
        suspendRunCatching { trips.summary(params, identity.current()) }
}
