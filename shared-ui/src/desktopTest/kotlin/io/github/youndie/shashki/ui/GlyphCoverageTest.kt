package io.github.youndie.shashki.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.youndie.shashki.ui.map.cityTile
import io.github.youndie.shashki.ui.map.tiles.labelText
import ru.workinprogress.viddik.core.ViddikGlyphCoverage
import ru.workinprogress.viddik.generated.GeneratedViddikRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Every string this project draws is drawn by a font this project ships.**
 *
 * A character no bundled face covers does not fail, and that is the whole problem: Skia resolves it
 * through the *host's* installed fonts, so the screenshot renders, the golden passes on the machine
 * that recorded it, and it reddens on the next one — or worse, quietly records one operating
 * system's idea of a glyph as this product's design. viddik's own notes measured 21 % pixel
 * mismatch from one such line. `₽` was this project's instance of it (research §1.2c) and was
 * answered by pricing in `$`; this is the guard for the next one.
 *
 * **It is built on the generated registry on purpose.** A helper each fixture remembers to call is
 * a guard that covers the fixtures written before it. `GeneratedViddikRegistry.components` is
 * written by viddik's own KSP processor from the `@ViddikScreenshot` annotations, so a fixture
 * added tomorrow is checked tomorrow, without anyone opting in.
 */
@OptIn(ExperimentalTestApi::class)
class GlyphCoverageTest {
    @Test
    fun `every string the fixtures draw is covered by a bundled face`() {
        val offences = mutableListOf<String>()
        val silent = mutableListOf<String>()
        val seen = mutableListOf<String>()
        for (component in GeneratedViddikRegistry.components) {
            runComposeUiTest {
                setContent { component.content() }
                val drawn = drawnText()
                seen += drawn
                if (drawn.isEmpty()) silent += "${component.group}/${component.name}"
                for (text in drawn) {
                    val missing = uncoveredIn(text)
                    if (missing.isNotEmpty()) {
                        offences += "${component.group}/${component.name}: \"$text\" — ${missing.render()}"
                    }
                }
            }
        }
        assertEquals(emptyList(), offences, "these would be drawn by whatever font the host happens to have")

        // **Per fixture, not in total.** A walk that silently returned nothing would make this test
        // green for every string in the project, and one fixture full of text is enough to hide
        // that from a check on the total. The two map tiles are text-free by construction — they
        // draw their labels onto the canvas, which is what the second test is for — so they are
        // named here rather than tolerated by a blanket rule.
        assertEquals(
            listOf("map/canvas tile dark", "map/canvas tile light"),
            silent,
            "a fixture drew no text at all: either it lost its labels, or the semantics walk is broken",
        )

        // And the walk reaches the characters this guard exists for. `č` is drawn by exactly one
        // fixture — the type ramp's `Miklošičeva cesta 4`, put there by B-06 — so its absence here
        // means the diacritics stopped being checked, not that they stopped being drawn.
        assertTrue(seen.any { 'č' in it }, "no fixture drew a diacritic, so nothing here tested coverage")
    }

    /**
     * The map's labels come from the tile rather than from a fixture, so no semantics node carries
     * them and the check above cannot see them — `drawTextOnPath` paints glyphs straight onto the
     * canvas. B-06 checked the whole extract's street alphabet by hand once; this keeps the tile
     * the goldens actually draw under the same rule as everything else.
     */
    @Test
    fun `every label the fixture tile draws is covered by a bundled face`() {
        val labels =
            cityTile.layers
                .flatMap { it.features }
                .mapNotNull { it.labelText() }
                .distinct()
        // **Pinned, and the pin says something uncomfortable.** All four are ASCII: this tile
        // carries no diacritic at all, so on its own it would prove nothing about a city whose
        // street names are full of them. The diacritics are checked by the test above, through the
        // type-ramp fixture that draws `Miklošičeva cesta 4` — and across the whole extract they
        // were checked once, by hand, in B-06. What this test adds is the only strings in the
        // project that come from data rather than from a literal, and a changed fixture tile that
        // quietly stopped carrying labels would fail here rather than pass silently.
        assertEquals(listOf("Voglje", "Vodice", "Torovo", "A2"), labels)
        val offences =
            labels.mapNotNull { label ->
                uncoveredIn(label).takeIf { it.isNotEmpty() }?.let { "\"$label\" — ${it.render()}" }
            }
        assertEquals(emptyList(), offences, "the tile carries characters no bundled face draws")
    }

