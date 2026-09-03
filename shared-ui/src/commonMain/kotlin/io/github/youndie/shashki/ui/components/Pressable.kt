package io.github.youndie.shashki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role

/**
 * A coloured surface a finger can press: **the click first, the colour under it** (B-82).
 *
 * **The kit's tilt was never missing — it was drawing inside the surface.** `KvadrantTheme` provides
 * it as `LocalIndication`, so every plain `Modifier.clickable` in this product was already asking
 * for it; but an indication draws what comes **after** it in the chain, and every pressable surface
 * here was written `.background(colour).clickable { }`. The colour was painted outside the
 * indication and stayed exactly where it was while the label tilted inside it — which on a 54 dp row
 * is nothing anybody can see.
 *
 * Measured on `ClassTile`, pressed and captured, comparing a resting frame with a held one: with the
 * click inside the background **8 of the tile's 36 corner pixels** move; with it outside, **35 of
 * 36**. `TilePressTest` is that measurement as a guard, and the order lives here rather than at
 * fourteen call sites, because getting it wrong is silent.
 *
 * The indication itself is deliberately **not** named: it is the theme's, and a surface that named
 * one would stop following the theme the day the theme changed its mind.
 */
@Composable
public fun Modifier.pressableSurface(
    colour: Color,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier = clickable(enabled = enabled, role = role, onClick = onClick).background(colour)
