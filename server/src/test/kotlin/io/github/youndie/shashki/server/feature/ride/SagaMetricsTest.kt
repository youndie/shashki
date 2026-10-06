package io.github.youndie.shashki.server.feature.ride

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.youndie.petich.Petich
import io.github.youndie.petich.PetichEngine
import io.github.youndie.petich.PetichPayload
import io.github.youndie.petich.PetichRepository
import io.github.youndie.petich.PetichResult
import io.github.youndie.petich.PetichStatus
import io.github.youndie.petich.PetichStep
import io.github.youndie.petich.PetichStepContext
import io.github.youndie.petich.petichDefinition
import io.github.youndie.shashki.server.feature.ride.saga.RideOutboxEvent
import io.github.youndie.shashki.server.feature.ride.saga.SagaMetrics
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-99: what this server's engine metrics do when petich actually calls them.
 *
 * A dropped event used to be a throw, and petich B-52 catches every throw from a counter and reports it
 * nowhere — so the loud path was silent, and no test had ever made it fire. This one does, through a real
 * engine: an outbox event emitted against a repository that cannot store events, which is exactly the
 * wiring `requireOutbox` refuses at construction and the only way the method can be reached.
 */
class SagaMetricsTest {
    @Serializable
    private data class Trip(
        val rideId: String,
    ) : PetichPayload()

    private class Emits : PetichStep<Trip> {
        override suspend fun execute(
            ctx: PetichStepContext,
            payload: Trip,
        ) = ctx.emit(RideOutboxEvent(id = "${payload.rideId}:ride.assigned", type = "ride.assigned", payload = "{}"))

        override suspend fun compensate(
            ctx: PetichStepContext,
            payload: Trip,
        ) = Unit
    }

    /** Stores sagas and cannot store events: the wiring `requireOutbox` exists to refuse. */
    private class Plain : PetichRepository {
        private val rows = mutableMapOf<String, Petich>()

        override suspend fun findById(id: String): Petich? = rows[id]

        override suspend fun saveOrGet(petich: Petich): Petich = rows.getOrPut(petich.id) { petich }

        override suspend fun update(petich: Petich): Boolean {
            rows[petich.id] = petich
            return true
        }
    }

    @Test
    fun `a dropped event is an ERROR line, and the saga still completes`() {
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        val logger = LoggerFactory.getLogger("shashki.saga") as Logger
        logger.addAppender(appender)
        try {
            val engine =
                PetichEngine(
                    repository = Plain(),
                    metrics = SagaMetrics,
                    definitions = listOf(petichDefinition<Trip>("trip") { step("assign", Emits()) }),
                )
            val result =
                runBlocking {
                    engine.process(
                        Petich(id = "ride-1", type = "trip", status = PetichStatus.DRAFT, payload = Trip("ride-1")),
                    )
                }

            assertTrue(result is PetichResult.Success, "the saga did not complete: $result")
            val errors = appender.list.filter { it.level == Level.ERROR }.map { it.formattedMessage }
            assertEquals(
                1,
                errors.size,
                "a dropped event left no ERROR line: ${appender.list.map { it.formattedMessage }}",
            )
            assertTrue("1 outbox event(s) of saga type 'trip' were dropped" in errors.single(), errors.single())
        } finally {
            logger.detachAppender(appender)
        }
    }
}
