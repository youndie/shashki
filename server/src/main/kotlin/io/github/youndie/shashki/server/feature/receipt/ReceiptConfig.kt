package io.github.youndie.shashki.server.feature.receipt

import io.github.youndie.shashki.server.feature.receipt.data.SmtpConfig
import io.github.youndie.shashki.server.feature.receipt.data.SmtpReceiptSender
import io.github.youndie.shashki.server.feature.receipt.domain.Receipt
import io.github.youndie.shashki.server.feature.receipt.domain.ReceiptSender
import org.slf4j.LoggerFactory

/**
 * Where the mail relay is, or the fact that there is none.
 *
 * **The same shape as `RoutingConfig` and `AuthConfig`, and for the same reason.** A server with no
 * relay configured has to start — every saga test, every golden run and every demo of the map would
 * otherwise need an SMTP server — so the absence is a value and it says so at `warn`. What it must
 * never be is silent: a settlement that thinks it sent a receipt and did not is the failure this
 * whole feature exists to make visible.
 */
public object ReceiptConfig {
    public const val HOST_VARIABLE: String = "SHASHKI_SMTP_HOST"
    public const val PORT_VARIABLE: String = "SHASHKI_SMTP_PORT"
    public const val FROM_VARIABLE: String = "SHASHKI_SMTP_FROM"
    public const val USER_VARIABLE: String = "SHASHKI_SMTP_USER"
    public const val PASSWORD_VARIABLE: String = "SHASHKI_SMTP_PASSWORD"
    public const val CA_VARIABLE: String = "SHASHKI_SMTP_CA"

    private val LOG = LoggerFactory.getLogger(ReceiptConfig::class.java)

    public fun sender(env: (String) -> String? = System::getenv): ReceiptSender {
        val host = env(HOST_VARIABLE)?.takeIf { it.isNotBlank() }
        if (host == null) {
            LOG.warn("no {}: receipts are recorded as unsent rather than delivered", HOST_VARIABLE)
            return UnsentReceipts
        }
        return SmtpReceiptSender(
            SmtpConfig(
                host = host,
                port = env(PORT_VARIABLE)?.toIntOrNull() ?: DEFAULT_PORT,
                from = env(FROM_VARIABLE)?.takeIf { it.isNotBlank() } ?: DEFAULT_FROM,
                clientIdentity = CLIENT_IDENTITY,
                username = env(USER_VARIABLE)?.takeIf { it.isNotBlank() },
                password = env(PASSWORD_VARIABLE)?.takeIf { it.isNotBlank() },
                caBundlePath = env(CA_VARIABLE)?.takeIf { it.isNotBlank() },
            ),
        )
    }

    private const val DEFAULT_PORT = 587
    private const val DEFAULT_FROM = "receipts@shashki.example"
    private const val CLIENT_IDENTITY = "shashki"
}

/**
 * No relay: the receipt is not sent, and the settlement records that it was not.
 *
 * **It returns `false` rather than throwing or pretending.** `false` is a value the saga already
 * handles — `Settled.RECEIPT` goes into the enriched payload either way — so a ride whose receipt
 * never went is findable afterwards instead of being indistinguishable from one whose did.
 */
public object UnsentReceipts : ReceiptSender {
    override suspend fun send(receipt: Receipt): Boolean = false
}
