package io.github.youndie.shashki.ui.kompot

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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

    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle {
        val type = ShashkiTheme.typography
        return when (token.key) {
            ShashkiTokens.TYPE_PAGE_TITLE -> type.pageTitle
            ShashkiTokens.TYPE_FIGURE -> type.figure
            ShashkiTokens.TYPE_STATE_HEADLINE -> type.stateHeadline
            ShashkiTokens.TYPE_TILE_LABEL -> type.tileLabel
            ShashkiTokens.TYPE_BODY -> type.body
            ShashkiTokens.TYPE_META -> type.meta
            else -> type.body
        }
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
