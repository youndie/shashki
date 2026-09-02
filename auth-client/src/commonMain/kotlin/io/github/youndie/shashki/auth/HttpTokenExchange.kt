package io.github.youndie.shashki.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The code exchange, against the provider rather than against this product's server.
 *
 * **A client of its own, and that is the one place this bundle has two.** The application's client
 * carries a bearer token and a base URL; this request goes to a different service, must not carry
 * the token being replaced, and happens once per sign-in. `CrashReporter` shares the application's
 * client for the opposite reason — it talks to a third service but often enough that a second
 * connection pool would be waste. One request per sign-in is not that.
 *
 * The address comes from `SignInConfig.tokenUrl()`, which builds it from shildik's own `@Resource`:
 * `:auth-client` has the resource classes and deliberately no HTTP client, so it hands over an
 * address and this hands over a transport.
 */
public class HttpTokenExchange(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TokenExchange {
    override suspend fun token(
        config: SignInConfig,
        form: Map<String, String>,
    ): String {
        val body =
            client
                .submitForm(
                    url = config.tokenUrl(),
                    formParameters = Parameters.build { form.forEach { (k, v) -> append(k, v) } },
                ).bodyAsText()
        return json
            .parseToJsonElement(body)
            .jsonObject["access_token"]
            ?.jsonPrimitive
            ?.content
            ?: error("the provider answered without an access token: ${body.take(200)}")
    }
}
