package io.github.youndie.shashki.server.feature.ride.saga

import io.github.youndie.petich.EnrichedPayload
import io.github.youndie.petich.PetichClock
import io.github.youndie.petich.PetichDefinition
import io.github.youndie.petich.PetichEngine
import io.github.youndie.petich.PetichEngineConfig
import io.github.youndie.petich.PetichEngineMetrics
import io.github.youndie.petich.PetichPayload
import io.github.youndie.petich.PetichStepRecord
import io.github.youndie.petich.SimpleEnrichedPayload
import io.github.youndie.petich.postgres.ExposedOutboxRepository
import io.github.youndie.petich.postgres.ExposedPetichRepository
import io.github.youndie.petich.postgres.OutboxEventsTable
import io.github.youndie.petich.postgres.PetichTable
import io.github.youndie.shashki.server.feature.settlement.saga.Charged
import io.github.youndie.shashki.server.feature.settlement.saga.SettlementPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.exposed.v1.jdbc.Database

/** The `Json` the saga's rows are written with. Polymorphism is registered here and nowhere else. */
public fun sagaJson(): Json =
    Json {
        ignoreUnknownKeys = true
        serializersModule =
            SerializersModule {
                polymorphic(PetichPayload::class) {
                    subclass(OrderPayload::class)
                    subclass(SettlementPayload::class)
                }
                polymorphic(EnrichedPayload::class) { subclass(SimpleEnrichedPayload::class) }
                // What a member recorded about what it did, by that member's key. A record whose
                // class is not registered fails to deserialize, and the member it belongs to then
                // compensates blind — which for `Charged` means a tip's refund that cannot find the
                // charge it is meant to give back.
                polymorphic(PetichStepRecord::class) { subclass(Charged::class) }
            }
    }

/** The tables and repositories petich needs, built once over one `Database`. */
public class SagaStorage(
    database: Database,
    json: Json,
    /**
     * **The same clock the engine and the sweeper use, and it was not** (B-93).
     *
     * `ExposedPetichRepository` takes a clock and defaults it to the wall clock, so this passed none
     * and the store stamped `updated_at` from `System.currentTimeMillis()` while everything else in
     * the saga read the injected `PetichClock`. In production both are the wall clock and nothing
     * shows. The sweeper's stranded query compares one against the other — `updated_at < now -
     * stuckAfter` — so with a controlled clock the two are years apart and it matches nothing,
     * silently.
     *
     * That is why this parameter exists rather than a test that moved its own clock to meet the
     * store: one mechanism, one source of time.
     *
     * **And it has no default.** One was written here first and `kapkan`'s wall-clock rule refused
     * it, in as many words: a time that has to agree with somebody else's is a value carried in,
     * not one read here. That is the whole defect restated — a default is what let the two clocks
     * diverge without anybody choosing it.
     */
    clock: PetichClock,
) {
    public val petichTable: PetichTable = PetichTable(json)
    public val outboxTable: OutboxEventsTable = OutboxEventsTable()
    public val petiches: ExposedPetichRepository = ExposedPetichRepository(database, petichTable, outboxTable, clock)
    public val outbox: ExposedOutboxRepository = ExposedOutboxRepository(database, outboxTable)
}

/**
 * The engine, and the two settings that are decisions rather than defaults.
 *
 * **One engine for both sagas**, as before — but for a better reason than `supports`. It used to run
 * whichever saga the row carried because each interceptor answered whether it handled the payload in
 * front of it. petich now keeps definitions by type and resolves the row itself, so the settlement
 * arrives as a `PetichDefinition` and the order saga as the interceptor list it still is. The two
 * models sit side by side here on purpose: this is the migration, and B-33 removes the older arm.
 *
 * **`requireOutbox = true`.** Without it an engine whose repository cannot store events drops them,
 * the saga completes, its state is correct, and only the consumer at the far end never runs
 * (research §1.4b). With it, an engine over the wrong repository refuses to be built. The test
 * `RequireOutboxTest` holds that refusal.
 *
 * **`onDroppedEvents` throws.** It cannot fire while `requireOutbox` holds, and that is the point:
 * if it ever does, something has changed under this constructor, and a counter nobody reads is not
 * the place to find out.
 */
public fun sagaEngine(
    storage: SagaStorage,
    @Suppress(
        "ktlint:kapkan:wall-clock",
        "the default of the engine's injectable clock; every saga test passes its own",
    )
    clock: PetichClock = PetichClock { System.currentTimeMillis() },
    // NO LONGER DEFAULTED: it was, so that the migration could move one saga at a time, and both
    // have moved. An engine built here with no definitions is a bug rather than a stage.
    definitions: List<PetichDefinition<*>>,
): PetichEngine =
    PetichEngine(
        repository = storage.petiches,
        config = PetichEngineConfig(requireOutbox = true),
        clock = clock,
        metrics = RefusingMetrics,
        definitions = definitions,
    )

private object RefusingMetrics : PetichEngineMetrics {
    override fun onDroppedEvents(
        type: String,
        count: Int,
    ): Unit =
        error(
            "$count outbox event(s) of saga type '$type' were dropped — requireOutbox is meant to make this impossible",
        )
}
