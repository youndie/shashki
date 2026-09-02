package io.github.youndie.shashki.ui.format

import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.Quote

/**
 * The three numbers both applications read, formatted in one place.
 *
 * **It lived in `:rider` and moved here when the driver needed the same fare (B-29)**, which is what
 * its own note below had already predicted: the failure this exists to prevent is the price being
 * rendered differently in the two bundles, and a copy in each bundle is that failure with extra
 * steps.
 *
 * **Not `String.format`**: it does not exist on Kotlin/Wasm, and the platform-specific ways round it
 * are how a price ends up rendered differently in the two bundles. These do the arithmetic and hand
 * back a string, which is the same string everywhere.
 *
 * The currency is written as the server sent it — the money is the server's and so is its name.
 */
public fun Quote.asMoney(): String = money(amountCents, currency)

/**
 * The same money, for an amount that is not a whole quote — a cancellation fee, a day's takings.
 *
 * Added rather than formatting a fee at its call site (B-43): a second way of writing money is a
 * second way of getting it wrong, and the fee sits next to the fare on the same screen.
 */
public fun money(
    amountCents: Long,
    currency: String,
): String = "${currency.symbol()} ${amountCents.centsAsMajor()}"

private fun String.symbol(): String =
    when (this) {
        "USD" -> "$"
        "EUR" -> "€"
        else -> this
    }

private fun Long.centsAsMajor(): String {
    val major = this / 100
    val minor = (this % 100).toInt()
    return if (minor == 0) "$major" else "$major.${minor.toString().padStart(2, '0')}"
}

/** Kilometres to one decimal, because the kit's `26.3 km` has one. */
public fun Int.asDistance(): String {
    val hundredMetres = (this + 50) / 100
    return "${hundredMetres / 10}.${hundredMetres % 10} km"
}

/** Whole minutes, rounded up: a rider told "0 min" for a car forty seconds away stops believing it. */
public fun Int.asDuration(): String = "${(this + 59) / 60} min"

/**
 * A point, as four decimals — about eleven metres, which is a street rather than a building.
 *
 * **This is what a place looks like when nothing has geocoded it.** There is no address service in
 * this product and inventing a street name would be the client asserting something nobody measured;
 * coordinates are ugly and true. `toString` is not enough on its own: a `Double` prints seventeen
 * digits when it feels like it, and a card whose lines change width between offers is a card that
 * cannot be read in two seconds.
 */
public fun GeoPoint.asCoordinates(): String = "${lat.toFourPlaces()}, ${lon.toFourPlaces()}"

private fun Double.toFourPlaces(): String {
    val scaled = (this * 10_000).toLong()
    val sign = if (scaled < 0) "-" else ""
    val whole = (if (scaled < 0) -scaled else scaled)
    return "$sign${whole / 10_000}.${(whole % 10_000).toString().padStart(4, '0')}"
}
