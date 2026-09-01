---
id: B-15
title: "Answer the kit's open questions, including the 4/3 spacing one"
status: question
priority: P1
size: XS
stage: stage-0-unknowns
---

# B-15 — Answer the kit's open questions, including the 4/3 spacing one

Carried from the handoff §1.6, unanswered by research because they are design decisions rather than
findings: may the 54 dp app bar carry a filled accent accept button; may the offer screen hide the
app bar entirely; and is the light theme kvadrant's stock light theme verified by goldens, or does
it wait for the kit's next pass.

Research §1.1c added a fourth, and it is the one that changes code: every spacing number in the kit
is exactly 4/3 of kvadrant's while its type ramp is exactly 1:1. 4/3 is 1 / 0.75, the kit's own
stated px → dp factor. Either the layout is a deliberate scale-up or five rows did not get the
conversion.

- **The fourth is decidable in one screenshot** — the class picker at 9 dp and at 12 dp beside the
  kit's own artboard. Adopting 12 dp before asking risks baking in a units slip; adopting 9 dp risks
  re-drawing every screen.
- **The light-theme question has a schedule attached.** Until it is answered, every fixture is dark
  only, and the light half of the suite is work that has not been scoped.
- The first two decide `DriverArrived` and `DriverOffer`, which is why they belong before the screen
  work rather than during it.
- The answers go back to the designer with the 900+ breakpoint already raised in the handoff — map
  full bleed, panels 390 dp on the right.

- AC: four answers written into the research as decisions, and the fixture list updated to match.
- AC: `ShashkiMetrics`' spacing constant set from the answer rather than from the kit's table.
