package io.github.youndie.shashki.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import io.github.youndie.kvadrant.components.KvadrantMessageBox
import io.github.youndie.kvadrant.components.KvadrantProgressDots
import io.github.youndie.kvadrant.foundation.KvadrantText
import io.github.youndie.kvadrant.theme.KvadrantTheme
import io.github.youndie.shashki.ui.ShashkiTheme

/**
 * What the rider is being told while there is no car yet.
 *
 * The two follow the server rather than inventing a vocabulary: [LOOKING] is a ride whose saga is
 * still asking drivers — `MATCHING` — and [NO_CARS] is the cascade having run out of them, which
 * ends the saga in `CANCELLED`. There is no third: a ride that found somebody is the trip screen's.
 */
public enum class MatchingStage { LOOKING, NO_CARS }

/**
 * R5 and R5·a: the wait, and its unhappy end.
 *
 * **No map, and that is the artboard's decision rather than an omission.** R4 gives the map 360 of
 * 844 dp because the rider is choosing where to go; here they are waiting, the camera has nothing
 * new to show, and the kit answers with a state screen — a headline, the kit's five dots, and one
 * action. A spinner over the trip screen is what the product did before this and what the kit
 * refused to draw.
 *
 * **The two states are one screen because they differ in one thing: what the server said.** The
 * destination and the class are the same ride; drawing them as two screens would make the transition
 * between them a navigation, and a rider whose car search failed has not gone anywhere.
 *
 * R5·a's headline is [ShashkiTypography.pageTitle] — 54/200, the kit's largest — against R5's
 * [ShashkiTypography.stateHeadline] at 24/300. That difference is the whole hierarchy of the pair:
 * one is a status, the other is an answer.
 *
 * **"notify me" is not here.** The artboard has it beside *try again*; it needs a subscription and a
 * push, and this product has neither. A disabled button is a promise, so the button is absent and
 * [B-43](../../../../../../../../docs/backlog/B-43-the-rider-sees-the-wait-and-its-end.md) says so.
 */
@Composable
public fun RiderMatching(
    stage: MatchingStage,
    headline: String,
    destination: String,
    meta: String,
    supporting: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    /** `asking the closest first · 0:24` — the cascade's position and its clock, while looking (B-73). */
    progress: String? = null,
    /** `economy · $ 28.96` — what was ordered, which is what the wait is for (B-73). */
    order: String? = null,
    /** R10 over the top of it, or `null`. See [CancelPrompt]. */
    prompt: CancelPrompt? = null,
    onConfirmPrompt: () -> Unit = {},
    onDismissPrompt: () -> Unit = {},
) {
    val colors = KvadrantTheme.colors
    val metrics = KvadrantTheme.metrics
    val type = ShashkiTheme.typography

    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = metrics.margin),
                verticalArrangement = Arrangement.Center,
            ) {
                KvadrantText(
                    headline,
                    style = if (stage == MatchingStage.NO_CARS) type.pageTitle else type.stateHeadline,
                )
                KvadrantText(
                    supporting,
                    Modifier.padding(top = 8.dp),
                    style = type.body.copy(color = colors.subtle),
                )

                // The kit's second line: which driver is being asked and how long they have. It
                // sits under the sentence because it changes every second and the sentence does not.
                progress?.let {
                    KvadrantText(it, Modifier.padding(top = 4.dp), style = type.meta.copy(color = colors.subtle))
                }

                // The dots belong to the state that is still happening. Leaving them running under
                // "no cars nearby" would say the search continues, which is the one thing that
                // screen exists to deny.
                if (stage == MatchingStage.LOOKING) {
                    Box(Modifier.padding(top = 28.dp)) { KvadrantProgressDots() }
                }

                // What was ordered, above the journey it was ordered for — the two lines the kit's
                // R5 ends with, and the ones a rider checks while they wait.
                order?.let {
                    KvadrantText(it, Modifier.padding(top = 28.dp), style = type.rowEmphasis)
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = if (order == null) 32.dp else 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    KvadrantText(destination, style = if (order == null) type.rowEmphasis else type.body)
                    KvadrantText(meta, style = type.meta.copy(color = colors.subtle))
                }
            }

            MatchingBar(actionLabel, onAction)
        }

        CancelPromptBox(prompt, onConfirmPrompt, onDismissPrompt)
    }
}

/**
 * R10, and the number it has to carry.
 *
 * **The fee is shown before the button, in the amount rather than the rule.** "You may be charged a
 * cancellation fee" is the sentence a product writes when it does not want to say how much; this one
 * exists to show the seam, and the seam is that a cancellation after a driver has set off is a
 * settlement with a smaller number rather than a rollback. [fee] is `null` before that point — free
 * — and the copy changes rather than the layout.
 */
public data class CancelPrompt(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val dismissLabel: String,
)

/** Shared by R5/R5·a and by the trip screen, because a rider can cancel from both. */
@Composable
public fun CancelPromptBox(
    prompt: CancelPrompt?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KvadrantMessageBox(
        visible = prompt != null,
        title = prompt?.title.orEmpty(),
        message = prompt?.message.orEmpty(),
        onConfirm = onConfirm,
        onCancel = onDismiss,
        confirmText = prompt?.confirmLabel.orEmpty(),
        cancelText = prompt?.dismissLabel.orEmpty(),
    )
}

/**
 * One action, at the app bar's height, like every other bar in this product.
 *
 * While the car is being looked for the action is *cancel*; when the search has failed it is *try
 * again*. One control in one place rather than a bar that grows a button as the state changes.
 */
@Composable
private fun MatchingBar(
    label: String,
    onClick: () -> Unit,
) {
    val colors = KvadrantTheme.colors
    val type = ShashkiTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .height(KvadrantTheme.metrics.appBarHeight)
            .background(colors.chrome)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KvadrantText(
            label,
            Modifier.clickable(onClick = onClick),
            style = type.body.copy(color = colors.accent),
        )
    }
}
