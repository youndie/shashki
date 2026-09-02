package io.github.youndie.shashki.driver.feature.earnings.domain

import io.github.youndie.shashki.driver.UseCase
import io.github.youndie.shashki.driver.suspendRunCatching
import io.github.youndie.shashki.protocol.EarningsView

/** What the driver has earned, as this bundle asks for it (B-46). */
public interface EarningsRepository {
    public suspend fun earnings(): EarningsView
}

/** D6's three numbers. A read, not a loop: a shift's takings change when a ride ends, not per second. */
public class ReadEarningsUseCase(
    private val earnings: EarningsRepository,
) : UseCase<Unit, EarningsView> {
    override suspend fun invoke(params: Unit): Result<EarningsView> = suspendRunCatching { earnings.earnings() }
}
