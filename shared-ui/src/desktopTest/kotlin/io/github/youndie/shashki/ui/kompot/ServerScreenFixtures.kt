package io.github.youndie.shashki.ui.kompot

import androidx.compose.runtime.Composable
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kvadrant.foundation.kvadrantLatin
import io.github.youndie.shashki.ui.RiderTheme
import io.github.youndie.shashki.ui.ShashkiTypography
import io.github.youndie.shashki.ui.portable
import ru.workinprogress.viddik.annotations.ViddikScreenshot

/**
 * A screen the server owns, drawn in this kit.
 *
 * **The fixtures decode JSON rather than build a tree**, because JSON is what arrives. A tree
 * assembled in Kotlin would exercise the renderers and skip the half that actually breaks: the
 * discriminator, the polymorphic module, and what happens to a name this build has never seen.
 *
 * The server's own tree is pinned by `PromoTreeTest` on the other side. These are not a copy of it —
 * they are the smallest trees that make the two properties visible.
 */
@ViddikScreenshot(name = "a tree the server sent", group = "bdui", width = 390, height = 400)
@Composable
internal fun ATreeTheServerSent() {
    Fixture(KNOWN_TREE)
}

/**
 * **The property a server-driven screen exists to demonstrate.**
 *
 * The middle component is a type this build does not know — a newer server, an older bundle. kompot
 * decodes it to `UnknownComponent` and the screen draws everything around it. The alternative, which
 * is what a naive client does, is a blank page: one unfamiliar node takes the whole response down.
 *
 * The hole is silent by design and that is a real cost, not a virtue — kompot's own
 * `KompotDegradationSink` exists because "a hole is reported by nobody", and this application binds
 * none yet.
 */
@ViddikScreenshot(name = "a component this build does not know", group = "bdui", width = 390, height = 400)
@Composable
internal fun AComponentThisBuildDoesNotKnow() {
    Fixture(TREE_WITH_AN_UNKNOWN_COMPONENT)
}

@Composable
private fun Fixture(json: String) {
    val latin = kvadrantLatin()
    RiderTheme(latin = latin, typography = ShashkiTypography.of(latin).portable()) {
        ServerScreen(shashkiKompotJson().decodeKompotComponent(json))
    }
}

private const val KNOWN_TREE = """
{
  "type": "column",
  "id": "root",
  "spacing": 16,
  "children": [
    { "type": "text", "id": "a", "text": "first ride on us", "style": "page_title" },
    { "type": "text", "id": "b", "text": "half of every fare", "style": "figure", "color": "accent" },
    { "type": "text", "id": "c", "text": "applied when the ride finishes", "style": "body", "color": "subtle" },
    { "type": "button", "id": "d", "text": "order a car", "action": { "type": "navigate", "deeplink": "shashki://rides" } }
  ]
}
"""

private const val TREE_WITH_AN_UNKNOWN_COMPONENT = """
{
  "type": "column",
  "id": "root",
  "spacing": 16,
  "children": [
    { "type": "text", "id": "a", "text": "first ride on us", "style": "page_title" },
    { "type": "shashki.countdown_ring", "id": "b", "seconds": 15 },
    { "type": "text", "id": "c", "text": "applied when the ride finishes", "style": "body", "color": "subtle" },
    { "type": "button", "id": "d", "text": "order a car", "action": { "type": "navigate", "deeplink": "shashki://rides" } }
  ]
}
"""