    /**
     * The five Selawik weights are only interchangeable if they cover the same characters, and
     * [selawik] leans on that. Asserted rather than assumed, because a future release adding a
     * glyph to one weight would make the guard depend on which weight a fixture happened to pick.
     */
    @Test
    fun `the five Selawik weights cover the same characters`() {
        val byFace =
            listOf("light", "semilight", "regular", "semibold", "bold")
                .associateWith { ViddikGlyphCoverage.codepointsOf(face("selawik_$it.ttf")) }
        val reference = byFace.getValue("light")
        assertTrue(reference.size > 300, "a face with ${reference.size} glyphs is not the one B-06 measured")
        for ((name, covered) in byFace) {
            assertEquals(emptySet(), covered - reference, "selawik_$name draws characters light cannot")
            assertEquals(emptySet(), reference - covered, "selawik_$name is missing characters light has")
        }
    }

    /**
     * The control. Nothing in the product may use `✕`, so a deliberately broken string proves the
     * check fails rather than passing vacuously — the failure mode this whole item exists for is a
     * check that renders happily and reports nothing.
     */
    @Test
    fun `a character no bundled face carries is reported, with its codepoint`() {
        assertEquals(setOf(0x2715), uncoveredIn("cancel ✕"))
        assertEquals("U+2715", setOf(0x2715).render())
        // And the fixtures' own vocabulary passes the same call, so the control is not passing
        // because the checker rejects everything.
        assertTrue(uncoveredIn("Miklošičeva cesta 4 · $ 26 940 — 09/15 …").isEmpty())
    }
}

/**
 * Every string the composition would draw, read off the semantics tree — which is where Compose's
 * text ends up regardless of whether a fixture wrote `KvadrantText`, `BasicText` or a stock
 * component's own label.
 */
@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.drawnText(): List<String> {
    val found = mutableListOf<String>()

    fun walk(node: androidx.compose.ui.semantics.SemanticsNode) {
        node.config.getOrNull(SemanticsProperties.Text)?.forEach { found += it.text }
        node.config.getOrNull(SemanticsProperties.EditableText)?.let { found += it.text }
        node.children.forEach(::walk)
    }
    walk(onRoot().fetchSemanticsNode())
    return found
}

/**
 * Which characters of [text] no bundled face can draw, under the rule `KvadrantText` actually
 * applies: Cyrillic and `U+25CF` go to the Source Sans companion, everything else stays on Selawik.
 * A character that falls out of the face it is routed to is drawn by the host.
 *
 * Selawik is checked through one weight, which is only sound because a separate test holds the five
 * to identical coverage. A string that only some weights could draw would be a string whose
 * rendering depends on which weight the fixture picked — the same silent dependency in a smaller box.
 */
private fun uncoveredIn(text: String): Set<Int> {
    val (companion, primary) = text.partition { it.needsCompanion() }
    return ViddikGlyphCoverage.missingGlyphs(primary, selawik) +
        ViddikGlyphCoverage.missingGlyphs(companion, sourceSans)
}

/** `KvadrantText.needsCompanion`, which is `internal` there. Cyrillic, plus the mask circle. */
private fun Char.needsCompanion(): Boolean = this in 'Ѐ'..'ӿ' || this == '●'

private fun Set<Int>.render(): String = sorted().joinToString(", ") { "U+%04X".format(it) }

/**
 * One Selawik face stands for all five, and the test above is what makes that legitimate: it fails
 * if their coverage ever diverges. Light is the one the fixtures draw most of their text in.
 */
private val selawik: ByteArray by lazy { face("selawik_light.ttf") }

private val sourceSans: ByteArray by lazy { face("source_sans_3_variable.ttf") }

private fun face(name: String): ByteArray =
    checkNotNull(
        GlyphCoverageTest::class.java.classLoader
            .getResourceAsStream("composeResources/io.github.youndie.kvadrant.resources/font/$name"),
    ) { "$name is not on the test classpath — kvadrant-core moved its resources" }
        .use { it.readBytes() }
