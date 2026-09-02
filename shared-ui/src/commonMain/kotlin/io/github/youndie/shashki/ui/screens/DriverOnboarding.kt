package io.github.youndie.shashki.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiColors
import io.github.youndie.shashki.ui.ShashkiTheme

/** One document, as the screen receives it: what it is, where it has got to, and what to say about it. */
public data class OnboardingDocument(
    val title: String,
    val meta: String,
    val state: OnboardingState,
)

/** The kit's three states for a document, in the order a document passes through them. */
public enum class OnboardingState { MISSING, PENDING, ACCEPTED }

/**
 * D1: the three documents a driver hands over before they can drive (B-47).
 *
 * **The semantic colours are the kit's and they are the only colour on the screen** — amber for
 * pending, green for accepted, and the inactive brush for a document nobody has uploaded. The
 * driver's accent is amber, which is why pending is *the same* amber: a document being looked at is
 * the thing this screen is about, and the kit spends its one accent on it.
 *
 * **The upload field stays light in both themes.** It is one of the two places the kit says fight
 * that instinct — a field is a sheet of paper in Metro, and it is white on a black screen — so it is
 * drawn with an explicit light surface rather than with the theme's chrome.
 *
 * **`ACCEPTED` is drawn and not produced**, and the screen is honest about that elsewhere: nothing
 * in this product reviews a document, because a reviewer is a person and a queue. The state exists
 * because the artboard has it and because a screen that could not draw it would have to be rewritten
 * the day one arrives.
 */
@Composable
public fun DriverOnboarding(
    documents: List<OnboardingDocument>,
    uploadLabel: String,
    note: String,
    onUpload: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KvadrantTheme.colors
    val metrics = KvadrantTheme.metrics
    val type = ShashkiTheme.typography

    Column(
        modifier.fillMaxSize().background(colors.background).padding(horizontal = metrics.margin),
    ) {
        Column(Modifier.padding(top = 24.dp, bottom = 16.dp)) {
            KvadrantText("documents", style = type.pageTitle)
            KvadrantText(note, style = type.body.copy(color = colors.subtle))
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            documents.forEachIndexed { index, document ->
                DocumentRow(document, uploadLabel) { onUpload(index) }
            }
        }
    }
}

@Composable
private fun DocumentRow(
    document: OnboardingDocument,
    uploadLabel: String,
    onUpload: () -> Unit,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography
    val ink =
        when (document.state) {
            OnboardingState.MISSING -> ShashkiColors.inactive
            OnboardingState.PENDING -> colors.accent
            OnboardingState.ACCEPTED -> ShashkiColors.positive
        }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KvadrantText(document.title, style = type.rowEmphasis)
            KvadrantText(document.state.name.lowercase(), style = type.meta.copy(color = ink))
        }
        KvadrantText(document.meta, style = type.meta.copy(color = colors.subtle))

        // **The field is light in both themes** — the kit's rule, drawn rather than inherited: a
        // text field in Metro is a sheet of paper, and a sheet of paper is white.
        Box(
            Modifier
                .fillMaxWidth()
                .height(FIELD)
                .background(FIELD_SURFACE)
                .border(HAIRLINE, if (document.state == OnboardingState.MISSING) colors.accent else FIELD_SURFACE)
                .clickable(onClick = onUpload)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            KvadrantText(uploadLabel, style = type.body.copy(color = FIELD_INK))
        }
    }
}

private val FIELD = 44.dp
private val HAIRLINE = 2.dp

/** The kit's field: white paper and black ink, in both themes. */
private val FIELD_SURFACE = Color.White
private val FIELD_INK = Color.Black
