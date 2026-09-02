package io.github.youndie.shashki.rider

import androidx.compose.ui.text.TextStyle
import io.github.youndie.shashki.ui.ShashkiTypography
import ru.workinprogress.viddik.core.ViddikPlatformTextStyle

/**
 * The ramp with hinting and smoothing pinned, so a golden means the same thing on two machines.
 *
 * **This is the third copy of eleven lines, and that is the finding.** kvadrant-ui has it and keeps
 * it `internal` in `desktopTest` (research §1.2b); `:shared-ui` copied it for the same reason and
 * made it `internal` too; `:rider` now copies it from `:shared-ui`. Each copy was correct and each
 * was forced — a screenshot helper cannot live in `commonMain` without making a UI library depend on
 * a screenshot library at run time, and Kotlin Multiplatform has no `testFixtures` to publish it
 * from.
 *
 * The shape that would end it is a small published testing module, or viddik publishing the pin
 * itself. Written down here rather than done, because it is a decision about the portfolio and this
 * item is about an application.
 */
internal fun ShashkiTypography.portable(): ShashkiTypography =
    copy(
        pageTitle = pageTitle.pinned(),
        figure = figure.pinned(),
        stateHeadline = stateHeadline.pinned(),
        tileLabel = tileLabel.pinned(),
        rowEmphasis = rowEmphasis.pinned(),
        body = body.pinned(),
        meta = meta.pinned(),
    )

private fun TextStyle.pinned(): TextStyle = copy(platformStyle = ViddikPlatformTextStyle)
