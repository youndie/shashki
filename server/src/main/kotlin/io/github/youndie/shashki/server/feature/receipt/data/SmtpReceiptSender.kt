package io.github.youndie.shashki.server.feature.receipt.data

import io.github.youndie.shashki.server.feature.receipt.domain.Receipt
import io.github.youndie.shashki.server.feature.receipt.domain.ReceiptSender
import io.github.youndie.smtp.client.Envelope
import io.github.youndie.smtp.client.SmtpClientConfig
import io.github.youndie.smtp.client.openSmtpSession
import io.github.youndie.smtp.mime.MessageBuilder
import io.github.youndie.smtp.protocol.Mailbox
import io.github.youndie.smtp.sasl.PlainMechanism
import io.github.youndie.smtp.tls.jvm.SslEngineTlsProvider
import io.github.youndie.smtp.transport.TlsConfig
import io.github.youndie.smtp.transport.ktor.connectSmtp
import org.slf4j.LoggerFactory
import kotlin.time.Clock

/** Where the relay is and who we are to it. */
public data class SmtpConfig(
    val host: String,
    val port: Int,
    val from: String,
    val clientIdentity: String,
    val username: String? = null,
    val password: String? = null,
    /**
     * The CA that signed the relay's certificate, when it is not one the JVM already trusts.
     *
     * **`null` means the system trust store, never "trust anything".** smtpkn offers
     * `dangerouslyDisableCertificateVerification` and this class does not expose it: the one thing
     * B-14 is checking is TLS through `SSLEngine`, and a test that turned verification off would be
     * checking the socket instead.
     */
    val caBundlePath: String? = null,
)

/**
 * The receipt, over SMTP, through smtpkn's JVM target.
 *
 * **This is the point of the feature rather than an implementation detail of it.** Research §1.6d:
 * smtpkn claims `linuxX64` and says the JVM target compiles and runs its suite in CI but is not
 * claimed, because TLS goes through `SSLEngine` there and that is the one part the native path does
 * not share. A reference service is what turns "compiles" into "claimed" — so this sends real mail
 * over a real TLS handshake, and [B-14](../../../../../../../../../../docs/backlog/B-14-receipt-over-smtpkn-jvm.md)
 * gates it against Mailpit.
 *
 * A JVM mail library would have worked and would have removed the only thing this part of the demo
 * demonstrates.
 */
public class SmtpReceiptSender(
    private val config: SmtpConfig,
    private val clock: Clock = Clock.System,
) : ReceiptSender {
    @Suppress(
        "ktlint:kapkan:swallowed-failure",
        "quitting a session that is already gone is not a failure anybody can act on",
    )
    override suspend fun send(receipt: Receipt): Boolean {
        val transport = connectSmtp(config.host, config.port)
        val session =
            openSmtpSession(
                transport = transport,
                config = SmtpClientConfig(clientIdentity = config.clientIdentity),
            )
        return try {
            session.startTls(
                SslEngineTlsProvider,
                TlsConfig(serverName = config.host, caBundlePath = config.caBundlePath),
            )
            // **Asserted, which it could not be until youndie/smtpkn#4.** `SmtpSession.encrypted` was
            // declared and never assigned, so this flag was permanently false and the check that
            // belongs here was impossible to write — B-14 recorded that and put the demonstration in
            // a test's negative control instead. The fix is in build 0.1.0-20260902.062954-3, and
            // this is the line the feature always wanted: a silent downgrade is exactly the failure
            // it exists to catch, and now it costs one `check`.
            check(session.isEncrypted) { "the session is not encrypted after STARTTLS" }
            if (config.username != null && config.password != null) {
                session.authenticate(PlainMechanism(config.username, config.password))
            }

            val from = Mailbox.parse(config.from)
            val to = Mailbox.parse(receipt.to)
            val message =
                MessageBuilder(from = from, to = listOf(to))
                    .apply {
                        subject = "shashki · ${receipt.rideId}"
                        text = receipt.asText()
                    }.build(sentAt = clock.now(), messageIdDomain = from.domain)

            val result = session.send(Envelope(sender = from, recipients = listOf(to)), message)
            // **A partial refusal is a failure here and smtpkn keeps the detail.** With one
            // recipient the distinction is academic; the reason it is written this way is that
            // `accepted.isNotEmpty()` would quietly become wrong the day a receipt is copied to
            // somebody, and the log line is what would be missing then.
            if (result.rejected.isNotEmpty()) {
                LOG.warn("receipt for {} rejected: {}", receipt.rideId, result.rejected)
            }
            result.rejected.isEmpty() && result.accepted.isNotEmpty()
        } finally {
            runCatching { session.quit() }
        }
    }

    /** Plain text only: an HTML receipt is a design artefact and the kit has not drawn one. */
    private fun Receipt.asText(): String =
        buildString {
            appendLine("Your ride is finished.")
            appendLine()
            appendLine("From:     $pickup")
            appendLine("To:       $dropoff")
            appendLine("Class:    ${rideClass.name.lowercase()}")
            appendLine("Distance: ${quote.distanceMetres / 1000.0} km")
            appendLine("Time:     ${quote.durationSeconds / 60} min")
            appendLine("Charged:  ${quote.amountCents / 100.0} ${quote.currency}")
            appendLine()
            appendLine("Ride $rideId")
        }

    private companion object {
        val LOG = LoggerFactory.getLogger(SmtpReceiptSender::class.java)!!
    }
}
