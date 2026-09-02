package io.github.youndie.shashki.ui.kompot

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.generated.generatedShashkiUiRenderers
import io.github.youndie.kompot.generated.generatedShashkiUiSerializersModule
import io.github.youndie.kompot.kompotJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **The server-driven subset registers itself, and this is what says so.**
 *
 * B-17's second criterion is that the three components appear in the generated registry. Reading the
 * generated file once would have shown it; this fails when it stops being true — which is the
 * difference that matters, because the registration is a KSP side effect and a component that lost
 * its annotation would compile, render locally, and be an `UnknownComponent` on the wire.
 */
class GeneratedRegistryTest {
    @Test
    fun `all three components and all three renderers are in the generated registry`() {
        assertEquals(
            setOf(TripRow::class, FareBreakdown::class, EarningsTile::class),
            generatedShashkiUiRenderers.keys,
            "the renderer map is not the three this module declares",
        )
    }

    /**
     * The registration is only worth anything if it makes the wire work, so the check is a decode
     * rather than a look at a map: `kompotJson` with this module's own serializers module must turn
     * the server's `type` discriminator back into the Kotlin class.
     */
    @Test
    fun `a tree the server sent decodes into this module's components`() {
        val json = kompotJson(generatedShashkiUiSerializersModule)

        val tile =
            json.decodeFromString<KompotComponent>(
                """{"type":"shashki.earnings_tile","id":"today","label":"today","figure":"$ 128","size":2}""",
            )

        assertIs<EarningsTile>(tile)
        assertEquals(2, tile.size)
    }

    /**
     * And the control: without this module's registrations the same payload decodes to kompot's
     * `UnknownComponent` rather than throwing. That is the toolkit's degradation working — and it is
     * why the test above is about the registry rather than about serialization in general.
     */
    @Test
    fun `without the registration the same payload becomes an unknown component`() {
        val plain: Json = kompotJson()

        val decoded =
            plain.decodeFromString<KompotComponent>(
                """{"type":"shashki.earnings_tile","id":"today","label":"today","figure":"$ 128","size":2}""",
            )

        assertTrue(decoded !is EarningsTile, "the payload decoded without this module's registry")
    }
}
