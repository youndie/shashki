package io.github.youndie.shashki.server.dispatch

import ru.workinprogress.petich.PetichClock
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A one-shot ticket that carries a driver's identity into a WebSocket upgrade (B-52).
 *
 * **Why a ticket at all: a browser cannot put a header on a WebSocket.** `new WebSocket(url)` takes
 * no `Authorization`, and Ktor's bearer provider — the one every other route on this server uses —
 * reads exactly that header. The two ways round it were:
 *
 * - **the token as the first frame.** No new endpoint, nothing in a URL. It needs the server to
 *   verify a raw JWT by itself, and shildik's `TokenVerifier` is `internal`: taking this route means
 *   shashki implements "is this signature ours" a second time, which is the one thing the service
 *   document says this product must never do.
 * - **this**: an authenticated `POST /api/driver/ticket` mints a random value bound to the token's
 *   subject, and the socket is opened with it. Verification stays in one place — the ticket is
 *   minted behind the same `authenticate` block as every other protected route.
 *
 * **It is not a token and must not become one.** Thirty seconds, single use, no claims: what it
 * proves is that somebody who held a valid token a moment ago is opening this socket. A ticket in a
 * query string is a value that reaches access logs and browser history, which is exactly why it
 * expires in half a minute and dies on redemption — the same reasoning that makes putting the token
 * itself in the URL unacceptable.
 */
public class DriverTickets(
    private val clock: PetichClock,
    private val random: SecureRandom = SecureRandom(),
) {
    private val issued = ConcurrentHashMap<String, Ticket>()

    @OptIn(ExperimentalEncodingApi::class)
    public fun mint(subject: String): String {
        // Expired tickets are swept on the way past rather than by a timer: the map holds one entry
        // per socket a driver opens, and a driver opens one per shift.
        val now = clock.nowEpochMs()
        issued.entries.removeIf { it.value.expiresAtEpochMs <= now }

        val bytes = ByteArray(TICKET_BYTES).also(random::nextBytes)
        val value = Base64.UrlSafe.encode(bytes)
        issued[value] = Ticket(subject, now + LIFETIME_MS)
        return value
    }

    /** The subject, once. A second redemption of the same ticket is a replay and gets nothing. */
    public fun redeem(value: String): String? {
        val ticket = issued.remove(value) ?: return null
        return ticket.subject.takeIf { ticket.expiresAtEpochMs > clock.nowEpochMs() }
    }

    private class Ticket(
        val subject: String,
        val expiresAtEpochMs: Long,
    )

    public companion object {
        /** Long enough to open a socket, short enough that a logged URL is worthless. */
        public const val LIFETIME_MS: Long = 30_000

        private const val TICKET_BYTES = 32
    }
}
