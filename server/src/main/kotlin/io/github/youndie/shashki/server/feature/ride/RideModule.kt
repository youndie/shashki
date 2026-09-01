package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.server.billing.InMemoryPaymentGateway
import io.github.youndie.shashki.server.billing.PaymentGateway
import io.github.youndie.shashki.server.dispatch.CandidateSource
import io.github.youndie.shashki.server.dispatch.DriverReservations
import io.github.youndie.shashki.server.dispatch.FixedCandidateSource
import io.github.youndie.shashki.server.dispatch.InMemoryDriverReservations
import io.github.youndie.shashki.server.dispatch.InMemoryOfferBoard
import io.github.youndie.shashki.server.dispatch.OfferBoard
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
import io.github.youndie.shashki.server.feature.ride.saga.orderSagaEngine
import io.github.youndie.shashki.server.feature.ride.saga.sagaJson
import io.github.youndie.shashki.server.pricing.Pricing
import io.github.youndie.shashki.server.pricing.RouteEstimator
import io.github.youndie.shashki.server.pricing.StraightLineRouteEstimator
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

/**
 * The ride feature's graph. The database is handed in because it is built before Koin is, and the
 * three stand-ins — straight-line routes, an in-memory gateway, a fixed candidate list — are bound
 * to their ports here so B-23, the real billing and B-20 each replace one line.
 */
public fun rideModule(
    database: Database,
    scope: CoroutineScope,
): Module =
    module {
        single { database }
        single { scope }
        single<PetichClock> { PetichClock { System.currentTimeMillis() } }
        single<Json> { sagaJson() }
        singleOf(::SagaStorage)

        single<RouteEstimator> { StraightLineRouteEstimator() }
        single { Pricing() }
        single<PaymentGateway> { InMemoryPaymentGateway() }
        single<CandidateSource> { FixedCandidateSource() }
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
            )
        }
        single<PetichEngine> { orderSagaEngine(get(), get(), get()) }
        single<PetichRepository> { get<SagaStorage>().petiches }

        single<RideRepository> { PetichRideRepository(get<SagaStorage>().petiches) }
        factory { AnswerOfferUseCase(engine = get(), sagas = get(), rides = get()) }
        factory { ExpireOfferUseCase(engine = get(), sagas = get()) }
        factory { CancelRideUseCase(engine = get(), sagas = get(), rides = get()) }
        factory { FindOfferUseCase(board = get(), rides = get()) }
        // An explicit lambda and not `factoryOf(::RequestRideUseCase)`: `factoryOf` resolves every
        // constructor parameter through Koin, default values included, and the use case's `ids`
        // is a `() -> String` with a default that no binding provides. Compilation is silent about
        // it; the first request answers 500 with NoDefinitionFoundException for `Function0`.
        factory { RequestRideUseCase(engine = get(), rides = get()) }
    }
