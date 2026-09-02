package io.github.youndie.shashki.server.feature.ride

import io.github.youndie.shashki.server.feature.ride.saga.ServiceAreaStep
import io.github.youndie.shashki.server.pricing.ServiceArea
import ru.workinprogress.petich.Petich
import ru.workinprogress.petich.PetichEngine
import ru.workinprogress.petich.PetichEngineConfig
import ru.workinprogress.petich.PetichRepository
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** B-11's second acceptance criterion, held by a test rather than by a comment. */
class RequireOutboxTest {
    /** A repository that stores sagas and cannot store events. Correct, and silently lossy. */
    private object PlainRepository : PetichRepository {
        override suspend fun findById(id: String): Petich? = null

        override suspend fun saveOrGet(petich: Petich): Petich = petich

        override suspend fun update(petich: Petich): Boolean = true
    }

    @Test
    fun `an engine that requires the outbox refuses a repository that cannot store events`() {
        val refusal =
            assertFailsWith<IllegalArgumentException> {
                PetichEngine(
                    interceptors = listOf(ServiceAreaStep { ServiceArea.LJUBLJANA }),
                    repository = PlainRepository,
                    config = PetichEngineConfig(requireOutbox = true),
                )
            }
        assertTrue("OutboxAwarePetichRepository" in (refusal.message ?: ""), refusal.message)
    }

    @Test
    fun `the same repository is accepted when the outbox is not required, which is the defect this guards`() {
        PetichEngine(interceptors = listOf(ServiceAreaStep { ServiceArea.LJUBLJANA }), repository = PlainRepository)
    }
}
