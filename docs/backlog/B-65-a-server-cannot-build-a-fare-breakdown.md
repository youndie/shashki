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
  make. Filed as [youndie/kompot#113](https://github.com/youndie/kompot/issues/113) with the
  measurement above, the attempted move and its error, and the three consumer-side workarounds
  priced — the argument being that none of them is bad enough to refuse, which is what makes a
  consumer pay for it quietly.
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

## The toolkit answered, and the move still does not resolve (2026-09-03)

[youndie/kompot#114](https://github.com/youndie/kompot/pull/114) is merged and the answer is that the
split **is** supported: a renderer may live in a different module from its component, the pairing
comes from the renderer's type argument, and kompot's own `:kompot-forms` / `:kompot-forms-client`
are that shape. What #113 actually reported, in the maintainer's reading, was a processor that failed
illegibly — it wrote a plausible package into generated code, so the reader saw `ERROR TYPE` in a
file nobody wrote and concluded the layout was unsupported.

**The toolkit is on `0.36.2.116` here now** and the message is the fix's own:

> Could not resolve the component type of `KompotComponentRenderer<T>` on `TripRowRenderer`. A
> renderer MAY be declared in a different module from its component… What is required is that the
> module declaring the component is on THIS module's compile classpath, and that it runs the
> processor itself for its own registration.

**And the move still does not resolve.** Measured, in this order:

| What was tried | What happened |
|---|---|
| components moved to `:protocol`, which `:shared-ui` already has as `api(projects.protocol)` | `:protocol` compiles, the server compiles against the components, `:shared-ui`'s metadata KSP reports all three renderers unresolved |
| `:protocol` given the processor and its own `kompotModuleTag` | it generates `generatedShashkiProtocolSerializersModule` with all three subclasses — and `:shared-ui`'s error is unchanged |
| the same with the processor removed from `:protocol` again | the same three errors, verbatim |
| `:protocol:clean :shared-ui:clean` and a fresh build | the same three errors |

So the requirement the message names is met — the module is on the compile classpath (the Kotlin
compiler resolves the same types in the same source set; only KSP does not) — and the second half,
running the processor there, changes nothing. Notably kompot's own `:kompot-forms` does **not** run
the processor, which suggests that half of the sentence is not the load-bearing one either.

The bump is kept: `0.34.1.101 → 0.36.2.116` is green across `check`, and the legible error is worth
having on its own. The move is reverted rather than left half-done.

**What is owed next is a report, not another attempt.** Two modules in one Gradle build, same KSP
2.3.11, the same shape as the toolkit's own pair, and the resolver does not see across the boundary —
that is a fact for the people who wrote the resolver, and filing it is asked for first.
