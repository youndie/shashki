package io.github.youndie.shashki.server.feature.auth

import org.slf4j.LoggerFactory
import ru.workinprogress.oidc.OidcConfig

/**
 * Where the identity provider is, or the fact that there is none.
 *
 * **`null` is a running configuration and not a failure**, which is why this returns one rather than
 * throwing: the saga tests, the golden suite and a demo of the map all run without anybody signing
 * in, and a server that refused to start without a provider would make every one of them need one.
 *
 * It is logged at `warn` for the same reason the missing OSM extract is: a server whose rider routes
 * are open is a server that looks exactly like a working one.
 */
public object AuthConfig {
    public const val ISSUER_VARIABLE: String = "SHASHKI_OIDC_ISSUER"
    public const val REALM_VARIABLE: String = "SHASHKI_OIDC_REALM"
    public const val CLIENT_VARIABLE: String = "SHASHKI_OIDC_CLIENT"

    private val LOG = LoggerFactory.getLogger(AuthConfig::class.java)

    public fun fromEnv(env: (String) -> String? = System::getenv): OidcConfig? {
        val issuer = env(ISSUER_VARIABLE)?.takeIf { it.isNotBlank() }
        if (issuer == null) {
            LOG.warn(
                "no {}: the rider routes are open, so anybody who can reach this server can order a car",
                ISSUER_VARIABLE,
            )
            return null
        }
        return OidcConfig(
            url = issuer,
            realm = env(REALM_VARIABLE)?.takeIf { it.isNotBlank() } ?: DEFAULT_REALM,
            clientId = env(CLIENT_VARIABLE)?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT,
        )
    }

    private const val DEFAULT_REALM = "shashki"
    private const val DEFAULT_CLIENT = "rider"
}
