package io.github.youndie.shashki.driver.feature.earnings.data

import io.github.youndie.shashki.driver.DriverIdentity
import io.github.youndie.shashki.driver.feature.earnings.domain.EarningsRepository
import io.github.youndie.shashki.protocol.DriverEarnings
import io.github.youndie.shashki.protocol.EarningsView
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get

/**
 * `GET /api/driver/earnings`.
 *
 * The id travels only because a server with no provider has no other way to know who is asking
 * (B-52); with a token it is ignored, and the number that comes back is the token's driver's.
 */
public class HttpEarningsRepository(
    private val client: HttpClient,
    private val identity: DriverIdentity,
) : EarningsRepository {
    override suspend fun earnings(): EarningsView = client.get(DriverEarnings(driverId = identity.current())).body()
}
