package io.github.youndie.shashki.rider.feature.history

import io.github.youndie.shashki.protocol.RideStatus
import io.github.youndie.shashki.rider.feature.history.ui.HistoryViewModel
import io.github.youndie.shashki.rider.feature.ride.FakeRideRepository
import io.github.youndie.shashki.rider.feature.ride.domain.MyRidesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * R9's list, as the screen receives it (B-45).
 *
 * **What is asserted is the money.** A finished ride shows what the settlement took, a ride cancelled
 * after a driver set off shows the fee, and one nobody drove shows `$ 0` — the two cancellations are
 * told apart in the list exactly as they are in the settlement, which is the item's third criterion
 * on the client's side.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val rides = FakeRideRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HistoryViewModel(MyRidesUseCase(rides), profile = listOf("name" to "rider-1"))

    @Test
    fun `each row carries what its ride actually cost`() =
        runTest(dispatcher) {
            rides.history =
                listOf(
                    FakeRideRepository.REQUESTED.copy(
                        id = "ride-3",
                        status = RideStatus.COMPLETED,
                        chargedCents = 2_896,
                    ),
                    FakeRideRepository.REQUESTED.copy(
                        id = "ride-2",
                        status = RideStatus.CANCELLED,
                        chargedCents = 724,
                    ),
                    FakeRideRepository.REQUESTED.copy(id = "ride-1", status = RideStatus.CANCELLED),
                )

            val model = viewModel()
            advanceUntilIdle()

            // Flattened, because the order across months is the server's too (B-61).
            val rows =
                model.uiState.value.months
                    .flatMap { it.trips }
            assertEquals(listOf("ride-3", "ride-2", "ride-1"), rows.map { it.id }, "the server's order was not kept")
            assertEquals(
                listOf("$ 28.96", "$ 7.24", "$ 0"),
                rows.map { it.amount },
                "the kit's row says the zero (B-78)",
            )
            assertEquals(rows[0].from, rows[0].title.substringBefore(" — "), "the stack's first line is the pickup")
            assertEquals(rows[0].to, rows[0].title.substringAfter(" — "), "and its second the drop-off")
            assertTrue(rows[1].meta.contains("cancelled"), rows[1].meta)
            assertTrue(rows[0].title.contains(" — "), "the row shows one end of the journey: ${rows[0].title}")
        }

    /** An empty list is a state, not a failure — and the screen has a line for it. */
    @Test
    fun `a rider with no rides gets an empty list and no error`() =
        runTest(dispatcher) {
            val model = viewModel()

            advanceUntilIdle()

            assertEquals(emptyList(), model.uiState.value.months)
            assertEquals(false, model.uiState.value.loading, "the screen would say nothing for ever")
        }
}
