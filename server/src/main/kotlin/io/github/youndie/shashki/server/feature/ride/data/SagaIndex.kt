package io.github.youndie.shashki.server.feature.ride.data

import io.github.youndie.shashki.server.feature.ride.saga.ORDER_SAGA_TYPE
import io.github.youndie.shashki.server.feature.ride.saga.SagaStorage
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Which rides exist, by id (B-45).
 *
 * **The ids and nothing else, on purpose.** petich owns the `petiches` table and its decoding; a
 * query here that read the `payload` column and parsed it would be a second implementation of how a
 * saga is stored, drifting the first time petich changes it. So this asks for the keys and the ride
 * repository asks petich for each row — an N+1 that is honest about being one, and that a rider with
 * a handful of rides pays nothing for.
 *
 * The day a rider has hundreds, the answer is a projection built from the broker's topic — which
 * B-38 already publishes to and B-45's own note calls the seam this screen exists to show.
 */
public class SagaIndex(
    private val database: Database,
    private val storage: SagaStorage,
) {
    public fun rideIds(): List<String> =
        transaction(database) {
            storage.petichTable
                .selectAll()
                .where { storage.petichTable.type eq ORDER_SAGA_TYPE }
                .map { it[storage.petichTable.id] }
        }
}
