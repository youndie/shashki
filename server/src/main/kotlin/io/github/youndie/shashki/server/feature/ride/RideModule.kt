package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.server.billing.ExposedPayoutRepository
import io.github.youndie.shashki.server.billing.InMemoryPaymentGateway
import io.github.youndie.shashki.server.billing.PaymentGateway
import io.github.youndie.shashki.server.billing.PayoutRepository
import io.github.youndie.shashki.server.dispatch.CandidateSource
import io.github.youndie.shashki.server.dispatch.DriverIndex
import io.github.youndie.shashki.server.dispatch.DriverReservations
import io.github.youndie.shashki.server.dispatch.GeoCandidateSource
import io.github.youndie.shashki.server.dispatch.GridDriverIndex
import io.github.youndie.shashki.server.dispatch.InMemoryDriverReservations
import io.github.youndie.shashki.server.dispatch.InMemoryOfferBoard
import io.github.youndie.shashki.server.dispatch.OfferBoard
import io.github.youndie.shashki.server.feature.events.Events
import io.github.youndie.shashki.server.feature.events.EventsConfig
import io.github.youndie.shashki.server.feature.events.data.BooblikOutboxPublisher
import io.github.youndie.shashki.server.feature.events.data.BooblikRideHistory
import io.github.youndie.shashki.server.feature.events.domain.InMemoryRideHistory
import io.github.youndie.shashki.server.feature.events.domain.RideHistory
import io.github.youndie.shashki.server.feature.quote.PickupEta
import io.github.youndie.shashki.server.feature.receipt.ReceiptConfig
import io.github.youndie.shashki.server.feature.receipt.domain.ReceiptSender
import io.github.youndie.shashki.server.feature.receipt.domain.SendReceiptUseCase
import io.github.youndie.shashki.server.feature.ride.data.PetichRideRepository
import io.github.youndie.shashki.server.feature.ride.domain.AnswerOfferUseCase
import io.github.youndie.shashki.server.feature.ride.domain.CancelRideUseCase
import io.github.youndie.shashki.server.feature.ride.domain.ExpireOfferUseCase
import io.github.youndie.shashki.server.feature.ride.domain.FindOfferUseCase
import io.github.youndie.shashki.server.feature.ride.domain.RequestRideUseCase
import io.github.youndie.shashki.server.feature.ride.domain.RideRepository
import io.github.youndie.shashki.server.feature.ride.saga.DriverAnswerStep
import io.github.youndie.shashki.server.feature.ride.saga.HoldPaymentStep
import io.github.youndie.shashki.server.feature.ride.saga.OfferStep
import io.github.youndie.shashki.server.feature.ride.saga.OfferTimeouts
import io.github.youndie.shashki.server.feature.ride.saga.PublishAssignedStep
import io.github.youndie.shashki.server.feature.ride.saga.QuoteStep
import io.github.youndie.shashki.server.feature.ride.saga.SagaStorage
import io.github.youndie.shashki.server.feature.ride.saga.ServiceAreaStep
import io.github.youndie.shashki.server.feature.ride.saga.sagaEngine
import io.github.youndie.shashki.server.feature.ride.saga.sagaJson
import io.github.youndie.shashki.server.feature.route.RoutingConfig
import io.github.youndie.shashki.server.feature.settlement.domain.SettleRideUseCase
import io.github.youndie.shashki.server.feature.settlement.saga.CaptureStep
import io.github.youndie.shashki.server.feature.settlement.saga.ChargeAndPayoutStep
import io.github.youndie.shashki.server.feature.settlement.saga.PayoutStep
import io.github.youndie.shashki.server.feature.settlement.saga.PublishSettledStep
import io.github.youndie.shashki.server.feature.settlement.saga.SettleableStep
import io.github.youndie.shashki.server.feature.trip.data.ExposedTripRepository
import io.github.youndie.shashki.server.feature.trip.domain.AdvanceTripUseCase
import io.github.youndie.shashki.server.feature.trip.domain.TripRepository
import io.github.youndie.shashki.server.pricing.Pricing
import io.github.youndie.shashki.server.pricing.RouteEstimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.petich.PetichClock
import ru.workinprogress.petich.PetichEngine
import ru.workinprogress.petich.PetichInterceptor
import ru.workinprogress.petich.PetichRepository
import java.net.InetSocketAddress

/**
 * The ride feature's graph. The database is handed in because it is built before Koin is, and the
 * three stand-ins — straight-line routes, an in-memory gateway, a fixed candidate list — are bound
 * to their ports here so B-23, the real billing and B-20 each replace one line.
 */
