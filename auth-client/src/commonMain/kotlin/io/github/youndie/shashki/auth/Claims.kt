package io.github.youndie.shashki.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A claim out of a token, for putting on a screen (B-45).
 *
 * **This does not verify anything and must never be used as though it did.** A JWT's payload is
 * base64 in the middle of the string and reading it is three lines; deciding whether the signature is
 * ours is the server's job, done once, in shildik's verifier — a client that checked signatures would
 * be the second implementation of that question, which is the thing this product refuses to have.
 *
 * What it is for: R9's profile page shows the address the rider signed in with. The consequence of
 * getting it wrong is a wrong label, and the consequence of not having it is a screen that knows who
 * somebody is and will not say.
 */
public fun emailClaim(token: String): String? = claim(token, "email")

/**
 * The subject — who the server will say this request is from.
 *
 * **The driver bundle needs this one for a reason the e-mail did not have**: after
 * [B-52](../../../../../../../docs/backlog/B-52-driver-routes-behind-the-token.md) the identity of
 * every driver request is the token's subject, and a client that goes on claiming a configured id
 * has its position frames dropped by the socket — measured, in B-53. The same caveat applies as
 * above: this reads, it does not verify, and the server decides.
 */
public fun subjectClaim(token: String): String? = claim(token, "sub")

@OptIn(ExperimentalEncodingApi::class)
private fun claim(
    token: String,
    name: String,
): String? {
    val payload = token.split(".").getOrNull(1) ?: return null
    val json =
        runCatching {
            // Base64url without padding, which is what a JWT uses and what `withPadding` allows for.
            Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(payload)
                .decodeToString()
        }.getOrNull() ?: return null
    val claims = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
    return claims[name]?.jsonPrimitive?.content
}
