package io.github.youndie.shashki.server.feature.receipt.domain

import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass

/** What the rider is told the ride cost, once the card has been charged. */
public data class Receipt(
    val rideId: String,
    val to: String,
    val rideClass: RideClass,
    val quote: Quote,
    val pickup: String,
    val dropoff: String,
)

/**
 * Sending it.
 *
 * **A port, because the transport is the thing under test.** B-14 exists to find out whether
 * smtpkn's JVM target works against a real server, and an interface here is what lets the saga be
 * tested without a mail server and the mail server be tested without a saga.
 */
public interface ReceiptSender {
    /** Whether the server took it. A partial refusal is a failure here; smtpkn reports which. */
    public suspend fun send(receipt: Receipt): Boolean
}
