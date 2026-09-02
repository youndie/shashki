package io.github.youndie.shashki.server.feature.settlement

import io.github.youndie.shashki.protocol.DRIVER_POSITIONS_PATH
import io.github.youndie.shashki.protocol.DriverDecision
import io.github.youndie.shashki.protocol.DriverOffers
import io.github.youndie.shashki.protocol.DriverReport
import io.github.youndie.shashki.protocol.DriverRides
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.OfferAnswer
import io.github.youndie.shashki.protocol.Quotes
import io.github.youndie.shashki.protocol.QuotesView
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideRating
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.protocol.RideTip
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.protocol.RouteRequest
import io.github.youndie.shashki.protocol.TripAdvance
import io.github.youndie.shashki.server.billing.PaymentGateway
import io.github.youndie.shashki.server.billing.Payout
import io.github.youndie.shashki.server.billing.PayoutRepository
import io.github.youndie.shashki.server.dispatch.CandidateSource
import io.github.youndie.shashki.server.dispatch.DriverIndex
import io.github.youndie.shashki.server.dispatch.DriverReservations
import io.github.youndie.shashki.server.feature.settlement.domain.SettleRideUseCase
import io.github.youndie.shashki.server.feature.settlement.saga.Settled
import io.github.youndie.shashki.server.shashki
import io.github.youndie.shashki.server.testing.PostgresHarness
import io.github.youndie.shashki.server.testing.awaitTrue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get
import ru.workinprogress.petich.PetichClock
import ru.workinprogress.petich.PetichRepository
import ru.workinprogress.petich.PetichStatus
import ru.workinprogress.petich.SimpleEnrichedPayload
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The second saga, from the outside: a driver drives, a rider is charged, a driver is owed.
 *
 * **Through the routes rather than against the engine**, because the half that was missing was never
 * the saga — it was that nothing reached it. `PaymentGateway.capture` had been implemented since
 * B-11 and called by nobody, `SendReceiptUseCase` was written, tested against a real SMTP server and
 * bound in no module, and there was no way to move a ride past `ASSIGNED` at all. What these tests
 * assert is the joining up.
 *
 * `SettlementSagaTest` is the other half — the phases, the compensations and the death between them.
 */
class SettlementTest {
    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    /** B-37's first criterion, end to end. */
    @Test
    fun `a driver drives the trip to its end and the ride settles`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()
            val ride = assignedRide(client, app)

            // The card is holding the fare while the trip runs, which is the state the whole demo is
            // about — and the state that, until this item, lasted for ever.
            assertEquals(1, app.get<PaymentGateway>().activeHolds().size)

            for (state in listOf(RideStatus.ARRIVING, RideStatus.ARRIVED, RideStatus.IN_PROGRESS)) {
                assertEquals(state, client.advance(ride.id, DRIVER, state).status)
                assertEquals(1, app.get<PaymentGateway>().activeHolds().size, "$state moved money")
            }

            val finished = client.advance(ride.id, DRIVER, RideStatus.COMPLETED)

