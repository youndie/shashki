package io.github.youndie.shashki.ui.kompot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.generated.generatedShashkiProtocolSerializersModule
import io.github.youndie.kompot.generated.generatedShashkiUiRenderers
import io.github.youndie.kompot.kompotCoreRenderers
import io.github.youndie.kompot.kompotJson
import io.github.youndie.kompot.kompotStandardRenderers
import io.github.youndie.kvadrant.theme.KvadrantTheme
import kotlinx.serialization.json.Json

/**
 * A tree the server sent, drawn in this kit.
 *
 * **Three registries, and they say which side owns what.** kompot's core and standard components —
 * column, row, text, button, table — are the vocabulary the toolkit supplies; shashki's three are the
 * product's, and they are the ones the kit's composition rules apply to. A component in none of them
 * is drawn as kompot's `UnknownComponent` and the screen around it keeps going: that is the property
 * a server-driven screen exists to demonstrate, and it is why this screen has no native version to
 * fall back to.
 */
@Composable
public fun ServerScreen(
    root: KompotComponent,
    modifier: Modifier = Modifier,
    onAction: KompotActionHandler = KompotActionHandler { },
) {
    val registry =
        remember { KompotRegistry(kompotCoreRenderers, kompotStandardRenderers, generatedShashkiUiRenderers) }
    // The three components carry no fields, so the controller has nothing to control; an empty schema
    // is the smallest honest way to satisfy a signature the toolkit shares with its forms module.
    val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }

    CompositionLocalProvider(
        LocalKompotDesignSystem provides ShashkiDesignSystem,
        LocalAccentBudget provides AccentBudget(),
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(KvadrantTheme.colors.background)
                .padding(KvadrantTheme.metrics.margin),
        ) {
            KompotScreen(root, registry, forms, onAction)
        }
    }
}

/**
 * The one JSON both sides speak.
 *
 * kompot's engine module plus this product's own components — a tree is decoded by
 * `classDiscriminator = "type"` and an unknown type becomes `UnknownComponent` rather than an
 * exception, which is the degradation the screen above then draws around.
 */
public fun shashkiKompotJson(): Json = kompotJson(generatedShashkiProtocolSerializersModule)
