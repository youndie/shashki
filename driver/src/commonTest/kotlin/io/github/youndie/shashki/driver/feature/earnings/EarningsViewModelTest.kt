package io.github.youndie.shashki.driver.feature.earnings

import io.github.youndie.shashki.driver.feature.earnings.domain.EarningsRepository
import io.github.youndie.shashki.driver.feature.earnings.domain.ReadEarningsUseCase
import io.github.youndie.shashki.driver.feature.earnings.ui.EarningsViewModel
import io.github.youndie.shashki.protocol.EarningsView
import io.github.youndie.shashki.ui.kompot.EarningsTile
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
 * D6's three numbers, as the screen receives them (B-46).
 *
 * **What is asserted is that the client computes nothing.** The sums are the server's — payout rows,
 * not fares — and this turns cents into the kit's figures. A tile whose size the renderer would
 * refuse is the other half: `EarningsTile.ALLOWED_SIZES` is the grid, and a screen that asked for a
 * width outside it would silently draw nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EarningsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class Fixed(
        private val view: EarningsView,
    ) : EarningsRepository {
        override suspend fun earnings(): EarningsView = view
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the three sums become the page figure and the kit's tiles`() =
        runTest(dispatcher) {
            val model =
                EarningsViewModel(
                    ReadEarningsUseCase(Fixed(EarningsView(2_316, 11_840, 98_765, "USD"))),
                )

            advanceUntilIdle()
            val state = model.uiState.value

            assertEquals("$ 23.16", state.today)
            assertEquals(listOf("$ 23.16", "$ 118.40", "$ 987.65"), state.tiles.map { it.figure })
            assertEquals(listOf("today", "week", "all time"), state.tiles.map { it.label })
            assertTrue(state.tiles.all { it.size in EarningsTile.ALLOWED_SIZES }, "a tile the grid cannot draw")
            assertEquals(1, state.tiles.count { it.accent }, "more than one tile asked for the accent")
        }

    /** A driver who has earned nothing sees zero rather than nothing: the row is the fact. */
    @Test
    fun `nothing earned is a figure and not an empty screen`() =
        runTest(dispatcher) {
            val model = EarningsViewModel(ReadEarningsUseCase(Fixed(EarningsView(0, 0, 0, "USD"))))

            advanceUntilIdle()

            assertEquals("$ 0", model.uiState.value.today)
            assertEquals(false, model.uiState.value.loading)
        }
}