            assertEquals(RideStatus.COMPLETED, finished.status)
            // B-37's third criterion: nothing is left holding money.
            assertEquals(emptyList(), app.get<PaymentGateway>().activeHolds().toList())
            // And the money actually moved rather than merely stopping being held.
            val taken = app.get<PaymentGateway>().captured().single()
            assertEquals(assertNotNull(ride.quote).amountCents, taken.amountCents)
        }

    /** The driver is owed his share, and the share comes from the charge rather than from a guess. */
    @Test
    fun `the driver's payout is the fare minus the platform's cut`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()
            val ride = assignedRide(client, app)
            drive(client, ride.id)

            val payout = assertNotNull(app.get<PayoutRepository>().find(ride.id))
            val fare = assertNotNull(ride.quote).amountCents

            assertEquals(DRIVER, payout.driverId)
            assertEquals(fare * PLATFORM_REMAINDER / HUNDRED, payout.amountCents)
            assertEquals("USD", payout.currency)
        }

    /**
     * B-37's fourth criterion, at the level this configuration can honestly assert it.
     *
     * **The rider's address comes from the token and this stand has no provider**, so there is
     * nobody to send a receipt to — and the settlement records that rather than inventing a
     * recipient or falling over. That the mail itself goes is `SettlementSagaTest`'s subject, where
     * a payload with an address can be built directly.
     */
    @Test
    fun `with no provider the settlement completes and records that no receipt went`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()
            val ride = assignedRide(client, app)
            drive(client, ride.id)

            val settlement =
                assertNotNull(
                    app.get<PetichRepository>().findById(SettleRideUseCase.settlementId(ride.id)),
                    "the settlement saga left no row",
                )
            assertEquals(PetichStatus.COMPLETED, settlement.status)
            assertEquals("false", (settlement.enrichedPayload as SimpleEnrichedPayload).data[Settled.RECEIPT])
        }

    /**
     * B-37's fifth criterion. **Two mechanisms under one word, and the test is that they diverge.**
     *
     * Cancelling before a driver is assigned compensates the order saga: the hold is released, and
     * nobody is charged anything. Cancelling after settles a fee: money moves, and it is a quarter
     * of the fare rather than the fare.
     */
    @Test
    fun `cancelling before a driver releases the hold and cancelling after charges a fee`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()

            // Before: no driver online at all, so the saga is still waiting.
            val waiting = request(client, "rider-early")
            client.post(Rides.Cancel(id = waiting.id))
            assertEquals(RideStatus.CANCELLED, client.read(waiting.id).status)
            assertEquals(emptyList(), app.get<PaymentGateway>().captured().toList(), "a rider who waited was charged")
            assertEquals(emptyList(), app.get<PaymentGateway>().activeHolds().toList())

            // After: a driver took it, and the rider changed their mind anyway.
            val assigned = assignedRide(client, app)
            val fare = assertNotNull(assigned.quote).amountCents
            val cancelled = client.post(Rides.Cancel(id = assigned.id)).body<RideView>()

            assertEquals(RideStatus.CANCELLED, cancelled.status)
            val taken = app.get<PaymentGateway>().captured().single()
            assertEquals(
                fare * CANCELLATION_PERCENT / HUNDRED,
                taken.amountCents,
                "the fee is not a quarter of the fare",
            )
            assertTrue(taken.amountCents < fare, "a cancellation cost the whole fare")
            assertEquals(emptyList(), app.get<PaymentGateway>().activeHolds().toList())
        }

    /**
     * **B-42: a driver's second ride.** Found on the stand and not by any test here, because a test
     * drives one ride and a demo drives two.
     *
     * `OfferStep` reserves a candidate and keeps the reservation when the driver accepts — that is
     * what stops a second offer reaching somebody already carrying a passenger — and nothing gave it
     * back. A driver therefore took exactly one ride per process, and every order after that was
     * refused with "no cars nearby" the instant it was made.
     */
    @Test
    fun `a driver who finished a ride is offered the next one`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()

            val first = assignedRide(client, app)
            assertEquals(RideStatus.COMPLETED, drive(client, first.id).status)
            assertEquals(emptyMap(), app.get<DriverReservations>().all(), "the finished ride kept its driver")

            // The same driver, still where he was: the index holds a position for a minute and this
            // is the second ride, not a second shift.
            val second = assignedRide(client, app)

            assertEquals(RideStatus.ASSIGNED, second.status)
            assertEquals(DRIVER, second.driverId)
            assertTrue(second.id != first.id)
        }

    /**
     * The other end of a ride, and its own case: **a cancellation after a driver set off also frees
     * him**. The fee is charged either way — that is the test above — and what this adds is that the
     * driver is not left carrying a ride nobody is taking.
     */
    @Test
    fun `a ride cancelled after a driver set off frees the driver for the next one`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()

            val abandoned = assignedRide(client, app)
            assertEquals(RideStatus.CANCELLED, client.post(Rides.Cancel(id = abandoned.id)).body<RideView>().status)
            assertEquals(emptyMap(), app.get<DriverReservations>().all(), "the cancelled ride kept its driver")

            assertEquals(DRIVER, assignedRide(client, app).driverId)
        }

    /**
     * **The two answers about one word, compared** — the assertion that would have caught B-42
     * without a stand.
     *
     * "Available" was computed twice out of two different facts: `PickupEta` asked the index, which
     * knows geography, and the saga asked the index *and then reserved*, which is geography plus who
     * is busy. So a rider was shown `0 min` for a car that was carrying somebody else — and, while
     * the reservation leaked, went on being shown it for ever. One list now answers both.
     */
    @Test
    fun `a wait is only shown for a class the dispatch can actually serve`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                // The straight-line estimator, like every other test here: the fixture graph is an
                // L a kilometre wide and this test's dropoff is the airport, which is outside it.
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()

            // The positive control first: with the driver free, the wait exists. Without this the
            // assertion below would pass over a server that never names a wait at all.
            parkDriver(client, app)
            assertNotNull(client.economyWait(), "no wait at all, so the assertion below proves nothing")

            val ride = assignedRide(client, app)
            assertNull(client.economyWait(), "a wait for a car that is carrying somebody else")

            drive(client, ride.id)
            assertNotNull(client.economyWait(), "the driver finished and is still nobody's candidate")
        }

    /**
     * **What cancelling costs, on the wire** (B-43). R10 shows the amount before the button, and the
     * rule that produces it — a quarter of the fare once a driver has set off — is `Commission`'s. A
     * client that multiplied the fare itself would be a second copy of a pricing rule, so the server
     * answers with the number and this is the test that it is the same number the settlement takes.
     *
     * `0` and `null` are different answers: free to cancel, and too late to cancel.
     */
    @Test
    fun `the ride carries what cancelling it would cost right now`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()

            // Still asking: a driver is parked, so the saga is waiting for an answer rather than
            // running out of candidates. Nothing has been captured, so calling it off is free.
            parkDriver(client, app)
            val waiting = request(client, "rider-1")
            assertEquals(RideStatus.MATCHING, waiting.status)
            assertEquals(0, waiting.cancellationFeeCents, "waiting for a car is free to call off")

            val assigned =
                client
                    .post(DriverOffers.Answer(rideId = waiting.id)) {
                        contentType(ContentType.Application.Json)
                        setBody(OfferAnswer(DRIVER, DriverDecision.ACCEPT))
                    }.body<RideView>()
            val fare = assertNotNull(assigned.quote).amountCents
            assertEquals(fare * CANCELLATION_PERCENT / HUNDRED, assigned.cancellationFeeCents)

            client.advance(assigned.id, DRIVER, RideStatus.ARRIVING)
            assertEquals(
                fare * CANCELLATION_PERCENT / HUNDRED,
                client.read(assigned.id).cancellationFeeCents,
                "a car on its way is still cancellable, for the same fee",
            )

            client.advance(assigned.id, DRIVER, RideStatus.ARRIVED)
            client.advance(assigned.id, DRIVER, RideStatus.IN_PROGRESS)
            assertNull(
                client.read(assigned.id).cancellationFeeCents,
                "the rider is in the car; the fare is the fare and there is nothing to confirm",
            )
        }

