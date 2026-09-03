package io.github.youndie.shashki.server.feature.receipt

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.encodeKompotComponent
import io.github.youndie.kompot.generated.generatedShashkiProtocolSerializersModule
import io.github.youndie.kompot.generated.generatedStandardSerializersModule
import io.github.youndie.kompot.kompotCoreSerializersModule
import io.github.youndie.kompot.standard.kompotStandardSerializersModule
import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.server.feature.receipt.domain.ReceiptScreenUseCase
import io.ktor.http.ContentType
import io.ktor.server.resources.get
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import org.koin.ktor.ext.inject

/**
 * `GET /api/rides/{id}/receipt` — R9·b as a tree (B-61).
 *
 * **Auth tier: whatever the rider's other routes have**, which is the token when a provider is
 * configured. It is declared inside `riderRoutes` rather than beside the promo screen for exactly
 * that reason: a promotion is what an anonymous visitor is shown and a receipt is somebody's own
 * money. What a token does *not* yet decide is **which** ride is yours — B-09's remaining half, and
 * the same limit `GET /api/rides/{id}` carries today.
 *
 * **The generated serializers module is `:protocol`'s.** The components are declared there so that
 * this server can build one at all (B-65), and the half of the registry that names them is
 * generated beside them. `:shared-ui`'s half — the renderers — is the client's and could not be
 * linked here even if it were wanted.
 */
public fun Route.receiptRoutes() {
    val receipt by inject<ReceiptScreenUseCase>()

    get<Rides.Receipt> { route ->
        call.respondText(encodedReceipt(receipt(route.id).getOrThrow()), ContentType.Application.Json)
    }
}

/**
 * kompot's own `Json`, as the promo route has it and for the same two reasons: `classDiscriminator =
 * "type"` would change how every other route serialises if it were installed globally, and
 * `encodeKompotComponent` rather than a reified `encodeToString` because the polymorphic bases are
 * plain interfaces — a reified call resolves by reflection on the JVM and throws where there is
 * none.
 *
 * **Four modules and not three.** `kompotStandardSerializersModule` carries the standard module's
 * *contextual* serializers; what registers `ColumnComponent` under the polymorphic base is
 * `generatedStandardSerializersModule`, kompot's own generated half. Leaving it out encodes nothing
 * and throws "serializer for subclass 'ColumnComponent' is not found" — at the first request, not at
 * compile time.
 */
internal fun encodedReceipt(root: KompotComponent): String = RECEIPT_JSON.encodeKompotComponent(root)

private val RECEIPT_JSON =
    Json {
        classDiscriminator = "type"
        serializersModule =
            kompotCoreSerializersModule + kompotStandardSerializersModule +
            generatedStandardSerializersModule + generatedShashkiProtocolSerializersModule
    }
