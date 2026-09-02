package io.github.youndie.shashki.server.di

import io.github.youndie.shashki.server.billing.PayoutRepository
import io.github.youndie.shashki.server.feature.documents.domain.DocumentStore
import io.github.youndie.shashki.server.feature.events.Events
import io.github.youndie.shashki.server.feature.events.data.BooblikOutboxPublisher
import io.github.youndie.shashki.server.feature.events.data.BooblikRideHistory
import io.github.youndie.shashki.server.feature.events.domain.RideHistory
import io.github.youndie.shashki.server.feature.receipt.domain.SendReceiptUseCase
import io.github.youndie.shashki.server.feature.ride.domain.RequestRideUseCase
import io.github.youndie.shashki.server.feature.ride.domain.RideRepository
import io.github.youndie.shashki.server.feature.ride.rideModule
import io.github.youndie.shashki.server.feature.ride.saga.OfferTimeouts
import io.github.youndie.shashki.server.feature.settlement.domain.SettleRideUseCase
import io.github.youndie.shashki.server.feature.trip.domain.AdvanceTripUseCase
import io.github.youndie.shashki.server.feature.trip.domain.TripRepository
import io.github.youndie.shashki.server.observability.Observability
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
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
import ru.workinprogress.tracy.agent.TracyAgent
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
 * - a value the lambda supplies by hand — `sagaEngine(get(), get())` passing the step list to
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
            // `PetichEngine` is built by `sagaEngine`, which hands its constructor every
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
                    // `Events` is built by a lambda that constructs both halves from one address —
                    // or neither. `verify()` sees a constructor with two parameters and asks the
                    // container for each; declared per definition rather than as a global
                    // `extraTypes`, so a real disappearance of the same type elsewhere stays visible.
                    definition<Events>(BooblikOutboxPublisher::class, BooblikRideHistory::class),
                    // `Observability` is the same shape: one lambda builds the agent from the
                    // environment, or does not.
                    definition<Observability>(TracyAgent::class),
                    // The HTTP client is built with `HttpClient(CIO)` — the engine is the argument
                    // the lambda supplies, and `verify()` asks the container for it like any other
                    // constructor parameter. Named here for the same reason as the four above.
                    definition<HttpClient>(HttpClientEngine::class),
                    // `RequestRideUseCase` takes the graph's served area as `() -> ServiceArea`,
                    // built by the module's own lambda (B-57). `verify()` sees a `Function0` on the
                    // constructor and asks the container for one, as it does for `OfferTimeouts`.
                    definition<RequestRideUseCase>(kotlin.jvm.functions.Function0::class),
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

        // **The settlement's own bindings, resolved rather than verified** (B-37). `verify()` is
        // reflection over a constructor and the two repositories are bound behind interfaces by
        // explicit lambdas, so it has nothing to look at; only building them says they are here.
        // `SendReceiptUseCase` is the one that matters most: it existed, was tested against a real
        // SMTP server, and was constructed by nobody until the settlement called it.
        assertNotNull(koin.get<TripRepository>(), "the trip is not in the graph")
        assertNotNull(koin.get<PayoutRepository>(), "the payout ledger is not in the graph")
        assertNotNull(koin.get<AdvanceTripUseCase>(), "the driver cannot move a trip")
        assertNotNull(koin.get<SettleRideUseCase>(), "nothing can start a settlement")
        assertNotNull(koin.get<SendReceiptUseCase>(), "the receipt is written and unbound again")

        // **The broker's absence is a value and it resolves** (B-38). This test runs with no
        // `SHASHKI_BOOBLIK`, which is the ordinary case for a checkout, so what it holds is that a
        // server with nowhere to publish still builds its graph — the failure it prevents is a
        // binding that only exists when a broker does.
        // **The object store, resolved because it shipped broken** (B-47). `DocumentsConfig.store`
        // asks the container for an `HttpClient` and nothing bound one, so every document route
        // answered 500 in the running stand — with a store configured and without one. Neither half
        // of this file saw it: `verify()` reflects over the bound type's constructor and
        // `DocumentStore` is an interface, and what a lambda asks the container for is invisible to
        // it. The list above is hand-maintained, and this is the line that was not added; adding it
        // is the whole fix to the guard, and the reason to write that down is that the next binding
        // behind an interface has the same hole waiting for it.
        assertNotNull(koin.get<DocumentStore>(), "a driver's documents have nowhere to go")

        assertNotNull(koin.get<RideHistory>(), "the projection is not in the graph")
        assertNull(koin.get<Events>().publisher, "a publisher appeared with no broker configured")
        assertNull(koin.get<Observability>().tracy, "an agent appeared with no collector configured")
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
        val trap = module { factoryOf(::DefaultedLambda) }

        // Static: nothing reported. `verify()` skips a parameter that has a default.
        trap.verify(extraTypes = listOf(RideRepository::class))

        // Dynamic: the container is asked for a Function0 and has none. The real parameter is a
        // real object rather than an `error()` stub — Koin resolves in order, and a stub that threw
        // would fail before reaching the one being measured, which is what the first version of this
        // test did.
        val koin = koinApplication { modules(trap, module { single<RideRepository> { NoRides } }) }.koin
        // Koin wraps the miss in InstanceCreationException; the NoDefinitionFoundException is its
        // cause, which is also how the production stack trace read.
        val failure = assertFailsWith<org.koin.core.error.InstanceCreationException> { koin.get<DefaultedLambda>() }
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

/**
 * The trap, and it is a class of this test's own (B-57).
 *
 * **It used to borrow `RequestRideUseCase`**, whose constructor has changed twice under it — a clock
 * in B-45, the served area in B-57 — and each time the control's premise ("three real parameters and
 * one defaulted lambda") had to be repaired before the control could say anything. A control that
 * depends on production code keeping a shape is a control that reports on that shape.
 */
private class DefaultedLambda(
    @Suppress("unused") private val rides: RideRepository,
    @Suppress("unused") private val ids: () -> String = { "id" },
)