public fun rideModule(
    database: Database,
    scope: CoroutineScope,
    /**
     * The router, so a test can hand in a graph it controls. Defaulted rather than always resolved
     * from the environment because the alternative is a test that either needs the city's 41 MB
     * extract or cannot check that the saga prices on roads at all.
     */
    routeEstimator: RouteEstimator = RoutingConfig.estimator(),
    /**
     * The mail relay, defaulted from the environment for the same reason the router is: a test that
     * needed an SMTP server to check a settlement would be a test about SMTP.
     */
    receiptSender: ReceiptSender = ReceiptConfig.sender(),
): Module =
    module {
        single { database }
        single { scope }
        single<PetichClock> { PetichClock { System.currentTimeMillis() } }
        single<Json> { sagaJson() }
        singleOf(::SagaStorage)

        // B-23's one line. `RoutingConfig` decides between the graph and the straight-line
        // stand-in, and says loudly which it chose — the stand-in is still reachable because the
        // saga tests kill the process at phase boundaries and have no use for a routing graph.
        single<RouteEstimator> { routeEstimator }
        single { Pricing() }

        // **The broker, or the honest absence of one, as one value.** Koin resolves by type and
        // binds only non-nullable ones — `single<BooblikOutboxPublisher?>` does not compile — so the
        // absence is a wrapper with two nulls in it rather than two missing bindings. The client's
        // `CrashReporting` has the same shape.
        single<RideHistory> { InMemoryRideHistory() }
        single {
            val address = EventsConfig.address()
            Events(
                publisher = address?.let { BooblikOutboxPublisher(it, scope) },
                consumer = address?.let { BooblikRideHistory(it, get()) },
            )
        }
        // The receipt, bound at last. It was written and tested against a real SMTP server in B-14
        // and constructed by nobody — the settlement saga is what calls it (B-37).
        single<ReceiptSender> { receiptSender }
        factory { SendReceiptUseCase(get()) }
        // The wait a rider is shown: the candidate query and the router, joined (B-31). Named
        // explicitly rather than `singleOf`, like everything else in this module.
        single { PickupEta(candidates = get(), estimator = get()) }
        single<PaymentGateway> { InMemoryPaymentGateway() }
        // The index is a cache of the last known positions, held by one process and rebuilt from
        // the socket after a restart — no repository, no migration, nothing through the broker.
        single<DriverIndex> { GridDriverIndex() }
        single<CandidateSource> { GeoCandidateSource(get(), get()) }
        single<DriverReservations> { InMemoryDriverReservations() }
        single<OfferBoard> { InMemoryOfferBoard() }

        // The timer resumes the saga through the engine, and the engine's steps schedule the timer:
        // a cycle, broken by resolving the engine lazily at fire time rather than at construction.
        single { OfferTimeouts(get()) { rideId, driverId -> get<ExpireOfferUseCase>().invoke(rideId, driverId) } }
        single { OfferStep(get(), get(), get(), get(), get()) }
        single<List<PetichInterceptor<*>>> {
            listOf(
                QuoteStep(get(), get()),
                ServiceAreaStep(),
                HoldPaymentStep(get()),
                get<OfferStep>(),
                DriverAnswerStep(get(), get(), get()),
                PublishAssignedStep(get()),
                // The second saga, in the same engine: each interceptor answers for the payload it
                // knows, so one engine runs whichever saga the row carries (B-37).
                ChargeAndPayoutStep(),
                SettleableStep(),
                CaptureStep(get()),
                PayoutStep(get()),
                PublishSettledStep(get(), get()),
            )
        }
        single<PetichEngine> { sagaEngine(get(), get(), get()) }
        single<PetichRepository> { get<SagaStorage>().petiches }

        // The trip and the ledger: two rows the order saga does not own (research §1.4c, B-37).
        single<TripRepository> { ExposedTripRepository(get()) { get<PetichClock>().nowEpochMs() } }
        single<PayoutRepository> { ExposedPayoutRepository(get()) { get<PetichClock>().nowEpochMs() } }

        single<RideRepository> { PetichRideRepository(get<SagaStorage>().petiches, get()) }
        factory { AdvanceTripUseCase(trips = get(), rides = get(), settle = get()) }
        factory { SettleRideUseCase(engine = get(), sagas = get()) }
        factory { AnswerOfferUseCase(engine = get(), sagas = get(), rides = get()) }
        factory { ExpireOfferUseCase(engine = get(), sagas = get()) }
        factory { CancelRideUseCase(engine = get(), sagas = get(), rides = get(), trips = get(), settle = get()) }
        factory { FindOfferUseCase(board = get(), rides = get(), clock = get()) }
        // An explicit lambda and not `factoryOf(::RequestRideUseCase)`: `factoryOf` resolves every
        // constructor parameter through Koin, default values included, and the use case's `ids`
        // is a `() -> String` with a default that no binding provides. Compilation is silent about
        // it; the first request answers 500 with NoDefinitionFoundException for `Function0`.
        factory { RequestRideUseCase(engine = get(), rides = get()) }
    }
