package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.server.billing.InMemoryPaymentGateway
import io.github.youndie.shashki.server.billing.PaymentGateway
import io.github.youndie.shashki.server.dispatch.CandidateSource
import io.github.youndie.shashki.server.dispatch.DriverReservations
import io.github.youndie.shashki.server.dispatch.FixedCandidateSource
import io.github.youndie.shashki.server.dispatch.InMemoryDriverReservations
import io.github.youndie.shashki.server.feature.ride.data.PetichRideRepository
import io.github.youndie.shashki.server.feature.ride.domain.RequestRideUseCase
import io.github.youndie.shashki.server.feature.ride.domain.RideRepository
import io.github.youndie.shashki.server.feature.ride.saga.HoldPaymentStep
import io.github.youndie.shashki.server.feature.ride.saga.PublishAssignedStep
import io.github.youndie.shashki.server.feature.ride.saga.QuoteStep
import io.github.youndie.shashki.server.feature.ride.saga.ReserveDriverStep
import io.github.youndie.shashki.server.feature.ride.saga.SagaStorage
import io.github.youndie.shashki.server.feature.ride.saga.ServiceAreaStep
import io.github.youndie.shashki.server.feature.ride.saga.orderSagaEngine
import io.github.youndie.shashki.server.feature.ride.saga.sagaJson
import io.github.youndie.shashki.server.pricing.Pricing
import io.github.youndie.shashki.server.pricing.RouteEstimator
import io.github.youndie.shashki.server.pricing.StraightLineRouteEstimator
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.workinprogress.petich.PetichEngine
import ru.workinprogress.petich.PetichInterceptor

/**
 * The ride feature's graph. The database is handed in because it is built before Koin is, and the
 * three stand-ins — straight-line routes, an in-memory gateway, a fixed candidate list — are bound
 * to their ports here so B-23, the real billing and B-20 each replace one line.
 */
public fun rideModule(database: Database): Module =
    module {
        single { database }
        single<Json> { sagaJson() }
        singleOf(::SagaStorage)

        single<RouteEstimator> { StraightLineRouteEstimator() }
        single { Pricing() }
        single<PaymentGateway> { InMemoryPaymentGateway() }
        single<CandidateSource> { FixedCandidateSource() }
        single<DriverReservations> { InMemoryDriverReservations() }

        single<List<PetichInterceptor<*>>> {
            listOf(
                QuoteStep(get(), get()),
                ServiceAreaStep(),
                HoldPaymentStep(get()),
                ReserveDriverStep(get(), get()),
                PublishAssignedStep(get()),
            )
        }
        single<PetichEngine> { orderSagaEngine(get(), get()) }

        single<RideRepository> { PetichRideRepository(get<SagaStorage>().petiches) }
        // An explicit lambda and not `factoryOf(::RequestRideUseCase)`: `factoryOf` resolves every
        // constructor parameter through Koin, default values included, and the use case's `ids`
        // is a `() -> String` with a default that no binding provides. Compilation is silent about
        // it; the first request answers 500 with NoDefinitionFoundException for `Function0`.
        factory { RequestRideUseCase(engine = get(), rides = get()) }
    }
