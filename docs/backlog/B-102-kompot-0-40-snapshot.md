---
id: B-102
title: "kompot 0.40 stabilization line: the snapshot, and the two workarounds it retires"
status: done
priority: P2
size: S
stage: stage-3-surface
---

# B-102 — kompot 0.40 stabilization line: the snapshot, and the two workarounds it retires

[B-101](B-101-kompot-0-39.md) moved the build to kompot 0.39 and filed two defects instead of
absorbing them: a `divider` with no colour was drawn in Material's `outlineVariant` with no way for
the design system to say otherwise
([youndie/kompot#204](https://github.com/youndie/kompot/issues/204)), and the DSL could not set
`heading` ([youndie/kompot#205](https://github.com/youndie/kompot/issues/205)). Both are fixed on
kompot's main (#213, #214), which is now the 0.40 stabilization line: consumers live on the newest
`0.40.0.<run>` and what they find there becomes a kompot issue. What changes for this repository is
written in kompot's `UPGRADING.md` (0.40.0) and SPEC.md §4.10 and §6.

- **A snapshot, deliberately.** `kompot = "0.40.0.208"` from the Reposilite rather than a release,
  because the stabilization line is where the fixes are and where this repository is useful to
  kompot as a consumer. The settings plugin's filter admits `io.github.youndie` and its subgroups on
  the snapshot repository, so nothing changes there — the same as for `0.39.0.201`.
- **The rule is the design system's.** `ShashkiDesignSystem` answers `KompotSurfaceRoles.Divider`
  with the hairline — the ink at 12 %, the line `DriverRow` draws by hand — and the receipt's
  `divider` names no colour. The `hairline` colour token existed only to work round #204, so it is
  gone from `ShashkiTokens`: a token nothing sends is a word the vocabulary guards would keep
  checking for no reason.
- **The headings are DSL.** Receipt and promo set their titles with `text(…, heading = true)`. The
  ids stay the ones the trees always had (`receipt-title`, `promo-headline`) rather than kompot's
  `nextChildPath()` — every other node in both trees names its id, and `PromoTreeTest` asserts the
  headline's.
- Rejected: keeping the `hairline` token for a client that predates the role. Server and bundles ship
  in one image, so the only such client is a tab left open across a deploy; it draws the divider in
  Material's `outlineVariant` until it reloads, which costs a shade of one line.
- Not here: `0.40.0` from Central. When kompot cuts it, the move is one line in the catalogue.

- ~~AC: the build resolves kompot `0.40.0.<run>`.~~ **Done:** `0.40.0.208`.
- ~~AC: nothing in the repository works round #204 or #205.~~ **Done:** no divider names a colour;
  no tree builds a `TextComponent` by hand.
- ~~AC: the receipt goldens do not move.~~ **Done:** see below.
- ~~AC: the two source breaks UPGRADING names do not apply, or are fixed.~~ **Done:** nothing here
  passes `modifierBlock` by position or calls `expandable`.
- Anchors: `gradle/libs.versions.toml`,
  `protocol/src/commonMain/kotlin/io/github/youndie/shashki/protocol/ScreenTokens.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/kompot/ShashkiDesignSystem.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/receipt/domain/ReceiptScreenUseCase.kt`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/promo/PromoRouting.kt`

## What it turned out to be

**The goldens did not move**, and that is the check rather than luck. The receipt fixture now sends
its `divider` with no colour, as the server does, so `screens_rider_receipt` and its light twin draw
the rule through kompot's `ruleColor()` → `resolveSurface(Divider).outline` instead of through
`resolveColor("hairline")` — and both PNGs verify unchanged, as do the other 50: the same `Color`
value reaches the same `HorizontalDivider`. `DesignSystemInkTest` holds the role itself (the ink at
12 % in both themes), since a golden that does not move cannot say which path drew it.

**And the golden can see the line go.** A golden that stays green says nothing unless it would have
gone red, so the role was set to `Color.Transparent` for one run: `viddikVerify` failed on both
receipt goldens at 366 of 329 160 pixels (0.11 % against the 0.05 % tolerance) — the rule's whole
width — and passed again with the hairline back. That is the failure
[youndie/kompot#217](https://github.com/youndie/kompot/issues/217) describes from another consumer:
0.40 asks `resolveSurface` two new roles, and a catch-all branch that answers with a colour erases
dividers and table rules while every golden without one stays green. Here the catch-all answers only
the shape and leaves every colour unspecified, so `table_header` (no table is drawn in this product)
keeps kompot's default; the branch now says why it must stay that way.

**The colour vocabulary is six names and five colours now.** Dropping `hairline` from
`ShashkiTokens.COLORS` takes the one derived brush out of the palette check; `ReceiptTreeTest` asserts
the rule carries no `color` at all, because a token on it would override the role.

**Nothing new to report to kompot.** Both fixes do what #204 and #205 asked, on this repository's own
trees, with no source change beyond the two call sites; the one hazard found is #217, already filed.
