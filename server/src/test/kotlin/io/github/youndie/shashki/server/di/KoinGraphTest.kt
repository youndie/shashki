package io.github.youndie.shashki.server.di

import io.github.youndie.shashki.server.feature.ride.domain.RequestRideUseCase
import io.github.youndie.shashki.server.feature.ride.domain.RideRepository
import io.github.youndie.shashki.server.feature.ride.rideModule
import io.github.youndie.shashki.server.feature.ride.saga.OfferTimeouts
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.MissingKoinDefinitionException
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import ru.workinprogress.petich.CompensationFailureHandler
import ru.workinprogress.petich.PetichClock
import ru.workinprogress.petich.PetichEngine
import ru.workinprogress.petich.PetichEngineConfig
import ru.workinprogress.petich.PetichEngineMetrics
import ru.workinprogress.petich.PetichRepository
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Koin resolves lazily, so a missing binding is a runtime failure at the first `by inject` — in a
 * route, in production, under a user. Verifying the graph turns that into a test.
 *
 * `verify()` reflects over the constructor of every definition's **bound type** — `singleOf` and
 * `single { … }` alike — and asks whether each parameter type has a binding. It never builds
 * anything, which is why a module that needs a `Database` can be verified without one. Two things
 * follow, and the first run of this file found both:
 *
 * - a value the lambda supplies by hand — `orderSagaEngine(get(), get())` passing the step list to
 *   `PetichEngine`'s constructor — is a parameter `verify()` still asks the container for. It is
 *   declared with `injections`, per type, so the exemption is as narrow as the fact;
 * - a `factoryOf` over a constructor with a defaulted `() -> String` is reported as a missing
 *   `Function0` — *before* any request, which is the whole value of this test (the positive
 *   control below).
 */
class KoinGraphTest {
    private val noDatabase: Database = Database.connect({ error("the graph verifier never opens a connection") })
    private val noScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every definition in the ride module can be resolved`() {
        rideModule(noDatabase, noScope).verify(
            // `PetichEngine` is built by `orderSagaEngine`, which hands its constructor every
            // argument itself; `verify()` cannot see that and asks the container for each one in
            // turn. All six are named, per definition rather than as a global `extraTypes`, so a
            // genuinely missing `List` or `PetichRepository` elsewhere is still reported.
            injections =
                injectedParameters(
                    definition<PetichEngine>(
                        List::class,
                        PetichRepository::class,
                        CompensationFailureHandler::class,
                        PetichEngineConfig::class,
                        PetichClock::class,
                        PetichEngineMetrics::class,
                    ),
                    // `OfferTimeouts` takes its `onExpired` as a suspend lambda written in the
                    // module — a `Function3` once the continuation is counted.
                    definition<OfferTimeouts>(kotlin.jvm.functions.Function3::class),
                ),
        )
    }

    @Test
    fun `the graph really produces the bindings it claims`() {
        // `verify()` over an empty module passes, so it is not by itself evidence of anything.
        // Resolving a binding by type is. The repository is the one that reaches through the
        // saga storage and the tables — built, not connected: Exposed's `connect` is lazy.
        val koin = koinApplication { modules(rideModule(noDatabase, noScope)) }.koin
        assertNotNull(koin.get<RideRepository>(), "the ride repository is not in the graph")
    }

    /**
     * The controls, and the reason this file has a second half.
     *
     * `RequestRideUseCase` has `ids: () -> String = { UUID… }`. `factoryOf(::RequestRideUseCase)`
     * ignores the default and asks the container for a `Function0`, and the first request answered
     * 500 with `NoDefinitionFoundException` while the compiler said nothing (B-11). The question
     * this answers is whether `verify()` would have said something — and it would not: the static
     * verifier skips a parameter that has a default, so the module passes `verify()` and fails on
     * resolution. The two tests below are that pair, and the third separates "has a default" from
     * "is a function type".
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `verify() passes a factoryOf over a defaulted lambda parameter that resolution then fails`() {
        val trap = module { factoryOf(::RequestRideUseCase) }
        val provided = listOf(PetichEngine::class, RideRepository::class)

        // Static: nothing reported.
        trap.verify(extraTypes = provided)

        // Dynamic: the container is asked for a Function0 and has none. The first two parameters
        // are real objects, not `error()` stubs — Koin resolves constructor parameters in order,
        // and a stub that throws would fail the resolution before it reached the one being
        // measured (which is exactly what the first version of this test did).
        val koin =
            koinApplication {
                modules(
                    trap,
                    module {
                        single<PetichEngine> { PetichEngine(interceptors = emptyList(), repository = NoSagas) }
                        single<RideRepository> { NoRides }
                    },
                )
            }.koin
        // Koin wraps the miss in InstanceCreationException; the NoDefinitionFoundException is its
        // cause, which is also how the production stack trace read.
        val failure = assertFailsWith<org.koin.core.error.InstanceCreationException> { koin.get<RequestRideUseCase>() }
        val chain = generateSequence<Throwable>(failure) { it.cause }.joinToString(" <- ") { it.message.orEmpty() }
        assertTrue("Function0" in chain, chain)
    }

    private object NoSagas : PetichRepository {
        override suspend fun findById(id: String): ru.workinprogress.petich.Petich? = null

        override suspend fun saveOrGet(petich: ru.workinprogress.petich.Petich) = petich

        override suspend fun update(petich: ru.workinprogress.petich.Petich): Boolean = true
    }

    private object NoRides : RideRepository {
        override suspend fun find(id: String): io.github.youndie.shashki.protocol.RideView? = null
    }

    class NeedsLambda(
        val make: () -> String,
    )

    class NeedsDefaultedInt(
        val n: Int = 5,
    )

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `verify() reports a non-defaulted lambda parameter and skips a defaulted Int`() {
        val lambda = module { factoryOf(::NeedsLambda) }
        val failure = assertFailsWith<MissingKoinDefinitionException> { lambda.verify() }
        assertTrue("Function0" in failure.message.orEmpty(), failure.message)

        // A default of any type is what `verify()` skips — it is not about function types.
        module { factoryOf(::NeedsDefaultedInt) }.verify()
    }
}
