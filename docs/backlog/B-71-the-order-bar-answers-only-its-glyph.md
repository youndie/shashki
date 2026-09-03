---
id: B-71
title: "The order bar's label does nothing: only the 48 dp circle takes the tap"
status: open
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
