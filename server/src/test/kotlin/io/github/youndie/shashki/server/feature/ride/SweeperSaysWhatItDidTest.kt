package io.github.youndie.shashki.server.feature.ride

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.youndie.petich.ExpiringPetichRepository
import io.github.youndie.petich.Petich
import io.github.youndie.petich.PetichClock
import io.github.youndie.petich.PetichEngine
import io.github.youndie.petich.PetichPayload
import io.github.youndie.petich.PetichPhase
import io.github.youndie.petich.PetichStatus
import io.github.youndie.petich.PetichStep
import io.github.youndie.petich.PetichStepContext
import io.github.youndie.petich.petichDefinition
import io.github.youndie.shashki.server.feature.ride.saga.sagaSweeper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * B-95: the sweeper was built with four of its ten parameters, so nothing it did or failed at
 * reached this application at all.
 *
 * **Against the real logger, not a seam.** `SweeperReport` could have taken a lambda the test
 * supplies, and then the test would prove something about the lambda. What has to be proved is that
 * petich's callback reaches a line under `shashki.sweeper`, so the appender goes on that logger and
 * the sweeper is the real one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SweeperSaysWhatItDidTest {
    @Serializable
    private data class Fare(
        val rideId: String,
    ) : PetichPayload()

    private class Inert : PetichStep<Fare> {
        override suspend fun execute(
            ctx: PetichStepContext,
            payload: Fare,
        ) = Unit

        override suspend fun compensate(
            ctx: PetichStepContext,
            payload: Fare,
        ) = Unit
    }

    private class Rows : ExpiringPetichRepository {
        val rows: MutableMap<String, Petich> = mutableMapOf()

        /** The failure the item is about: the query this worker lives on cannot be served. */
        var expiryFails: Boolean = false

        fun seed(petich: Petich) {
            rows[petich.id] = petich
        }

        override suspend fun findById(id: String): Petich? = rows[id]

        override suspend fun saveOrGet(petich: Petich): Petich = rows.getOrPut(petich.id) { petich }

        override suspend fun update(petich: Petich): Boolean {
            val existing = rows[petich.id] ?: return false
            if (petich.version != existing.version + 1) return false
            rows[petich.id] = petich
            return true
        }

        override suspend fun findExpired(
            nowEpochMs: Long,
            limit: Int,
        ): List<Petich> {
            if (expiryFails) throw IllegalStateException("the expiry index is being rebuilt")
            return rows.values
                .filter { it.status == PetichStatus.PENDING_SIGNATURE }
                .filter { (it.suspendedUntilEpochMs ?: Long.MAX_VALUE) <= nowEpochMs }
                .take(limit)
        }

        override suspend fun findStuck(
            status: PetichStatus,
            notTouchedSinceEpochMs: Long,
            limit: Int,
        ): List<Petich> = rows.values.filter { it.status == status }.take(limit)
    }

    private fun engineOver(repository: Rows) =
        PetichEngine(
            repository = repository,
            clock = PetichClock { NOW },
            definitions = listOf(petichDefinition<Fare>("fare") { step("settle", Inert()) }),
        )

    /**
     * THE SERVER'S OWN WIRING, not a sweeper this test assembled.
     *
     * The first version of this file built its own `SuspendedPetichSweeper` with the three
     * callbacks passed, and stayed green when they were deleted from `Application` — proving that
     * `SweeperReport` works and nothing about whether anything calls it. That is the item's defect
     * reproduced inside its own test, which is why the composition has a name.
     */
    private fun sweeperOver(
        repository: Rows,
        stuckAfter: kotlin.time.Duration? = null,
    ) = sagaSweeper(
        repository = repository,
        engine = engineOver(repository),
        clock = PetichClock { NOW },
        stuckAfter = stuckAfter,
    )

    /** Everything written under `shashki.sweeper` while [body] runs. */
    private fun recorded(body: () -> Unit): List<String> {
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        val logger = LoggerFactory.getLogger("shashki.sweeper") as Logger
        logger.addAppender(appender)
        try {
            body()
        } finally {
            logger.detachAppender(appender)
        }
        return appender.list.map { it.formattedMessage }
    }

    @Test
    fun `a pass whose query cannot be served leaves a line naming the queue`() {
        val repository = Rows()
        repository.expiryFails = true

        val lines =
            recorded {
                runTest {
                    // Through `start` rather than `sweep()`, because the catch that calls the
                    // reporter is in the worker's loop: a direct `sweep()` would throw at the test
                    // and report nothing, which is the version of this test that proves nothing.
                    val job = sweeperOver(repository).start(this)
                    runCurrent()
                    job.cancel()
                }
            }

        // THE ACCEPTANCE. Without this line a worker that has failed every pass for an hour is
        // indistinguishable from one with nothing to sweep.
        assertEquals(1, lines.size, "$lines")
        assertTrue(lines.single().startsWith("sweeper failed at sweep"), lines.single())
        assertTrue(lines.single().contains("the expiry index is being rebuilt"), lines.single())
    }

    @Test
    fun `a saga picked up after its process died leaves a line`() {
        val repository = Rows()
        repository.seed(
            Petich(
                id = "ride-stranded",
                type = "fare",
                currentPhase = PetichPhase.EXECUTION,
                status = PetichStatus.PROCESSING,
                payload = Fare("ride-stranded"),
            ),
        )

        val lines =
            recorded { runTest { sweeperOver(repository, stuckAfter = 5.minutes).sweepStuck() } }

        // B-93 gave the sweeper this job and nobody read the answer. The rate is normally zero, and
        // its becoming non-zero is the only sign this system has that instances die mid-saga.
        assertEquals(1, lines.size, "$lines")
        assertTrue(lines.single().contains("ride-stranded"), lines.single())
        assertTrue(lines.single().contains("after the process running it died"), lines.single())
    }

    @Test
    fun `a row whose definition was deployed away is named rather than skipped in silence`() {
        val repository = Rows()
        repository.seed(
            Petich(
                id = "ride-orphan",
                type = "surge",
                currentPhase = PetichPhase.EXECUTION,
                status = PetichStatus.PENDING_SIGNATURE,
                payload = Fare("ride-orphan"),
                suspendedUntilEpochMs = NOW - 1,
            ),
        )

        val lines = recorded { runTest { sweeperOver(repository).sweep() } }

        // The engine skips these rather than ending them, which is right — a deploy that dropped a
        // definition would otherwise finish every saga of that type, irreversibly. It is also why
        // nothing else would ever mention them again.
        assertEquals(1, lines.size, "$lines")
        assertTrue(lines.single().contains("surge"), lines.single())
        assertTrue(lines.single().contains("ride-orphan"), lines.single())
        assertEquals(PetichStatus.PENDING_SIGNATURE, repository.rows["ride-orphan"]?.status)
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
