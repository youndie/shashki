package io.github.youndie.shashki.rider.feature.ride

import io.github.youndie.shashki.protocol.ClassQuote
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.rider.feature.ride.domain.QuoteJourneyUseCase
import io.github.youndie.shashki.rider.feature.ride.domain.RequestRideUseCase
import io.github.youndie.shashki.rider.feature.ride.ui.ClassPickerUiAction
import io.github.youndie.shashki.rider.feature.ride.ui.ClassPickerUiEvent
import io.github.youndie.shashki.rider.feature.ride.ui.ClassPickerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ClassPickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val rides = FakeRideRepository()

    @BeforeTest
    fun main() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `it asks the server what the journey costs and stops loading`() =
        runTest(dispatcher) {
            val model = viewModel()

            advanceUntilIdle()

            val state = model.uiState.value
            assertFalse(state.loading)
            assertEquals(2, state.quotes.size)
            assertEquals(22_806, state.distanceMetres)
        }

    /**
     * The class the rider picked is the class that is ordered — the assertion that would silently
     * fail if the screen and the request read different fields.
     */
    @Test
    fun `ordering sends the selected class and answers with the ride`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.onAction(ClassPickerUiAction.Select(RideClass.COMFORT))
            model.onAction(ClassPickerUiAction.Order)
            advanceUntilIdle()
            // **Read after the fact, not collected alongside.** The events are a buffered `Channel`,
            // which is exactly a thing you can read afterwards; a concurrent collector would add a
            // scheduling question the test does not exist to answer.
            val event = model.events.first()

            assertEquals(RideClass.COMFORT, rides.requested?.rideClass)
            assertIs<ClassPickerUiEvent.Ordered>(event)
            assertEquals("ride-1", event.rideId)
        }

    /**
     * B-31's second criterion, on the client's side.
     *
     * **A class nobody is driving cannot be selected**, and the tile that says so still reports a
     * click — the kit draws the unavailable state and leaves the decision to the screen. Ordering it
     * would create a ride the saga cancels for want of cars a second later, which is a worse answer
     * than the tile already gave.
     */
    @Test
    fun `a class with no cars cannot be selected`() =
        runTest(dispatcher) {
            rides.quotes =
                FakeRideRepository.QUOTES.copy(
                    classes =
                        listOf(
                            ClassQuote(RideClass.ECONOMY, Quote(22_806, 2_079, 2_490, "USD"), pickupEtaSeconds = 240),
                            ClassQuote(RideClass.COMFORT, Quote(22_806, 2_079, 3_890, "USD"), pickupEtaSeconds = null),
                        ),
                )
            val model = viewModel()
            advanceUntilIdle()

            model.onAction(ClassPickerUiAction.Select(RideClass.COMFORT))

            assertEquals(RideClass.ECONOMY, model.uiState.value.selected)
            assertFalse(model.uiState.value.hasCars(RideClass.COMFORT))
        }

    /**
     * **The screen opens on a class the rider can order.** `ECONOMY` is the default before anything
     * is known; if the answer says nobody is driving one, staying there would leave a greyed row
     * with the order bar live under it.
     */
    @Test
    fun `the opening selection moves to a class that has cars`() =
        runTest(dispatcher) {
            rides.quotes =
                FakeRideRepository.QUOTES.copy(
                    classes =
                        listOf(
                            ClassQuote(RideClass.ECONOMY, Quote(22_806, 2_079, 2_490, "USD"), pickupEtaSeconds = null),
                            ClassQuote(RideClass.COMFORT, Quote(22_806, 2_079, 3_890, "USD"), pickupEtaSeconds = 360),
                        ),
                )
            val model = viewModel()

            advanceUntilIdle()

            assertEquals(RideClass.COMFORT, model.uiState.value.selected)
        }

    /** A server that does not answer leaves a screen that says so, not one that hangs on "…". */
    @Test
    fun `a failure stops the loading state and reports once`() =
        runTest(dispatcher) {
            rides.failWith = RuntimeException("connection refused")
            val model = viewModel()
            advanceUntilIdle()
            val event = model.events.first()

            assertFalse(model.uiState.value.loading)
            assertTrue(
                model.uiState.value.quotes
                    .isEmpty(),
            )
            assertIs<ClassPickerUiEvent.Failed>(event)
        }

    private fun viewModel() =
        ClassPickerViewModel(
            quoteJourney = QuoteJourneyUseCase(rides),
            requestRide = RequestRideUseCase(rides),
            pickup = GeoPoint(46.0511, 14.5051),
            dropoff = GeoPoint(46.2237, 14.4576),
            riderId = "rider-1",
            paymentMethodId = "card-4417",
        )
}
