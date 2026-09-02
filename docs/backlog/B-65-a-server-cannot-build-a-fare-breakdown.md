---
id: B-65
title: "A server cannot build a FareBreakdown: the components live where Compose does"
status: question
priority: P1
size: M
stage: stage-6-what-running-it-said
---

# B-65 — A server cannot build a FareBreakdown: the components live where Compose does

`TripRow`, `FareBreakdown` and `EarningsTile` are declared in `shared-ui`, a Compose module, and the
server cannot depend on it. So the one thing a server-driven component is *for* — a server sending
one — is impossible for all three, and `FareBreakdown` has had a renderer, a golden and no caller
since kompot was wired up. [B-61](B-61-the-history-row-and-the-receipt.md) is blocked on this: R9·b
is a receipt drawn by that component from lines the server owns.

**This was measured rather than assumed.** The move was attempted: the three data classes went to
`:protocol` (which both sides already share and which took `kompot-core` without Compose), the server
built a `FareBreakdown` and compiled, and `shared-ui` failed — kompot's registry processor emits
`GeneratedShashkiUiKompotRegistration` from `@KompotComponentMarker`, and the marker has to be on the
**component**, in the module the processor runs in. With the classes elsewhere the generated file
still names `io.github.youndie.shashki.ui.kompot.TripRow` and every reference is an `ERROR TYPE`.
Reverted, in one commit, with this item in its place.

- **The decision this needs is where a component's declaration belongs**, and it is kompot's
  question as much as this repository's: a component is a wire type shared by a sender and a
  renderer, and a processor that requires it beside the renderer makes the sender impossible. Three
  ways out, all real: teach the processor to scan a dependency (kompot's own change); put the
  renderers in `:protocol` too, which drags Compose where it must not go; or declare the components
  twice and check the two against each other, which is the copy this repository refuses everywhere
  else.
- **The third is not as bad as it sounds and should be priced honestly.** A hand-written
  `SerializersModule` in `:protocol` next to a duplicate declaration, plus a test that holds it to
  `generatedShashkiUiSerializersModule`, is a copy with a guard — and the guard is the thing this
  repository normally asks for. It is still a copy.
- The rejected alternative is the server emitting the JSON by hand. It is the same copy with no type
  and no guard.
- Deliberately **not** covered: `TripRow` and `EarningsTile` being sent by the server. R9's list and
  D6's grid are drawn natively on purpose (D11), and this item is about whether they *could* be.

- AC: a route on the server builds a `FareBreakdown` from a ride's own numbers and answers it as a
  kompot tree, and the rider draws it with the registered renderer.
- AC: whichever way out is taken, the reason is written down where the next reader meets it — in
  `ServerDrivenComponents.kt` if the classes stay, in the research if kompot changes.
- AC: if the answer is a change in kompot, it is filed there with the measurement above.
- Anchors: `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/kompot/ServerDrivenComponents.kt`,
  `protocol/src/commonMain/kotlin/io/github/youndie/shashki/protocol/`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/promo/PromoRouting.kt`

## What the measurement said (2026-09-03)

**The processor cannot be pointed at another module, and that is read out of its bytecode.**
`KompotRegistrySymbolProcessor` calls `Resolver.getSymbolsWithAnnotation` for
`@KompotComponentMarker`; KSP answers that with the current module's own sources. There is no option
to widen it — `kompotModuleTag` names the generated file, nothing more — and the processor never
calls `getDeclarationsFromPackage`, which is the KSP call that would read a dependency.

So the three candidates in the item are the three there are, and none of them is a refactor this
item can simply do:

* **kompot scans a dependency.** The right fix, in the right place, and not this repository's to
  make. It needs an issue there with the measurement above — and filing in somebody else's
  repository is asked for first.
* **The renderers move to `:protocol`.** That is Compose in the module every headless consumer
  depends on, which is what that module exists not to have.
* **Declare the component twice with a test holding the two together.** Available tonight, and the
  reason not to take it silently is that a copy of the contract is the thing this repository refuses
  everywhere else — the endpoint tables, the DTOs, the pricing rules all say so in their own words. A
  guard makes it arguable, not free.

**Filed as a question rather than answered**, because which of the three is right is a decision about
the stack rather than about this screen, and the honest state of a reference product is that its
toolkit makes one of its own properties unreachable. The finding is written where a reader meets it:
`ServerDrivenComponents.kt`'s own header, and research open question 4.
