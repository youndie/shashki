package io.github.youndie.shashki.rider.format

import io.github.youndie.shashki.protocol.Quote

/**
 * The three numbers a rider reads, formatted in one place.
 *
 * **Not `String.format`**: it does not exist on Kotlin/Wasm, and the platform-specific ways round it
 * are how a price ends up rendered differently in the two bundles. These do the arithmetic and hand
 * back a string, which is the same string everywhere.
 *
 * The currency is written as the server sent it — the money is the server's and so is its name.
 */
public fun Quote.asMoney(): String = "${currencySymbol()} ${amountCents.centsAsMajor()}"

private fun Quote.currencySymbol(): String =
    when (currency) {
        "USD" -> "$"
        "EUR" -> "€"
        else -> currency
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
