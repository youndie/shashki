package io.github.youndie.shashki.server.feature.auth

import io.github.youndie.shashki.auth.SignInAttempt
import io.github.youndie.shashki.auth.SignInConfig
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.server.shashki
import io.github.youndie.shashki.server.testing.PostgresHarness
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import ru.workinprogress.oidc.OidcConfig
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rider's routes behind a token.
 *
 * **A guard that is off by default is a guard nobody notices is off**, and this server's is off by
 * default on purpose — a demo pointed at no provider has nobody to sign in against. So the switch is
 * tested on both sides: [RideRoutesTest] already covers the open server, and the first test here
 * requires a 401 from the closed one.
 *
 * The 401 needs no provider to be reachable, which is why it runs everywhere: a request with no
 * `Authorization` header is refused before anything is verified. The second test does need one, and
 * is the criterion B-26 actually asks for — a token **this project's client obtained** is one this
 * project's server accepts. Nothing about that is provable from either side alone: the client could
 * be handed a token by a provider whose keys the server never fetches, and the server could accept
 * one no client of ours can produce.
 *
 * ```bash
 * docker compose -f docker/compose.yaml up -d && bash docker/bootstrap-shildik.sh
 * SHASHKI_SHILDIK=http://127.0.0.1:18081 ./gradlew :server:test --tests '*ProtectedRides*'
 * ```
 */
class ProtectedRidesTest {
    @BeforeTest
    fun clean() = PostgresHarness.truncateAll()

    @Test
    fun `with a provider configured the rider's routes refuse a request that carries no token`() =
        testApplication {
            application { shashki(PostgresHarness.database, oidc = unreachableProvider()) }
            val client = typedClient()
            startApplication()

            val created =
                client.post(Rides()) {
                    contentType(ContentType.Application.Json)
                    setBody(request())
                }

            // 401 rather than 403: nobody claimed to be anybody. The provider in the configuration
            // is deliberately not running — reaching it would mean the refusal happened too late.
            assertEquals(HttpStatusCode.Unauthorized, created.status, created.bodyAsText().take(200))
        }

    @Test
    fun `a token the rider client signed in for is one this server accepts`() {
        val issuer = System.getenv(ISSUER_VARIABLE)
        assumeTrue(!issuer.isNullOrBlank(), "no $ISSUER_VARIABLE: this test needs a running shildik")

        testApplication {
            application {
                shashki(
                    PostgresHarness.database,
                    oidc = OidcConfig(url = issuer!!, realm = REALM, clientId = CLIENT),
                )
            }
            val client = typedClient()
            startApplication()

            val token = signIn(issuer!!)

            val created =
                client.post(Rides()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(request())
                }

            assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText().take(300))

            // **The control the acceptance needs.** A validator that accepted anything would pass
            // every line above; what separates it from one that checks a signature is a token that
            // is well-formed, unexpired and *not* signed by this provider. So a character of the
            // signature is changed and the same request must be refused.
            //
            // **In the middle of the segment, not at its end** (B-89). A signature is 64 bytes for
            // ES256 or 256 for RS256, and both leave a remainder of one modulo three — so base64url
            // spends its *final* character on two significant bits and four of padding, which means
            // that character can only ever be `A`, `Q`, `g` or `w`. The rule below used to be "if it
            // is `A` make it `B`", and `A` and `B` decode to the same byte: one token in four was
            // not forged at all, the signature stayed valid, the server rightly answered 201, and
            // this line reported it as "a broken signature was accepted" — the test accusing the
            // server. Measured over 3 000 signatures: 755 of them for ES256, 713 for RS256, against
            // none for the character below, which carries all six of its bits.
            val body = token.substringBeforeLast('.')
            val signature = token.substringAfterLast('.')
            val at = signature.length / 2
            val forged = "$body.${signature.replaceRange(at, at + 1, if (signature[at] == 'A') "B" else "A")}"
            val refused =
                client.post(Rides()) {
                    header(HttpHeaders.Authorization, "Bearer $forged")
                    contentType(ContentType.Application.Json)
                    setBody(request())
                }
            assertEquals(HttpStatusCode.Unauthorized, refused.status, "a broken signature was accepted")
        }
    }

    /**
     * The whole sign-in, through the module a browser would run.
     *
     * The two steps in the middle are a person — shildik serves the form and checks the password,
     * and the client under test never sees either.
     */
    private suspend fun signIn(issuer: String): String =
        HttpClient(CIO) { followRedirects = false }.use { browser ->
            val attempt =
                SignInAttempt.begin(
                    SignInConfig(issuer = issuer, realm = REALM, clientId = CLIENT, redirectUri = REDIRECT),
                )
            val page = browser.get(attempt.authorizeUrl())
            val parked = PARKED_STATE.find(page.bodyAsText())?.groupValues?.get(1)
            assertTrue(parked != null, "no parked state on the sign-in page")

            val redirect =
                browser.submitForm(
                    url = "$issuer/realms/$REALM/oauth2/login/password",
                    formParameters =
                        parameters {
                            append("state", parked)
                            append("login", EMAIL)
                            append("password", PASSWORD)
                        },
                ) {
                    header(HttpHeaders.Origin, issuer)
                    header(HttpHeaders.Referrer, "$issuer/realms/$REALM/oauth2/authorize")
                }
            val callback = Url(redirect.headers[HttpHeaders.Location]!!)
            val code = callback.parameters["code"]!!

            val tokens =
                browser.submitForm(
                    url = "$issuer/realms/$REALM/oauth2/token",
                    formParameters =
                        parameters {
                            attempt.tokenForm(code, callback.parameters["state"]!!).forEach { (k, v) -> append(k, v) }
                        },
                )
            Json
                .parseToJsonElement(tokens.bodyAsText())
                .jsonObject["access_token"]!!
                .jsonPrimitive
                .content
        }

    /**
     * A provider address nothing answers on.
     *
     * The point of the first test is that the refusal does not depend on one, so pointing at a
     * running shildik there would hide a validator that reached out before checking whether there
     * was anything to check.
     */
    private fun unreachableProvider() = OidcConfig(url = "http://127.0.0.1:1", realm = REALM, clientId = CLIENT)

    private fun ApplicationTestBuilder.typedClient(): HttpClient =
        createClient {
            install(Resources)
            install(ContentNegotiation) { json() }
        }

    private fun request() =
        RideRequest(
            riderId = "rider-1",
            pickup = GeoPoint(46.0511, 14.5051),
            dropoff = GeoPoint(46.2237, 14.4576),
            rideClass = RideClass.ECONOMY,
            paymentMethodId = "card-4417",
        )

    private companion object {
        const val ISSUER_VARIABLE = "SHASHKI_SHILDIK"
        const val REALM = "shashki"
        const val CLIENT = "rider"
        const val REDIRECT = "http://127.0.0.1:18080/callback"
        const val EMAIL = "rider@example.com"
        const val PASSWORD = "correct-horse-battery-staple"

        val PARKED_STATE = Regex("""name="state"[^>]*value="([^"]+)"""")
    }
}
