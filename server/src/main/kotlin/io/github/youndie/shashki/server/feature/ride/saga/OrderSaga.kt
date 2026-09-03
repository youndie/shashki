package io.github.youndie.shashki.server.feature.ride.saga

import io.github.youndie.shashki.server.feature.settlement.saga.SettlementPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.exposed.v1.jdbc.Database
import ru.workinprogress.petich.EnrichedPayload
import ru.workinprogress.petich.PetichClock
import ru.workinprogress.petich.PetichEngine
import ru.workinprogress.petich.PetichEngineConfig
import ru.workinprogress.petich.PetichEngineMetrics
import ru.workinprogress.petich.PetichInterceptor
import ru.workinprogress.petich.PetichPayload
import ru.workinprogress.petich.SimpleEnrichedPayload
import ru.workinprogress.petich.postgres.ExposedOutboxRepository
import ru.workinprogress.petich.postgres.ExposedPetichRepository
import ru.workinprogress.petich.postgres.OutboxEventsTable
import ru.workinprogress.petich.postgres.PetichTable

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
            }
    }

/** The tables and repositories petich needs, built once over one `Database`. */
public class SagaStorage(
    database: Database,
    json: Json,
) {
    public val petichTable: PetichTable = PetichTable(json)
    public val outboxTable: OutboxEventsTable = OutboxEventsTable()
    public val petiches: ExposedPetichRepository = ExposedPetichRepository(database, petichTable, outboxTable)
    public val outbox: ExposedOutboxRepository = ExposedOutboxRepository(database, outboxTable)
}

/**
 * The engine, and the two settings that are decisions rather than defaults.
 *
 * **One engine for both sagas, because that is what `supports` is for.** The order saga and the
 * settlement (B-37) have different steps and different payloads; each interceptor answers whether it
 * handles the payload in front of it, so one engine over both step lists runs whichever saga the row
 * carries. Two engines would be two configurations to keep in step and two places to forget
 * `requireOutbox`.
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
    steps: List<PetichInterceptor<*>>,
    storage: SagaStorage,
    @Suppress(
        "ktlint:kapkan:wall-clock",
        "the default of the engine's injectable clock; every saga test passes its own",
    )
    clock: PetichClock = PetichClock { System.currentTimeMillis() },
): PetichEngine =
    PetichEngine(
        interceptors = steps,
        repository = storage.petiches,
        config = PetichEngineConfig(requireOutbox = true),
        clock = clock,
        metrics = RefusingMetrics,
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
