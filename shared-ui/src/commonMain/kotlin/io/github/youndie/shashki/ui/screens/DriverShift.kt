package io.github.youndie.shashki.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiTheme
import io.github.youndie.shashki.ui.components.OfferCard

/** What the shift screen has to say, as plain values. The bundle formats; this draws. */
public data class DriverShiftState(
    val online: Boolean,
    val driverLabel: String,
    val classLabel: String,
    /**
     * Reports the **server** acknowledged. `null` while offline — a zero would read as a broken
     * socket.
     *
     * Not what the client wrote: those were the same number until a bundle whose id the token
     * contradicted had every frame refused and this went on rising (B-54).
     */
    val reported: Int?,
    /**
     * Where the position being sent comes from, already worded — `null` while offline.
     *
     * **A parked driver is a fact and not a bug**, and this is the line that makes the difference
     * visible: a bundle sending its configured point looks exactly like one sending a device's
     * until the screen says which (B-49).
     */
    val positionLabel: String? = null,
    val offer: DriverOfferState? = null,
)

/** An offer, already formatted. The seconds are counted by whoever holds the deadline. */
public data class DriverOfferState(
    val fare: String,
    val classAndPayment: String,
    val secondsLeft: Int,
    val secondsTotal: Int,
    val pickup: String,
    val pickupMeta: String,
    val dropoff: String,
    val dropoffMeta: String,
)

/**
 * D1: the driver's shift. Off, waiting, or holding an offer.
 *
 * **Three states and one screen, because that is how a shift feels.** The card does not arrive on a
 * new page — the driver was looking at this screen, and now there is something on it. The rejected
 * shape was an offer route of its own, and `DriverRoute` says why: an address for a thing that lives
 * fifteen seconds is a link that is broken by design.
 *
 * **Waiting is deliberately dull.** The whole of the driver's attention is meant to be available for
 * the card when it comes, so the waiting state is a word and a count and nothing that moves. The one
 * animated thing in this application is the offer's own bar, which is [OfferCard]'s and drains left
 * to right.
 *
 * The count is the honest part: `reported` rises when the **server** acknowledges a position, so a
 * driver who is "online" over a socket that quietly died — or one whose every frame is being refused
 * — sees a number that has stopped. It said this before B-54 and counted the frames the client had
 * written, which is a fact about this application rather than about the shift. Amber for
 * online is `DriverTheme`'s accent — the kit reserves red for cancellation in both applications.
 */
@Composable
public fun DriverShift(
    state: DriverShiftState,
    onToggleOnline: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    /** The driver's earnings (B-46). Does nothing where there is nowhere to go. */
    onEarnings: () -> Unit = {},
    /** The driver's documents (B-47), the other thing done between rides. */
    onDocuments: () -> Unit = {},
    /** What to say about the documents, or `null` when there is nothing to say. */
    documentsLabel: String? = null,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = MARGIN),
    ) {
        Spacer(Modifier.height(TOP))
        // **The header is the way to D6.** A driver looks at what they have earned between rides,
        // and this screen is where they are between rides; a second control at the bottom would
        // crowd the one switch that matters while a shift is running.
        Column(Modifier.clickable(onClick = onEarnings)) {
            KvadrantText(state.driverLabel, style = type.meta.copy(color = colors.subtle))
            KvadrantText(state.classLabel, style = type.rowEmphasis)
        }
        // **A line rather than a button**, and in the foreground brush rather than the accent.
        // B-48's rule reads "accent-coloured text is a control's label", and this is a control's
        // label — but the light golden measured the same 2.11:1 that rule was written to stop.
        // The rule holds where the accent is a *surface* with black ink on it; as ink on the light
        // background this amber is unreadable wherever it lands, control or not. The header above
        // is also tappable and is also plain, so the accent here would have been the odd one out.
        documentsLabel?.let { label ->
            KvadrantText(
                label,
                Modifier.clickable(onClick = onDocuments).padding(top = 4.dp),
                style = type.meta.copy(color = colors.foreground),
            )
        }

        Spacer(Modifier.height(GAP))

        val offer = state.offer
        if (offer != null) {
            OfferCard(
                fare = offer.fare,
                classAndPayment = offer.classAndPayment,
                secondsLeft = offer.secondsLeft,
                secondsTotal = offer.secondsTotal,
                pickup = offer.pickup,
                pickupMeta = offer.pickupMeta,
                dropoff = offer.dropoff,
                dropoffMeta = offer.dropoffMeta,
                onAccept = onAccept,
                onDecline = onDecline,
            )
            Spacer(Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    KvadrantText(
                        if (state.online) ONLINE else OFFLINE,
                        style =
                            type.stateHeadline.copy(
                                color = if (state.online) colors.accent else colors.inactive,
                                textAlign = TextAlign.Center,
                            ),
                    )
                    state.reported?.let { taken ->
                        Spacer(Modifier.height(GAP))
                        // Beside the count rather than under it: the count says the socket is alive
                        // and the source says what is travelling over it, and they are one fact.
                        val line = listOfNotNull("$taken positions taken", state.positionLabel).joinToString(" · ")
                        KvadrantText(line, style = type.meta.copy(color = colors.subtle))
                    }
                }
            }
        }

        // The shift switch, at the app bar's height and drawn here rather than taken from the
        // library: B-15 answered "may an app bar carry a filled action" with *simplify*, and this is
        // the same answer OfferCard's strip already gives.
        Row(
            Modifier
                .fillMaxWidth()
                .height(BAR)
                .border(RING, if (state.online) colors.accent else colors.inactive)
                .clickable(onClick = onToggleOnline),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KvadrantText(
                if (state.online) GO_OFFLINE else GO_ONLINE,
                style = if (state.online) type.body.copy(color = colors.accent) else type.body,
            )
        }
        Spacer(Modifier.size(MARGIN))
    }
}

private const val ONLINE = "waiting"
private const val OFFLINE = "offline"
private const val GO_ONLINE = "go online"
private const val GO_OFFLINE = "go offline"

private val MARGIN = 12.dp
private val GAP = 12.dp
private val TOP = 24.dp
private val BAR = 54.dp
private val RING = 1.dp
