package io.github.youndie.shashki.server.feature.trip.data

import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.server.feature.trip.domain.Trip
import io.github.youndie.shashki.server.feature.trip.domain.TripRepository
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

public object TripsTable : Table("trips") {
    public val rideId: Column<String> = varchar("ride_id", 255)
    public val driverId: Column<String> = varchar("driver_id", 255)
    public val status: Column<String> = varchar("status", 50)
    public val updatedAt: Column<Long> = long("updated_at")

    override val primaryKey: PrimaryKey = PrimaryKey(rideId)
}

/**
 * One row per ride, written by the driver's own transitions.
 *
 * Blocking, like every other Exposed access in this server: the ORM is, and a `suspend` signature
 * over a blocking implementation is a promise the code does not keep.
 */
public class ExposedTripRepository(
    private val database: Database,
    private val now: () -> Long,
) : TripRepository {
    override fun find(rideId: String): Trip? =
        transaction(database) {
            TripsTable
                .selectAll()
                .where { TripsTable.rideId eq rideId }
                .singleOrNull()
                ?.let {
                    Trip(
                        it[TripsTable.rideId],
                        it[TripsTable.driverId],
                        RideStatus.valueOf(it[TripsTable.status]),
                    )
                }
        }

    override fun advance(trip: Trip) {
        transaction(database) {
            val updated =
                TripsTable.update({ TripsTable.rideId eq trip.rideId }) {
                    it[status] = trip.status.name
                    it[updatedAt] = now()
                }
            if (updated == 0) {
                TripsTable.insert {
                    it[rideId] = trip.rideId
                    it[driverId] = trip.driverId
                    it[status] = trip.status.name
                    it[updatedAt] = now()
                }
            }
        }
    }
}
