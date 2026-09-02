package io.github.youndie.shashki.protocol

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

/**
 * What a driver's app sends up the position socket, once a few seconds while online.
 *
 * **`rideClass` and `rating` are self-reported, and that is a seam rather than a design.** There is
 * no driver record yet, so the only place these can come from is the message; a real system reads
 * them server-side from the driver's row and ignores whatever the socket claims. The fields stay
 * where they are when that lands — what changes is who fills them, and the socket stops being
 * believed. Named here because a self-reported rating is the kind of hole that is obvious in a
 * comment and invisible in a schema.
 */
@Serializable
public data class DriverReport(
    val driverId: String,
    val rideClass: RideClass,
    val rating: Double,
    val at: GeoPoint,
)

/** Where the driver's app sends [DriverReport]s. A path, so the client does not spell it either. */
public const val DRIVER_POSITIONS_PATH: String = "/api/driver/positions"

/**
 * The query parameter the socket carries its ticket in (B-52).
 *
 * **A browser cannot put a header on a WebSocket**, so the upgrade cannot present a bearer token the
 * way every other protected call does. The ticket is minted at [DriverTickets] behind the ordinary
 * token check, lives thirty seconds and dies on redemption — a value in a URL reaches access logs
 * and history, and that is the whole reason it is worth so little.
 */
public const val DRIVER_TICKET_QUERY: String = "ticket"

/** What `POST /api/driver/ticket` answers: one use, and not for long. */
@Serializable
public data class DriverTicket(
    val value: String,
    val expiresInSeconds: Int,
)

/**
 * What the driver has earned, in three periods (B-46).
 *
 * **Sums of payout rows, not of fares.** The driver's number is what was written down as owed; a
 * figure recomputed from journeys agrees with it until the first refund and then argues with the
 * bank.
 *
 * The day and the week are the server's, in UTC — see the route, which says what that costs a driver
 * in another timezone.
 */
@Serializable
public data class EarningsView(
    val todayCents: Long,
    val weekCents: Long,
    val allTimeCents: Long,
    val currency: String,
)

/**
 * `GET /api/driver/earnings` — D6's three tiles.
 *
 * [driverId] is the same seam every other driver route carries (B-52): **ignored the moment there is
 * a token**, and the only source there is on a server with no provider configured, which is the demo.
 */
@Resource("/api/driver/earnings")
public class DriverEarnings(
    public val driverId: String? = null,
)

/** `POST /api/driver/ticket` — the driver's token, exchanged for something a socket can carry. */
@Resource("/api/driver/ticket")
public class DriverTickets
