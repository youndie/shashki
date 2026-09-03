package io.github.youndie.shashki.server.feature.receipt

import io.github.youndie.shashki.protocol.Quote
import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.protocol.ShashkiTokens
import io.github.youndie.shashki.server.feature.receipt.domain.NoReceiptException
import io.github.youndie.shashki.server.feature.receipt.domain.ReceiptRepository
import io.github.youndie.shashki.server.feature.receipt.domain.ReceiptScreenUseCase
import io.github.youndie.shashki.server.feature.receipt.domain.SettledRide
import io.github.youndie.shashki.server.feature.receipt.domain.receiptTree
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * R9·b, read off the wire (B-61).
 *
 * **The same posture as `PromoTreeTest` and for the same reason**: what the browser parses is JSON,
 * and what a server-driven screen can go wrong about is a name. A component type the client does not
 * register draws as `UnknownComponent`; a token it cannot resolve falls back to a default and is
 * never reported. Neither is a compile error on either side, so both are asserted here.
 *
 * **And the money.** The figure on this card is what two settlements charged, so it is exactly the
 * thing no client may compute — these tests hold the server to that arithmetic.
 */
class ReceiptTreeTest {
    @Test
    fun `the figure is both charges and the lines name them`() {
        val tree = Json.parseToJsonElement(encodedReceipt(receiptTree(COMPLETED)))

        val card = tree.card()
        assertEquals("$ 32.10", card.string("amount"), "the fare and the tip, added by the server")
        assertEquals("economy · 26.3 km · 20 min", card.string("caption"))
        assertTrue(card["primary"]?.let { assertIs<JsonPrimitive>(it).content } == "true")
        assertContentEquals(
            listOf("fare" to "$ 29.10", "tip" to "$ 3", "paid with" to "card-1"),
            card.lines(),
        )
    }

    /**
     * **A cancelled ride says both numbers.** Showing only what was taken leaves a rider working out
     * a quarter of a fare they were never told, and the fee is a quarter *of that fare*.
     */
    @Test
    fun `a cancellation shows the fare it would have been as well as the fee`() {
        val card = Json.parseToJsonElement(encodedReceipt(receiptTree(CANCELLED))).card()

        assertEquals("$ 7.28", card.string("amount"))
        assertTrue(card.string("caption").endsWith("· cancelled"), card.string("caption"))
        assertContentEquals(
            listOf("quoted fare" to "$ 29.10", "cancellation fee" to "$ 7.28", "paid with" to "card-1"),
            card.lines(),
        )
    }

    /** The three types on this wire, and every one of them is registered on the other side. */
    @Test
    fun `every node on the wire is a type the client registers`() {
        val types = Json.parseToJsonElement(encodedReceipt(receiptTree(COMPLETED))).collect("type")

        assertEquals(setOf("column", "text", "shashki.fare_breakdown"), types)
    }

    @Test
    fun `every token the server names is one the client can resolve`() {
        val tree = Json.parseToJsonElement(encodedReceipt(receiptTree(COMPLETED)))

        val used = tree.collect("style") + tree.collect("color")

        assertTrue(used.isNotEmpty(), "the tree names no tokens at all; this test would pass over nothing")
        assertEquals(emptySet(), used - (TYPOGRAPHY + COLOURS), "these would fall back to the default on the client")
    }

    /**
     * **A ride nobody has settled has no receipt, and says so with a 404 rather than an empty card.**
     * An empty breakdown would be a receipt for nothing, which is worse than an address that is not
     * there yet.
     */
    @Test
    fun `an unsettled ride has no receipt`() =
        runTest {
            val useCase =
                ReceiptScreenUseCase(
                    object : ReceiptRepository {
                        override suspend fun settled(rideId: String): SettledRide? = null
                    },
                )

            assertIs<NoReceiptException>(useCase("ride-1").exceptionOrNull())
        }

    private fun JsonElement.card(): JsonObject =
        assertIs<JsonArray>(assertIs<JsonObject>(this)["children"])
            .map { assertIs<JsonObject>(it) }
            .single { it.string("type") == "shashki.fare_breakdown" }

    private fun JsonObject.string(key: String): String = assertIs<JsonPrimitive>(this[key]).content

    private fun JsonObject.lines(): List<Pair<String, String>> =
        assertIs<JsonArray>(this["lines"]).map { assertIs<JsonObject>(it) }.map {
            it.string("label") to
                it.string("value")
        }

    private fun JsonElement.collect(key: String): Set<String> =
        when (this) {
            is JsonObject -> {
                entries.flatMapTo(mutableSetOf()) { (name, value) ->
                    val here =
                        if (name == key && value is JsonPrimitive &&
                            value.isString
                        ) {
                            setOf(value.content)
                        } else {
                            emptySet()
                        }
                    here + value.collect(key)
                }
            }

            is JsonArray -> {
                flatMapTo(mutableSetOf()) { it.collect(key) }
            }

            else -> {
                emptySet()
            }
        }

    private companion object {
        val QUOTE = Quote(distanceMetres = 26_300, durationSeconds = 1_200, amountCents = 2_910, currency = "USD")

        val COMPLETED =
            SettledRide(
                rideId = "ride-1",
                rideClass = RideClass.ECONOMY,
                quote = QUOTE,
                chargedCents = 2_910,
                cancelled = false,
                tipCents = 300,
                paymentMethodId = "card-1",
            )

        val CANCELLED = COMPLETED.copy(chargedCents = 728, cancelled = true, tipCents = 0)

        val TYPOGRAPHY =
            setOf(
                ShashkiTokens.TYPE_PAGE_TITLE,
                ShashkiTokens.TYPE_FIGURE,
                ShashkiTokens.TYPE_STATE_HEADLINE,
                ShashkiTokens.TYPE_TILE_LABEL,
                ShashkiTokens.TYPE_BODY,
                ShashkiTokens.TYPE_META,
            )

        val COLOURS =
            setOf(
                ShashkiTokens.COLOR_BACKGROUND,
                ShashkiTokens.COLOR_FOREGROUND,
                ShashkiTokens.COLOR_SUBTLE,
                ShashkiTokens.COLOR_ACCENT,
                ShashkiTokens.COLOR_ON_ACCENT,
                ShashkiTokens.COLOR_CHROME,
            )
    }
}
