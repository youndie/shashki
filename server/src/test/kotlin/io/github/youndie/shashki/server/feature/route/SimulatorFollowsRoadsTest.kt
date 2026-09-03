package io.github.youndie.shashki.server.feature.route

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.baseModule
import io.github.youndie.shashki.server.common.haversineMetres
import io.github.youndie.shashki.server.dispatch.DriverIndex
import io.github.youndie.shashki.server.dispatch.DriverSimulator
import io.github.youndie.shashki.server.dispatch.GridDriverIndex
import io.github.youndie.shashki.server.dispatch.SimulatorConfig
import io.github.youndie.shashki.server.dispatch.driverPositionRoutes
import io.github.youndie.shashki.server.feature.driver.domain.Driver
import io.github.youndie.shashki.server.feature.driver.domain.DriverRepository
import io.github.youndie.shashki.server.pricing.RouteEstimator
import io.github.youndie.shashki.server.pricing.StraightLineRouteEstimator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.ext.get
import ru.workinprogress.petich.PetichClock
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * **The simulated cars are on the streets, and the control is what proves it.**
 *
 * "The driver moved" is what the broken version also does, and "the driver is near the road" is
 * true of a straight line too when the road happens to run that way. So the same run is made twice
 * over the same fixture — once with the graph bound and once with the straight-line stand-in — and
 * the claim is relative: driving by road stays on the L, driving by line cuts its corner.
 */
class SimulatorFollowsRoadsTest {
    @Test
    fun `a simulated driver keeps to the road, and does not when the router is the stand-in`() {
        val byRoad = maximumDistanceFromTheRoad(FixtureGraph.estimator)
        val byLine = maximumDistanceFromTheRoad(StraightLineRouteEstimator())

        assertTrue(
            byRoad < ON_ROAD_METRES,
            "a car by road wandered $byRoad m from it — the geometry is not being followed",
        )
        assertTrue(
            byLine > byRoad * 3,
            "the control did not cut the corner: $byLine m by line against $byRoad m by road, " +
                "so this run would have passed with the routing removed",
        )
    }

    /** Runs one driver for a while and reports how far from the L it ever got. */
    @Suppress(
        "ktlint:kapkan:wall-clock",
        "the saga's clock in a test that asserts on distance from the road, never on time",
    )
    private fun maximumDistanceFromTheRoad(estimator: RouteEstimator): Double {
        val seen = mutableListOf<GeoPoint>()
        testApplication {
            lateinit var app: Application
            application {
                app = this
                baseModule(
                    listOf(
                        module {
                            single<RouteEstimator> { estimator }
                            // `driverPositionRoutes` resolves these two as well; without them the
                            // socket handler throws, the session closes, and the simulator reports
                            // nothing — which reads as "the driver never drove".
                            single<PetichClock> { PetichClock { System.currentTimeMillis() } }
                            single<Json> { Json }
                            // Every position the driver reports passes through here on its way to
                            // the index, which is the only place in the server that sees them all.
                            single<DriverIndex> { RecordingDriverIndex(GridDriverIndex(), seen) }
                            // **Everybody exists here** (B-63). This suite is about whether a
                            // simulated car keeps to the road; the records that decide a driver's
                            // class are `MatchingTest`'s subject and a table this module has not
                            // got.
                            single<DriverRepository> {
                                DriverRepository { id -> Driver(id, id, "—", "—", RideClass.ECONOMY) }
                            }
                        },
                    ),
                )
                routing {
                    routeRoutes()
                    driverPositionRoutes()
                }
            }
            val client =
                createClient {
                    install(ContentNegotiation) { json() }
                    install(Resources)
                    install(WebSockets)
                }
            startApplication()

            val jobs =
                DriverSimulator(
                    client,
                    SimulatorConfig(
                        drivers = 1,
                        // **Inside the graph's bounding box, and that is not a detail.**
                        // GraphHopper refuses a point outside the box before it tries to snap it —
                        // "Point 0 is out of bounds" — so a driver wandering off the fixture's
                        // extent gets no route at all and falls back to straight lines. Every
                        // position would then be a straight line and this test would pass its own
                        // control. The box is lat 46.05..46.07, lon 14.50..14.52; this stays well
                        // inside it.
                        centre = INSIDE_THE_BOX,
                        radiusMetres = 400.0,
                        reportInterval = 60.milliseconds,
                        // A simulator, not a physics engine. At the demo's 8 m/s a car covers half a
                        // metre per report and this test would measure where it started. At 400 m/s
                        // one report is 24 m, so the drive to the nearest street costs a third of
                        // the run and the rest of it is spent on the road, which is what is measured.
                        speedMetresPerSecond = 400.0,
                        pollInterval = 10.seconds,
                    ),
                ).start(app)
            try {
                delay(RUN_FOR)
            } finally {
                jobs.forEach { it.cancel() }
            }
        }

        // **The same window for both runs, and it starts late on purpose.** The driver appears at a
        // random point up to [radiusMetres] from the centre, which is off the road by construction —
        // it has not asked for a way anywhere yet, and its first leg is the drive to the nearest
        // street. Measuring that approach would measure the random seed. Measuring only "when it
        // arrived" would be worse: the control never arrives, so the window has to be chosen by the
        // clock rather than by the thing under test.
        assertTrue(seen.size > MINIMUM_REPORTS, "only ${seen.size} positions reported; the driver never drove")
        val window = seen.drop(seen.size / 2)
        return window.fold(0.0) { worst, at -> max(worst, distanceToTheL(at)) }
    }

    private companion object {
        val WEST = GeoPoint(46.0500, 14.5000)
        val CORNER = GeoPoint(46.0500, 14.5200)
        val NORTH = GeoPoint(46.0700, 14.5200)

        /** The fixture's two ways, as the polyline every position is measured against. */
        val THE_L = listOf(WEST, CORNER, NORTH)

        /** The middle of the fixture's bounding box, so a 400 m wander stays inside it. */
        val INSIDE_THE_BOX = GeoPoint(46.0600, 14.5100)

        const val ON_ROAD_METRES = 60.0
        const val MINIMUM_REPORTS = 10
        val RUN_FOR = 6.seconds

        fun distanceToTheL(point: GeoPoint): Double = THE_L.zipWithNext { a, b -> distanceToSegment(point, a, b) }.min()

        /**
         * Point to segment, in metres, on a local flat projection — over 2 km of one city the
         * curvature is far below the tolerance this is compared against, and a spherical version
         * would be a second implementation of `haversineMetres` to keep in step.
         */
        fun distanceToSegment(
            point: GeoPoint,
            from: GeoPoint,
            to: GeoPoint,
        ): Double {
            val ax = 0.0
            val ay = 0.0
            val bx = eastMetres(from, to)
            val by = northMetres(from, to)
            val px = eastMetres(from, point)
            val py = northMetres(from, point)
            val lengthSquared = bx * bx + by * by
            val t =
                if (lengthSquared ==
                    0.0
                ) {
                    0.0
                } else {
                    (((px - ax) * bx + (py - ay) * by) / lengthSquared).coerceIn(0.0, 1.0)
                }
            val dx = px - bx * t
            val dy = py - by * t
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }

        fun northMetres(
            from: GeoPoint,
            to: GeoPoint,
        ): Double = haversineMetres(from, GeoPoint(to.lat, from.lon)) * if (to.lat < from.lat) -1 else 1

        fun eastMetres(
            from: GeoPoint,
            to: GeoPoint,
        ): Double = haversineMetres(from, GeoPoint(from.lat, to.lon)) * if (to.lon < from.lon) -1 else 1
    }
}
