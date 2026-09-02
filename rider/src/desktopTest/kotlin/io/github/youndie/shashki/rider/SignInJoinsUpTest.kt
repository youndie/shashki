package io.github.youndie.shashki.rider

import io.github.youndie.shashki.auth.InMemoryTokenStore
import io.github.youndie.shashki.auth.SignInConfig
import io.github.youndie.shashki.protocol.GeoPoint
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.RideRequest
import io.github.youndie.shashki.protocol.RideView
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.rider.feature.auth.data.HttpTokenExchange
import io.github.youndie.shashki.rider.feature.auth.domain.Session
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **The join, against both real services.** B-41 exists because every piece of sign-in was proven and
 * nothing put them together: `SignInAttempt` against a live shildik, the server against a token it
 * was handed, and an application that called neither.
 *
 * So this is the application's own `Session` and its own `HttpTokenExchange`, driven through a real
 * provider, and the token it ends up holding used on a real `POST /api/rides`. The two steps in the
 * middle are a person — shildik serves the form and checks the password — and this test plays them,
 * exactly as `SignInAgainstShildikTest` does one layer down.
 *
 * What it cannot cover is the redirect itself: `redirectTo` is a browser and `sessionStorage` is a
 * browser, and neither exists on this target. The page's half was checked by hand against the stand.
 *
 * ```bash
 * ./gradlew :server:image -PosmFile=~/shashki-city/Ljubljana.osm.pbf -PcommitSha=local
 * SHASHKI_IMAGE=shashki/server:local docker compose -f docker/compose.yaml up -d
 * bash docker/bootstrap-shildik.sh
 * SHASHKI_SERVER=http://127.0.0.1:18080 SHASHKI_SHILDIK=http://127.0.0.1:18081 \
 *   ./gradlew :rider:desktopTest --tests '*SignInJoinsUp*'
 * ```
 */
class SignInJoinsUpTest {
    // **A block body and not `= runBlocking { … }`**, which is how this was written first and is why
    // it silently did not run: the last expression of the block is a value, so the method is
    // non-void, and JUnit does not collect a non-void method as a test. The build's own guard caught
    // it — "declares 1 @Test and reported nothing at all" — which is the difference between a test
    // that fails and a test that is not there.
    @Test
    fun `the application signs in and orders a ride with the token it got`() {
        runBlocking {
            val server = System.getenv(SERVER)
            val issuer = System.getenv(ISSUER)
            assumeTrue(!server.isNullOrBlank() && !issuer.isNullOrBlank(), "no $SERVER / $ISSUER: needs the stand")

            val store = InMemoryTokenStore()
            HttpClient(CIO) { followRedirects = false }.use { provider ->
                // The redirect is where a browser would be sent; here the test follows it itself.
                var authorize: String? = null
                val session = Session(store, config(issuer!!), HttpTokenExchange(provider)) { authorize = it }

                // 1. The application parks an attempt and would redirect. Here the test follows.
                session.begin()
                assertNotNull(store.parked(), "nothing parked: the redirect would lose the verifier")

                // 2. The person signs in on shildik's own page.
                val page = provider.get(assertNotNull(authorize, "the application did not send the browser anywhere"))
                val formState = assertNotNull(PARKED_STATE.find(page.bodyAsText())?.groupValues?.get(1))
                val redirect =
                    provider.submitForm(
                        url = "$issuer/realms/$REALM/oauth2/login/password",
                        formParameters =
                            parameters {
                                append("state", formState)
                                append("login", EMAIL)
                                append("password", PASSWORD)
                            },
                    ) {
                        header(HttpHeaders.Origin, issuer)
                        header(HttpHeaders.Referrer, "$issuer/realms/$REALM/oauth2/authorize")
                    }
                val callback = Url(assertNotNull(redirect.headers[HttpHeaders.Location]))
                assertEquals(
                    "http://127.0.0.1:18080/callback",
                    "${callback.protocol.name}://${callback.host}:${callback.port}${callback.encodedPath}",
                    "the redirect URI the provider sent back is not the one this application answers",
                )

                // 3. The application completes the callback — its own code, its own exchange.
                val signedIn =
                    session.complete(
                        code = assertNotNull(callback.parameters["code"]),
                        state = assertNotNull(callback.parameters["state"]),
                    )
                assertTrue(signedIn, "the exchange did not produce a token")
            }

            // 4. And the token is one this server accepts, on a route it refuses without one.
            HttpClient(CIO) {
                install(Resources)
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                defaultRequest {
                    url(server!!)
                    contentType(ContentType.Application.Json)
                }
                expectSuccess = false
            }.use { api ->
                val anonymous = api.post(Rides()) { setBody(REQUEST) }
                assertEquals(HttpStatusCode.Unauthorized, anonymous.status, "the stand is not protecting anything")

                val ordered =
                    api.post(Rides()) {
                        header(HttpHeaders.Authorization, "Bearer ${assertNotNull(store.token())}")
                        setBody(REQUEST)
                    }
                assertEquals(HttpStatusCode.Created, ordered.status, ordered.bodyAsText().take(200))
                assertNotNull(ordered.body<RideView>().id)
            }
        }
    }

    /** Rebuilt here because `authorizeUrl` needs the attempt, and the attempt is inside the session. */
    private suspend fun authorizeUrl(
        config: SignInConfig,
        state: String,
        verifier: String,
    ): String =
        io.github.youndie.shashki.auth.SignInAttempt
            .resume(
                config,
                io.github.youndie.shashki.auth
                    .ParkedAttempt(verifier, state, state),
            ).authorizeUrl()

    private fun config(issuer: String) =
        SignInConfig(issuer = issuer, realm = REALM, clientId = CLIENT, redirectUri = REDIRECT)

    private companion object {
        const val SERVER = "SHASHKI_SERVER"
        const val ISSUER = "SHASHKI_SHILDIK"
        const val REALM = "shashki"
        const val CLIENT = "rider"
        const val REDIRECT = "http://127.0.0.1:18080/callback"
        const val EMAIL = "rider@example.com"
        const val PASSWORD = "correct-horse-battery-staple"

        val PARKED_STATE = Regex("""name="state"[^>]*value="([^"]+)"""")

        val REQUEST =
            RideRequest(
                riderId = "rider-1",
                pickup = GeoPoint(46.0511, 14.5051),
                dropoff = GeoPoint(46.2237, 14.4576),
                rideClass = RideClass.ECONOMY,
                paymentMethodId = "card-4417",
            )
    }
}
