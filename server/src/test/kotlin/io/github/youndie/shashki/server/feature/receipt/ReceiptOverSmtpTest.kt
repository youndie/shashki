package io.github.youndie.shashki.server.feature.receipt

import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.feature.receipt.data.SmtpConfig
import io.github.youndie.shashki.server.feature.receipt.data.SmtpReceiptSender
import io.github.youndie.shashki.server.feature.receipt.domain.Receipt
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The receipt through smtpkn's JVM target, over a real TLS handshake, into a real Mailpit.
 *
 * **This is the whole point of B-14 and of §1.6d.** smtpkn claims `linuxX64`; the JVM target
 * compiles and runs its suite in CI but is not claimed, because TLS there goes through `SSLEngine`
 * and that is the one part the native path does not share. So the assertion that matters is not
 * "a mail arrived" but "a mail arrived **after a verified TLS handshake**" — which is why
 * `dangerouslyDisableCertificateVerification` is not used and the CA is handed over instead.
 *
 * **Skipped unless a Mailpit is named**, because CI has none. The run behind B-14's numbers —
 * **four variables and two certificates**, because the second test is the control and it has its own
 * assumption: with only the first three set, the control skips, the positive test passes, and the
 * build is green while the half that makes the result mean anything did not run (B-87 measured that:
 * `tests=2 skipped=1`).
 *
 * ```bash
 * cd /tmp/mailpit-tls
 * openssl req -x509 -newkey rsa:2048 -nodes -keyout key.pem -out cert.pem -days 30 \
 *   -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1'
 * # the unrelated CA the control needs: it signed nothing here, and that is the point
 * openssl req -x509 -newkey rsa:2048 -nodes -keyout wrong-key.pem -out wrong-ca.pem -days 30 \
 *   -subj '/CN=nobody'
 * docker run -d --name mailpit -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 \
 *   -v /tmp/mailpit-tls:/tls -e MP_SMTP_TLS_CERT=/tls/cert.pem -e MP_SMTP_TLS_KEY=/tls/key.pem \
 *   axllent/mailpit:latest
 * SHASHKI_MAILPIT=127.0.0.1:1025 SHASHKI_MAILPIT_API=http://127.0.0.1:8025 \
 *   SHASHKI_MAILPIT_CA=/tmp/mailpit-tls/cert.pem \
 *   SHASHKI_MAILPIT_WRONG_CA=/tmp/mailpit-tls/wrong-ca.pem \
 *   ./gradlew :server:test --tests '*ReceiptOverSmtpTest*'
 * ```
 *
 * Read the report rather than the build's exit code: `assumeTrue` skips are green.
 * `server/build/test-results/test/TEST-*ReceiptOverSmtpTest.xml` has to say `skipped="0"`.
 */
class ReceiptOverSmtpTest {
    @Test
    fun `a receipt goes out over STARTTLS and arrives in Mailpit`() =
        // **`runBlocking` and not `runTest`.** smtpkn's timeouts are `withTimeout`, and `runTest`'s
        // virtual clock advances the moment every coroutine is suspended — which is exactly what a
        // socket waiting for a reply looks like. The first run of this test failed with
        // "Timed out after 5m waiting for the reply to EHLO" in under a second.
        runBlocking {
            val smtp = System.getenv(SMTP_VARIABLE)
            val api = System.getenv(API_VARIABLE)
            val ca = System.getenv(CA_VARIABLE)
            assumeTrue(
                !smtp.isNullOrBlank() && !api.isNullOrBlank() && !ca.isNullOrBlank(),
                "no $SMTP_VARIABLE / $API_VARIABLE / $CA_VARIABLE: this test needs a Mailpit with TLS",
            )

            val rideId = "ride-b14-" + System.nanoTime()
            val sender =
                SmtpReceiptSender(
                    SmtpConfig(
                        host = smtp!!.substringBefore(':'),
                        port = smtp.substringAfter(':').toInt(),
                        from = "receipts@shashki.example",
                        clientIdentity = "shashki.example",
                        // Verification stays **on**; this is the CA that signed Mailpit's
                        // certificate. Turning verification off would test the socket, not the TLS.
                        caBundlePath = ca,
                    ),
                )

            val sent =
                sender.send(
                    Receipt(
                        rideId = rideId,
                        to = "rider@example.com",
                        rideClass = RideClass.COMFORT,
                        quote =
                            Quote(
                                distanceMetres = 22_806,
                                durationSeconds = 2_079,
                                amountCents = 3_890,
                                currency = "USD",
                            ),
                        pickup = "Slovenska cesta 15",
                        dropoff = "Airport, terminal B",
                    ),
                )

            assertTrue(sent, "smtpkn reported the receipt as not delivered")

            val inbox = get("$api/api/v1/messages")
            assertTrue(rideId in inbox, "Mailpit has no message for $rideId; it holds: ${inbox.take(400)}")
            assertTrue("shashki · $rideId" in inbox, "the subject did not survive the wire")
        }

    /**
     * The control, and the reason the test above means anything.
     *
     * A send that succeeded would prove a message arrived; it would not prove the certificate was
     * checked. Pointed at a CA that did not sign Mailpit's certificate, the same code must fail —
     * and if it does not, `SSLEngine` is trusting something it should not, which is precisely the
     * part of smtpkn that is unclaimed.
     */
    @Test
    fun `a certificate no configured CA signed is refused`() =
        runBlocking {
            val smtp = System.getenv(SMTP_VARIABLE)
            val wrongCa = System.getenv(WRONG_CA_VARIABLE)
            assumeTrue(
                !smtp.isNullOrBlank() && !wrongCa.isNullOrBlank(),
                "no $SMTP_VARIABLE / $WRONG_CA_VARIABLE: this control needs the same Mailpit and an unrelated CA",
            )

            val failure =
                runCatching {
                    SmtpReceiptSender(
                        SmtpConfig(
                            host = smtp!!.substringBefore(':'),
                            port = smtp.substringAfter(':').toInt(),
                            from = "receipts@shashki.example",
                            clientIdentity = "shashki.example",
                            caBundlePath = wrongCa,
                        ),
                    ).send(
                        Receipt(
                            rideId = "ride-control",
                            to = "rider@example.com",
                            rideClass = RideClass.ECONOMY,
                            quote = Quote(1, 1, 1, "USD"),
                            pickup = "a",
                            dropoff = "b",
                        ),
                    )
                }.exceptionOrNull()

            assertTrue(
                failure != null,
                "a receipt went out to a certificate no configured CA signed; verification is not on",
            )
        }

    private fun get(url: String): String =
        HttpClient
            .newHttpClient()
            .send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
            .body()

    private companion object {
        const val SMTP_VARIABLE = "SHASHKI_MAILPIT"
        const val API_VARIABLE = "SHASHKI_MAILPIT_API"
        const val CA_VARIABLE = "SHASHKI_MAILPIT_CA"
        const val WRONG_CA_VARIABLE = "SHASHKI_MAILPIT_WRONG_CA"
    }
}
