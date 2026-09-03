---
id: B-71
title: "The order bar's label does nothing: only the 48 dp circle takes the tap"
status: done
priority: P2
size: S
stage: stage-6-what-running-it-said
---

# B-71 — The order bar's label does nothing: only the 48 dp circle takes the tap

On R4 the bar reads `◯ order · $ 28.96 ···`. Tapping the words — twice, on the desktop rider against
the stand — does nothing; the request goes only when the circle itself is hit. The same shape sits
on R5 (*cancel*), R5·a (*try again*), R8 (*done*) and the driver's bars, and it is worth checking
each.

- **The kit's app bar button is a circle with a label under it**, and the label is part of the
  target. This product draws the label beside the circle, which is a defensible variant of the
  kit's shape, and then makes only the glyph pressable — which is not.
- A tester who hits the label and sees nothing concludes the order failed; a rider concludes the
  same. Nothing tells either of them where the target is.

- AC: the whole `[glyph + label]` row of `KvadrantAppBarButton` as this product uses it is one
  clickable, on every bar that has a label.
- AC: a UI test presses the label's text node and asserts the action fired.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/components/`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/RiderClassPicker.kt`

## What it turned out to be

**One composable, `LabelledAppBarButton`, and two screens that draw it.** R4's *order* and R7's
*call the driver* had each built the same thing by hand — the library's `KvadrantAppBarButton` for
the ring, a `KvadrantText` beside it — and neither had made the text pressable. The row is the
control now, with `Role.Button` and the label as its name; the ring inside keeps the library's 48 dp
target and fires the same callback, so a tap on either half is one tap.

**The other bars were already right.** R5's *cancel*, R5·a's *try again* and R8's *done* are a
single `KvadrantText` with the click on it; D3's *decline* uses the library's own label. The defect
was the two bars that had a glyph *and* a label, which is exactly the shape the kit draws for an
action row and the one the library does not provide.

**Pressed by its text, on purpose.** `LabelledAppBarButtonTest` finds the node by the words and
clicks it; with the row's `clickable` removed — the old drawing — both of its cases fail, which was
run rather than assumed. The disabled case is there too: B-62's *nothing to order* must stay nothing
to press on either half.

**Four goldens moved by 326 pixels each, and the pixels are the ring.** With `enabled` now handed to
the library's button as well as to the words, a picker with nothing to order draws its ring in the
disabled brush — `#474747` became `#2E2E2E` inside a 36 × 36 dp box at the bar's left, and nothing
else on the screen changed. That is the two halves of one control agreeing about their state, and
the re-recorded goldens were checked pixel by pixel before being kept rather than accepted because
the number was small.
