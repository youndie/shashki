package io.github.youndie.shashki.server.feature.rating.data

import io.github.youndie.shashki.server.feature.rating.domain.Rating
import io.github.youndie.shashki.server.feature.rating.domain.RatingRepository
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

public object RatingsTable : Table("ratings") {
    public val rideId: Column<String> = varchar("ride_id", 255)
    public val driverId: Column<String> = varchar("driver_id", 255)
    public val stars: Column<Int> = integer("stars")
    public val createdAt: Column<Long> = long("created_at")

    override val primaryKey: PrimaryKey = PrimaryKey(rideId)
}

/**
 * The ratings table, and an average computed in Kotlin.
 *
 * **`avg()` in SQL would be the better answer and is not the one here**: Exposed's aggregate needs a
 * column expression and a result mapping for a nullable numeric, and a driver in this product has a
 * handful of rides. What must not happen is the average being computed somewhere a second time — so
 * it is computed here, once, behind the port.
 */
public class ExposedRatingRepository(
    private val database: Database,
    @Suppress(
        "ktlint:kapkan:wall-clock",
        "the default of an injectable clock; the port is the parameter this initialises",
    )
    private val now: () -> Long = { System.currentTimeMillis() },
) : RatingRepository {
    override fun record(rating: Rating) {
        transaction(database) {
            RatingsTable.insert {
                it[rideId] = rating.rideId
                it[driverId] = rating.driverId
                it[stars] = rating.stars
                it[createdAt] = now()
            }
        }
    }

    override fun find(rideId: String): Rating? =
        transaction(database) {
            RatingsTable
                .selectAll()
                .where { RatingsTable.rideId eq rideId }
                .singleOrNull()
                ?.let { Rating(it[RatingsTable.rideId], it[RatingsTable.driverId], it[RatingsTable.stars]) }
        }

    override fun averageFor(driverId: String): Double? =
        transaction(database) {
            RatingsTable
                .selectAll()
                .where { RatingsTable.driverId eq driverId }
                .map { it[RatingsTable.stars] }
                .takeIf { it.isNotEmpty() }
                ?.average()
        }
}
