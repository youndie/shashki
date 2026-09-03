package io.github.youndie.shashki.ui.kompot

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.KompotSurface
import io.github.youndie.kompot.SurfaceRole
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.protocol.ShashkiTokens
import io.github.youndie.shashki.ui.ShashkiTheme

/**
 * What a name the server sent looks like here.
 *
 * **This is the whole of the boundary, and it is one direction.** The server names a role and never a
 * value: there is no colour on the wire, no size, no font. So a tree from a backend that has never
 * heard of this kit still renders in this kit — and a backend cannot paint an unreadable screen,
 * because it has no way to say what a colour is.
 *
 * **A token nobody knows falls back rather than failing.** kompot's own §6 requires it, and it is
 * what lets a server that has moved on address a client that has not: an unknown typography token
 * draws in `body`, an unknown colour in the foreground. The alternative — throwing — would make a
 * screen disappear because one word asked for a name this build predates.
 */
public object ShashkiDesignSystem : KompotDesignSystem {
    @Composable
    override fun resolveColor(token: ColorToken): Color {
        val colors = KvadrantTheme.colors
        return when (token.key) {
            ShashkiTokens.COLOR_BACKGROUND -> colors.background
            ShashkiTokens.COLOR_FOREGROUND -> colors.foreground
            ShashkiTokens.COLOR_SUBTLE -> colors.subtle
            ShashkiTokens.COLOR_ACCENT -> colors.accent
            ShashkiTokens.COLOR_ON_ACCENT -> colors.onAccent
            ShashkiTokens.COLOR_CHROME -> colors.chrome
            else -> colors.foreground
        }
    }

    /**
     * What a name the server sent is set in — **and in this kit's ink, always**.
     *
     * **A style with no colour is not neutral, it is Material's.** kompot's `resolveTextColor` uses
     * the component's colour token if it has one, then the style's own colour, and if neither is set
     * falls back to `MaterialTheme.colorScheme.onSurface` — read out of `ComponentsKt` rather than
     * guessed. This kit is not Material, so on the dark theme that fallback is `#1D1B20` on black:
     * **1.23:1, which is a heading nobody can see.** The promo screen's title had been drawn that
     * way since B-32 and its golden had been photographing it (B-61 measured the PNG and found it).
     *
     * So the ink is filled in here, at the boundary, rather than asked of every server: a tree may
     * still name a colour and override it, and one that names none is readable by construction.
     */
    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle {
        val type = ShashkiTheme.typography
        val style =
            when (token.key) {
                ShashkiTokens.TYPE_PAGE_TITLE -> type.pageTitle
                ShashkiTokens.TYPE_FIGURE -> type.figure
                ShashkiTokens.TYPE_STATE_HEADLINE -> type.stateHeadline
                ShashkiTokens.TYPE_TILE_LABEL -> type.tileLabel
                ShashkiTokens.TYPE_BODY -> type.body
                ShashkiTokens.TYPE_META -> type.meta
                else -> type.body
            }
        return if (style.color.isSpecified) style else style.copy(color = KvadrantTheme.colors.foreground)
    }

    /**
     * What a control draws for itself. **The kit has square corners and the toolkit assumes rounded
     * ones**, so a button left to its defaults is a Material button in a Metro screen — which is the
     * one thing a design system has to be able to say and could not, until kompot added this hook.
     */
    @Composable
    override fun resolveSurface(role: SurfaceRole): KompotSurface {
        val colors = KvadrantTheme.colors
        val type = ShashkiTheme.typography
        return when {
            role.key.startsWith("button") -> {
                KompotSurface(
                    shape = RectangleShape,
                    container = colors.accent,
                    content = colors.onAccent,
                    outline = Color.Transparent,
                    textStyle = type.body,
                )
            }

            role.key == "container" -> {
                KompotSurface(shape = RectangleShape, container = colors.chrome)
            }

            else -> {
                KompotSurface(shape = RectangleShape)
            }
        }
    }
}
