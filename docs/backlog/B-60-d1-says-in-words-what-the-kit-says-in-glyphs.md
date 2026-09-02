---
id: B-60
title: "D1 states a document's status in words where the kit states it in a glyph"
status: done
priority: P2
size: S
stage: stage-6-what-running-it-said
---

# B-60 — D1 states a document's status in words where the kit states it in a glyph

`Shashki Flows` is explicit under D1: *status is a glyph, not a badge: green tick, subtle timer,
accent camera for what is missing.* [B-47](B-47-driver-onboarding-and-the-object-store.md) built the
screen with a right-aligned word — `pending`, `missing` — which reads as a badge and spends a line of
type on something the kit gives to a 20 dp mark.

- **The glyph is the leading slot's whole argument.** Composition rule 4 allows a row one glyph, and
  a status is exactly what it is for: three rows of marks are scanned in one look, three rows of
  words are read. `ShashkiIcons` already carries the vocabulary this needs a tick and a timer added
  to.
- **The upload field is a separate question and this item does not touch it.** The kit's D1 rows are
  tapped; the product draws a light `choose a file` field per row, which the handoff's colour rule
  explicitly protects ("адресный поиск и загрузка документов НЕ перекрашивать"). Whether the field
  or a tap is right is worth asking — but the answer is not "make it dark", and it is not this item.
- The rejected alternative is keeping both, a glyph *and* the word. That is the badge the kit's
  sentence rules out.
- Deliberately **not** covered: the five documents of the design against this product's three, and
  the `3 of 5` progress line. Both are scope decisions B-47 took deliberately.

- AC: each row's state is a glyph in the kit's semantic colours, and the golden shows all three
  states at once.
- AC: `GlyphCoverageTest` still passes — the new marks are vectors, like `ShashkiIcons.star`.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/DriverOnboarding.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/ShashkiIcons.kt`,
  `docs/screens/screen-driver-onboarding.md`

## What it turned out to be

**Three marks in the leading slot, which is what composition rule 4 gives a row one glyph for.** An
amber timer for *pending*, a green tick for *accepted*, a grey ring for *missing* — the kit's
semantic colours doing the work a right-aligned word was doing badly. The golden shows all three at
once, which is the only way to see that they read as a set rather than as three separate decisions.

**The third mark is a ring and not the kit's camera, deliberately.** The design says "accent camera
for what is missing"; this product uploads a file through a picker and a camera would promise one.
An empty ring is the shape of the other two with nothing in it, which is what "nothing here yet"
looks like in a language that has no illustrations.

**And the fixture was saying something the product does not.** Its note read *"three documents, and a
person looks at them"* against the screen's own *"nothing here reviews them yet"* — a golden of a
friendlier product than the one that ships, and the same gap the earnings fixture had with two tiles
against the client's three. It carries the product's sentence now.

`GlyphCoverageTest` passes: the three marks are `ImageVector`s built from path data like
`ShashkiIcons.star`, so nothing new asks the bundled face for a character it does not have.
