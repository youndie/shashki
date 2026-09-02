package io.github.youndie.shashki.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.components.KvadrantAppBarButton
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiIcons
import io.github.youndie.shashki.ui.ShashkiTheme
import io.github.youndie.shashki.ui.components.ClassTile
import io.github.youndie.shashki.ui.components.ClassTileState
import io.github.youndie.shashki.ui.map.MapPane
import io.github.youndie.shashki.ui.map.MapScene

/** One row of the picker, as the screen receives it. Prices are formatted by whoever knows the currency. */
public data class RideClassOffer(
    val name: String,
    val meta: String,
    val price: String?,
    val carRects: Int,
    val available: Boolean = true,
)

/**
 * R4: the map, where you are going, what it costs in each class, and how you pay.
 *
 * **The first screen that puts Compose over a map, which is why it is one of B-01's two.** The
 * kit gives the map 360 of the 844 dp and hangs everything else below it — so on this screen the
 * map is a *sized element*, not a backdrop. That is the measurement the four routes disagree about:
 * routes 1 and 3 draw the map outside Compose's canvas and can only approximate a rectangle inside
 * it, and route 4 is a composable like any other.
 *
 * The phone's own status strip (`mts ru 9:41`) is in the artboard and deliberately not here: the
 * operating system draws it, and an application that painted its own would be drawing a picture of
 * a phone.
 *
 * One accent surface, and it is the selected class — the kit's rule, and the reason the order action
 * at the bottom is a bar button rather than a filled bar.
 */
@Composable
public fun RiderClassPicker(
    scene: MapScene,
    destination: String,
    destinationMeta: String,
    offers: List<RideClassOffer>,
    selectedIndex: Int,
    paymentLabel: String,
    orderLabel: String,
    /**
     * Whether there is anything to order (B-62).
     *
     * **A bar that offers a ride nobody can take is the tile's defect one row down.** The kit draws
     * a disabled action in the disabled brush and lets it be pressed by nobody, which is what a
     * screen with no cars in it should look like.
     */
    canOrder: Boolean = true,
    onSelect: (Int) -> Unit,
    onChangePayment: () -> Unit,
    onOrder: () -> Unit,
    modifier: Modifier = Modifier,
    /** The overflow: the rider's own pages (B-45). Does nothing where there is nowhere to go. */
    onMore: () -> Unit = {},
) {
    val colors = KvadrantTheme.colors
    val metrics = KvadrantTheme.metrics
    val type = ShashkiTheme.typography

    Column(modifier.fillMaxSize().background(colors.background)) {
        MapPane(scene, Modifier.fillMaxWidth().height(MAP_HEIGHT))

        Column(
            Modifier.weight(1f).padding(start = metrics.margin, top = 20.dp, end = metrics.margin),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                KvadrantText(destination, style = type.figure)
                KvadrantText(destinationMeta, style = type.body.copy(color = colors.subtle))
            }

            Column(verticalArrangement = Arrangement.spacedBy(metrics.tileGap)) {
                offers.forEachIndexed { index, offer ->
                    ClassTile(
                        name = offer.name,
                        meta = offer.meta,
                        price = offer.price,
                        state =
                            when {
                                !offer.available -> ClassTileState.Unavailable
                                index == selectedIndex -> ClassTileState.Selected
                                else -> ClassTileState.Default
                            },
                        carRects = offer.carRects,
                        onClick = { onSelect(index) },
                    )
                }
            }

            PaymentRow(paymentLabel, onChangePayment)
        }

        OrderBar(orderLabel, canOrder, onOrder, onMore)
    }
}

@Composable
private fun PaymentRow(
    label: String,
    onChange: () -> Unit,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography
    Column {
        Box(Modifier.fillMaxWidth().height(HAIRLINE).background(colors.foreground.copy(alpha = HAIRLINE_ALPHA)))
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = rememberVectorPainter(ShashkiIcons.card),
                contentDescription = null,
                modifier = Modifier.size(ROW_GLYPH),
                colorFilter = ColorFilter.tint(colors.foreground),
            )
            KvadrantText(label, Modifier.weight(1f), style = type.body)
            KvadrantText(
                "change",
                Modifier.clickable(onClick = onChange),
                style = type.meta.copy(color = colors.accent),
            )
        }
    }
}

/**
 * The kit's bottom bar: a chrome strip at the app bar's height carrying a ring, a label and the
 * overflow dots.
 *
 * **Not `KvadrantAppBar`, and this is the second screen where that is true.** The library's bar
 * centres its buttons and puts their labels underneath; the kit's R4 and D3 bars are an action row —
 * ring and label side by side, dots at the far end. The ring itself *is* the library's, through
 * `KvadrantAppBarButton`, which is where the 36 dp visual and the 48 dp touch target come from.
 */
@Composable
private fun OrderBar(
    label: String,
    canOrder: Boolean,
    onOrder: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .height(KvadrantTheme.metrics.appBarHeight)
            .background(colors.chrome)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KvadrantAppBarButton(onClick = { if (canOrder) onOrder() }, label = null) {
                Image(
                    painter = rememberVectorPainter(ShashkiIcons.check),
                    contentDescription = null,
                    modifier = Modifier.size(ROW_GLYPH).align(Alignment.Center),
                    colorFilter = ColorFilter.tint(if (canOrder) colors.foreground else colors.disabled),
                )
            }
            KvadrantText(label, style = type.body.copy(color = if (canOrder) colors.foreground else colors.disabled))
        }
        Spacer(Modifier.size(0.dp))
        // **The dots go somewhere now** (B-45). They were the kit's shape with nothing behind them
        // until R9 existed; the rider's own pages are what an overflow on this screen is for.
        KvadrantText(
            "···",
            Modifier.clickable(onClick = onMore),
            style = type.tileLabel.copy(color = colors.border),
        )
    }
}

/** The kit gives the map 360 of the 844 dp canvas. */
private val MAP_HEIGHT = 360.dp
private val ROW_GLYPH = 20.dp
private val HAIRLINE = 1.dp
private const val HAIRLINE_ALPHA = 0.12f
