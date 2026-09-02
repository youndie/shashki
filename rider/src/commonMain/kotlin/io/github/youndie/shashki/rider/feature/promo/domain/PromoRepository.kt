package io.github.youndie.shashki.rider.feature.promo.domain

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.shashki.rider.UseCase
import io.github.youndie.shashki.rider.suspendRunCatching

/** The one screen this client does not draw: it asks for it. */
public interface PromoRepository {
    public suspend fun promo(): KompotComponent
}

/**
 * Fetch it.
 *
 * A use case for one call, which the project's rule requires and which earns itself here for a
 * different reason than usual: **this is the only place in the client where a failure means "no
 * screen"** rather than "a value is missing", and having it in the domain layer is what stops a
 * composable deciding what that looks like.
 */
public class LoadPromoUseCase(
    private val promos: PromoRepository,
) : UseCase<Unit, KompotComponent> {
    override suspend fun invoke(params: Unit): Result<KompotComponent> = suspendRunCatching { promos.promo() }
}