/**
     * **R8, through the routes** (B-44): the rating and the tip, each refused while the ride is
     * still running, and each landing where it belongs afterwards.
     */
    @Test
    fun `rating and tipping are refused until the ride is over`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()
            val ride = assignedRide(client, app)

            assertEquals(HttpStatusCode.Conflict, client.rate(ride.id, stars = 5).status, "a ride in progress")
            assertEquals(HttpStatusCode.Conflict, client.tip(ride.id, TIP).status, "a ride in progress")

            drive(client, ride.id)

            val rated = client.rate(ride.id, stars = 5)
            assertEquals(HttpStatusCode.NoContent, rated.status, rated.bodyAsText())
            val tipped = client.tip(ride.id, TIP)
            assertEquals(HttpStatusCode.OK, tipped.status, tipped.bodyAsText())
        }

    /**
     * **The tip is money on top, and the driver keeps it all.** The fare's capture is what the ride
     * cost; the tip is a second charge with its own payout row, and the two are visible side by
     * side — which is the assertion that the tip did not quietly grow the capture.
     */
    @Test
    fun `a tip is a second charge with a payout row of its own`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()
            val ride = assignedRide(client, app)
            drive(client, ride.id)
            val fare = assertNotNull(ride.quote).amountCents

            client.tip(ride.id, TIP)

            val taken =
                app
                    .get<PaymentGateway>()
                    .captured()
                    .map { it.amountCents }
                    .sorted()
            assertEquals(listOf(TIP, fare).sorted(), taken, "the tip was not a charge of its own")

            val payouts = app.get<PayoutRepository>().forRide(ride.id).associateBy { it.kind }
            assertEquals(fare * PLATFORM_REMAINDER / HUNDRED, assertNotNull(payouts[Payout.FARE]).amountCents)
            assertEquals(TIP, assertNotNull(payouts[Payout.TIP]).amountCents, "the platform took a cut of a tip")
        }

    /**
     * **The rating becomes the number the candidate sort reads** (B-44) — the first time that key is
     * something other than every driver's default. What it does *not* do is reorder by rating alone:
     * distance is still first, and research §1.6d says why no coefficient was invented.
     */
    @Test
    fun `a rating a rider gave is the rating dispatch sees`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()
            val ride = assignedRide(client, app)
            drive(client, ride.id)

            // The socket reports 4.9 and the rider says three: the sort must read the rider's.
            client.rate(ride.id, stars = 3)

            val candidates = app.get<CandidateSource>().candidates(PICKUP, RideClass.ECONOMY)
            assertEquals(3.0, assertNotNull(candidates.firstOrNull { it.driverId == DRIVER }).rating)
        }

    /** The order is the point of the route: a driver cannot arrive at a ride they have not started. */
    @Test
    fun `a transition that is not the next one is refused`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()
            val ride = assignedRide(client, app)

            val skipped =
                client.post(DriverRides.Advance(rideId = ride.id)) {
                    contentType(ContentType.Application.Json)
                    setBody(TripAdvance(DRIVER, RideStatus.COMPLETED))
                }

            assertEquals(HttpStatusCode.Conflict, skipped.status)
            assertEquals(RideStatus.ASSIGNED, client.read(ride.id).status, "the refused transition moved the ride")
            assertEquals(1, app.get<PaymentGateway>().activeHolds().size, "a refused transition took money")
        }

    /** And it has to be the driver whose ride it is, checked against the driver the saga assigned. */
    @Test
    fun `a driver cannot advance somebody else's ride`() =
        testApplication {
            lateinit var app: Application
            application {
                app = this
                shashki(PostgresHarness.database)
            }
            val client = typedClient()
            startApplication()
            val ride = assignedRide(client, app)

            val stranger =
                client.post(DriverRides.Advance(rideId = ride.id)) {
                    contentType(ContentType.Application.Json)
                    setBody(TripAdvance("driver-9", RideStatus.ARRIVING))
                }

            // 404 rather than 403: confirming that somebody else's ride exists is itself an answer.
            assertEquals(HttpStatusCode.NotFound, stranger.status)
            assertEquals(RideStatus.ASSIGNED, client.read(ride.id).status)
        }

    private suspend fun HttpClient.advance(
        rideId: String,
        driverId: String,
        to: RideStatus,
    ): RideView =
        post(DriverRides.Advance(rideId = rideId)) {
            contentType(ContentType.Application.Json)
            setBody(TripAdvance(driverId, to))
        }.body()

    private suspend fun HttpClient.read(rideId: String): RideView = get(Rides.ById(id = rideId)).body()

    private suspend fun HttpClient.rate(
        rideId: String,
        stars: Int,
    ) = post(Rides.Rate(id = rideId)) {
        contentType(ContentType.Application.Json)
        setBody(RideRating(stars))
    }

    private suspend fun HttpClient.tip(
        rideId: String,
        amountCents: Long,
    ) = post(Rides.Tip(id = rideId)) {
        contentType(ContentType.Application.Json)
        setBody(RideTip(amountCents))
    }

    /** What the rider's class tile shows for economy: a wait in seconds, or nothing. */
    private suspend fun HttpClient.economyWait(): Int? =
        post(Quotes()) {
            contentType(ContentType.Application.Json)
            setBody(RouteRequest(PICKUP, DROPOFF))
        }.body<QuotesView>()
            .classes
            .first { it.rideClass == RideClass.ECONOMY }
            .pickupEtaSeconds

    /** The whole trip, in the order a driver actually taps them. */
    private suspend fun drive(
        client: HttpClient,
        rideId: String,
    ): RideView {
        for (state in listOf(RideStatus.ARRIVING, RideStatus.ARRIVED, RideStatus.IN_PROGRESS)) {
            client.advance(rideId, DRIVER, state)
        }
        return client.advance(rideId, DRIVER, RideStatus.COMPLETED)
    }

    private suspend fun request(
        client: HttpClient,
        riderId: String,
    ): RideView =
        client
            .post(Rides()) {
                contentType(ContentType.Application.Json)
                setBody(RideRequest(riderId, PICKUP, DROPOFF, RideClass.ECONOMY, "card-4417"))
            }.body()

    /** A ride with a driver on it: park one, ask for a car, accept the offer. */
    private suspend fun assignedRide(
        client: HttpClient,
        app: Application,
    ): RideView {
        parkDriver(client, app)
        val ride = request(client, "rider-1")
        val answered =
            client.post(DriverOffers.Answer(rideId = ride.id)) {
                contentType(ContentType.Application.Json)
                setBody(OfferAnswer(DRIVER, DriverDecision.ACCEPT))
            }
        assertEquals(HttpStatusCode.OK, answered.status)
        return answered.body()
    }

    private suspend fun parkDriver(
        client: HttpClient,
        app: Application,
    ) {
        val session = client.webSocketSession(DRIVER_POSITIONS_PATH)
        val at = GeoPoint(PICKUP.lat + 50 / 111_320.0, PICKUP.lon)
        session.send(
            Frame.Text(
                Json.encodeToString(DriverReport.serializer(), DriverReport(DRIVER, RideClass.ECONOMY, 4.9, at)),
            ),
        )
        val index = app.get<DriverIndex>()
        val clock = app.get<PetichClock>()
        awaitTrue("$DRIVER reaches the index") {
            index.near(PICKUP, RideClass.ECONOMY, clock.nowEpochMs()).any { it.driverId == DRIVER }
        }
    }

    private fun ApplicationTestBuilder.typedClient(): HttpClient =
        createClient {
            install(Resources)
            install(WebSockets)
            install(ContentNegotiation) { json() }
        }

    private companion object {
        const val DRIVER = "driver-1"
        val PICKUP = GeoPoint(46.0511, 14.5051)
        val DROPOFF = GeoPoint(46.2237, 14.4576)

        /** `Commission.DEFAULT`, restated so the test fails when somebody changes the split. */
        const val PLATFORM_REMAINDER = 80L
        const val CANCELLATION_PERCENT = 25L

        /** The kit's middle tip button. */
        const val TIP = 500L
        const val HUNDRED = 100L
    }
}
