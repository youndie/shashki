---
id: screen-driver-onboarding
title: Driver onboarding
type: client_screen
platform: [web, desktop]
status: active
entry:
  web: "/driver/documents"
parent_feature: feature-driver-onboarding
calls_api:
  - endpoint-driver
source: driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/documents
---

# Screen: driver onboarding

## 0a. Code anchors

| What | File |
|---|---|
| View model | `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/documents/ui/OnboardingViewModel.kt` |
| Screen / Content | `driver/src/commonMain/kotlin/io/github/youndie/shashki/driver/feature/documents/ui/OnboardingScreen.kt` |
| Drawing | `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/DriverOnboarding.kt` |
| The file dialog | `driver/src/commonMain/.../ui/FilePicker.kt` and its two `actual`s |
| Goldens | `screens_driver_onboarding`, `screens_driver_onboarding_light` |

## 0. Entry point and visibility

- **Entry point:** a line under the shift screen's header. Not a button beside the shift switch —
  the kit allows one call to action on that screen and it is the switch.
- The line reads *documents* and says nothing about what is missing: the states are the store's
  answer and are read here, on D1. A label that guessed would be a second answer to the same
  question.

## 1. Three rows, three states, and one that nothing can produce

`MISSING` is a grey ring, `PENDING` an amber timer, `ACCEPTED` a green tick — **a glyph in the row's
leading slot, not a word at its end** (B-60). The kit says so in as many words about D1, and
composition rule 4 gives a row one glyph for exactly this; a right-aligned word reads as a badge and
spends a line of type on what a 20 dp mark says at a glance. The third mark is a ring rather than the
design's camera because this product uploads through a file picker, and a camera would promise one. **Nothing in this product writes `ACCEPTED`.** A document is
accepted by a person who does not exist here, so a driver who uploads all three sees three amber rows
for ever. That is the feature working, not a gap in it: the alternative shows a green tick for a file
nobody looked at.

The row's second line is the size the store reported when it listed the object. It is the only fact
this screen can state about a file it cannot draw, and it comes from the store rather than from what
the browser thought it sent.

## 2. The upload field is light in both themes

Section 06 of the kit: the two surfaces that fight the instinct to go dark stay light, and this is
one of them. Both goldens were looked at rather than only compared — dark and light — and the field
is white in both.

## 3. What the client does not do

- **No preview.** A picked file is bytes and a content type; drawing it would mean decoding formats
  in Compose for a screen whose subject is whether the file arrived.
- **No state of its own after an upload.** The response is the whole document view and the screen
  takes it: flipping a row to `PENDING` locally would show a state the store has not confirmed, which
  is the same invention as `ACCEPTED`.
- **The picker is a port.** `pickDocument` is `expect`/`actual` — `<input type="file">` and a
  `FileReader` on wasm, a no-op on desktop, where there is nobody to ask. It is a constructor
  parameter with a default, so the test hands in its own and the DI graph never sees a file dialog.
