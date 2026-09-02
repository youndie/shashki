package io.github.youndie.shashki.ui.kompot

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The kit's first composition rule, made enforceable: **one accent surface per screen**, and the
 * second one degrades to chrome.
 *
 * **Claims are keyed by component id rather than counted.** A counter would be wrong under
 * recomposition — the same tile re-claiming would find the budget spent and turn to chrome on the
 * second frame, which is a screen that changes colour while nothing changed. Keyed by id, a claim is
 * idempotent: the same component always gets the same answer, and the answer is decided by
 * composition order, which is the order the server sent.
 */
public class AccentBudget {
    private var holder: String? = null

    /** True for the first component that asks, and for that same component ever after. */
    public fun claim(id: String): Boolean {
        if (holder == null) holder = id
        return holder == id
    }
}

/**
 * Provided per screen. **A default exists here and does not elsewhere** — `LocalMapSurface` has
 * none, because a missing map is a hole a golden would pass. A missing budget is not: the default is
 * a budget, so the worst a screen that forgot to provide one gets is the rule applied across it
 * rather than per screen, which is stricter and never wrong-looking.
 */
public val LocalAccentBudget: ProvidableCompositionLocal<AccentBudget> = staticCompositionLocalOf { AccentBudget() }
