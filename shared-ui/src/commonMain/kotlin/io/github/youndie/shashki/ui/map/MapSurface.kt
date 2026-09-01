package io.github.youndie.shashki.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiTheme

/**
 * The one seam between this product's screens and whatever draws the map.
 *
 * **This interface is B-01's decision made early, on purpose.** Which of the four routes to a
 * browser wins — desktop-first, wasm on an unreleased Compose, maplibre-gl-js in the DOM under a
 * transparent Compose canvas, or a renderer of our own on Compose Canvas — is not decided here and
 * does not have to be, because every one of them can implement `Map(scene, modifier)`. What the
 * choice must not be allowed to become is a rewrite of eighteen screens, and a screen that names
 * this interface instead of a library is a screen the choice cannot reach.
 *
 * The implementations differ in a way the signature already admits: routes 1, 3 and WorldWind draw
 * *behind* or *beside* Compose and can only honour `modifier` approximately — the DOM canvas is
 * full-window and says so in its own KDoc — while route 4 draws inside the caller's bounds like any
 * other composable. That is a real difference and it is a reason to pick, not a reason for two
 * interfaces.
 */
public interface MapSurface {
    @Composable
    public fun Map(
        scene: MapScene,
        modifier: Modifier,
    )
}

/**
 * Provided by whatever assembles the application. **No default**, for the reason
 * `LocalShashkiTypography` has none: a default that quietly draws nothing would let a screen render,
 * and a golden of it pass, while the map — the thing the screen is mostly made of — is missing.
 */
public val LocalMapSurface: ProvidableCompositionLocal<MapSurface> =
    staticCompositionLocalOf {
        error("no MapSurface in composition: the application must provide one — see B-01 for which")
    }

/** `MapSurface.Map` at the call site, so a screen reads as a screen. */
@Composable
public fun MapPane(
    scene: MapScene,
    modifier: Modifier = Modifier,
) {
    LocalMapSurface.current.Map(scene, modifier)
}

/**
 * A map-shaped hole, for the screens that exist before a renderer does.
 *
 * **It is not a stub that pretends.** It paints the basemap's own background colour — the one the
 * style documents open with — and says in the middle of itself that no renderer is bound, so a
 * screenshot of a screen using it is obviously a screen without a map rather than a screen whose map
 * failed to load. That distinction is the whole reason it is not simply an empty `Box`.
 */
public class PlaceholderMapSurface(
    private val label: String = "no map renderer bound — B-01",
) : MapSurface {
    @Composable
    override fun Map(
        scene: MapScene,
        modifier: Modifier,
    ) {
        val ground = if (KvadrantTheme.colors.isDark) DARK_GROUND else LIGHT_GROUND
        Box(modifier.background(ground), contentAlignment = Alignment.Center) {
            KvadrantText(
                label,
                Modifier.padding(KvadrantTheme.metrics.margin),
                style = ShashkiTheme.typography.meta.copy(color = KvadrantTheme.colors.subtle),
            )
        }
    }

    private companion object {
        /** `background-color` of `shashki-map-dark.json` and of its light twin. */
        val DARK_GROUND = Color(0xFF0A0A0A)
        val LIGHT_GROUND = Color(0xFFEFEFEF)
    }
}

/** Everything a scene needs when there is nothing to show yet: the city, and no route. */
public fun emptyScene(camera: MapCamera): MapScene = MapScene(camera = camera)

/** The map's own composable, wrapped so a fixture can fill the screen with it. */
@Composable
public fun FullBleedMap(
    scene: MapScene,
    modifier: Modifier = Modifier,
) {
    MapPane(scene, modifier.fillMaxSize())
}
