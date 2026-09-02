package io.github.youndie.shashki.server.feature.rating.domain

/** What one rider thought of one ride. */
public data class Rating(
    val rideId: String,
    val driverId: String,
    val stars: Int,
)

/**
 * Where ratings live (B-44).
 *
 * **Written down, unlike the positions.** The geo-index is a cache of where cars are and is rebuilt
 * from the socket in a minute; a rating is the only number the candidate sort has that is not
 * geometry, and one that vanished on a restart would be a sort key that lies. One row per ride:
 * a rider rates a ride once, and the primary key is what says so.
 */
public interface RatingRepository {
    public fun record(rating: Rating)

    public fun find(rideId: String): Rating?

    /**
     * The driver's number, or `null` for a driver nobody has rated.
     *
     * `null` rather than a flattering default: "not rated yet" and "rated three" are different
     * facts, and a default of five is the kind of lie that makes a sort key useless.
     */
    public fun averageFor(driverId: String): Double?
}
