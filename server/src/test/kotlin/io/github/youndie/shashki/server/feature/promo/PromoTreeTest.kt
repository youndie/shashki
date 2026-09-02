package io.github.youndie.shashki.server.feature.promo

import io.github.youndie.shashki.protocol.ShashkiTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The tree is read off the wire, not off the Kotlin.**
 *
 * What the client receives is JSON, and what it can go wrong about is a name: a token this server
 * invents resolves to a fallback on the other side and renders in the wrong style, silently and
 * forever. So this walks the encoded document rather than the object graph — the same bytes the
 * browser parses.
 */
class PromoTreeTest {
    private val tree: JsonElement = Json.parseToJsonElement(encodedPromoTree())

    /**
     * The guard that matters. `ShashkiTokens` is the vocabulary both sides agree on and it lives in
     * `:protocol` for that reason; a token outside it is not a compile error anywhere, because
     * kompot's tokens are open strings on purpose.
     */
    @Test
    fun `every token the server names is one the client can resolve`() {
        val known = TYPOGRAPHY + COLOURS

        val used = tree.collect("style") + tree.collect("color")

        assertTrue(used.isNotEmpty(), "the tree names no tokens at all; this test would pass over nothing")
        assertEquals(emptySet(), used - known, "these would fall back to the default on the client")
    }

    /**
     * **`navigate` is in this set and belongs there.** `type` is the discriminator for actions as
     * well as components — they share one open hierarchy and one registration module — so a client
     * that knew every component and not the action would render the button and do nothing when it
     * was pressed. Pinning both together is what makes this assertion about the wire rather than
     * about the component list.
     */
    @Test
    fun `every node and action on the wire is a type the client registers`() {
        val types = tree.collect("type")

        assertEquals(setOf("column", "text", "button", "navigate"), types)
    }

    /**
     * kompot forbids `http` in a `navigate` deeplink precisely so a server cannot walk somebody out
     * of the application through an ordinary transition. The rider's handler leans on that, so the
     * assumption is checked where it is made rather than trusted.
     */
    @Test
    fun `the action is an internal deeplink and not a way out of the application`() {
        val deeplinks = tree.collect("deeplink")

        assertEquals(setOf("shashki://rides"), deeplinks)
        assertTrue(deeplinks.none { it.startsWith("http") })
    }

    /** Every string value under [key], anywhere in the document. */
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
