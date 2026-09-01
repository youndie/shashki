package io.github.youndie.shashki.ui

import androidx.compose.ui.text.TextStyle
import io.github.youndie.kvadrant.theme.KvadrantTypography
import ru.workinprogress.viddik.core.ViddikPlatformTextStyle

/**
 * The ramp with hinting and smoothing pinned, so a golden means the same thing on two operating
 * systems.
 *
 * **Re-implemented rather than imported, and that is not an oversight.** kvadrant-ui has exactly
 * this helper and it is `internal`, in `desktopTest` — not published (research §1.2b). Its own notes
 * are why it exists at all: the suite's first run on Linux failed on twenty-odd images and every one
 * was text, because the font file is the same across hosts and the rasteriser is not.
 *
 * `ViddikPlatformTextStyle` is public in `ru.workinprogress.viddik.core`, so what has to be copied is
 * the shape and not the mechanism. **Every slot**, because a ramp with one unpinned style is a ramp
 * with one unportable golden — and [pinned] separately for any fixture that builds a `TextStyle` by
 * hand, which is the case kvadrant found still failing after it had pinned the ramp.
 */
internal fun KvadrantTypography.portable(): KvadrantTypography =
    copy(
        normal = normal.pinned(),
        subtle = subtle.pinned(),
        title = title.pinned(),
        mediumLarge = mediumLarge.pinned(),
        large = large.pinned(),
        extraLarge = extraLarge.pinned(),
        pageTitle = pageTitle.pinned(),
        pivotHeader = pivotHeader.pinned(),
        panoramaTitle = panoramaTitle.pinned(),
        panoramaSectionHeader = panoramaSectionHeader.pinned(),
    )

/** The same pin, for a style a fixture constructs itself. */
internal fun TextStyle.pinned(): TextStyle = copy(platformStyle = ViddikPlatformTextStyle)

/**
 * The same pin for the product's own ramp.
 *
 * Separate from the library's because the slots are different — this is exactly the case kvadrant's
 * note warns about, where a fixture builds its own styles and goes on failing on another host after
 * the inherited ramp has been pinned.
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
