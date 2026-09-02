---
id: B-16
title: "One wasm bundle or two"
status: done
priority: P2
size: XS
stage: stage-1-skeleton
blocked_by: [B-01]
---

# B-16 — One wasm bundle or two

The brief proposes two bundles — rider and driver — for a cleaner demo, and that is very likely
right. It is worth re-asking once B-01 lands, because the routes to the browser have different fixed
costs per bundle: a pinned Compose dev build is paid once per build, a DOM-overlay map is paid once
per page.

- **Not a question about code layout.** Both clients share `shared-ui` either way; this is about how
  many artefacts are served and how the sign-in role routes.
- The rejected alternative is deciding now. The costs that decide it are not known until B-01 is.

- ~~AC: one answer, recorded in the research with the number that decided it.~~ **Done, 2026-09-02:
  two, in [D10](../research/research-architecture.md#d10-two-bundles-and-the-number-is-that-the-roles-are-5--of-one).**
  The number is 3 328 940 gzipped bytes of skiko against 173 808 for everything shashki has written.

## What it turned out to be

**The question dissolved and the answer stayed the same.**

This item existed because two of B-01's four routes carried a per-bundle cost that would have decided
it — a pinned Compose dev build paid once per build, a DOM-overlay map paid once per page. Route 4
has neither. So what remained was the ordinary trade-off, and the brief's proposal needed a number
rather than an argument.

Building `:shared-ui`'s own wasm distribution gave one. **skiko's wasm is 3 328 940 gzipped bytes;
everything this project has authored — both themes, every component, both screens, the map, the tile
decoder, the projection — is 173 808 before dead-code elimination.** Five per cent. A single bundle
would save a person nothing, because they fetch the same runtime either way; it would only add the
other role's screens to what they download.

**One thing is asserted and not measured, and it is marked as such.** Two bundles make a person who
uses both roles pay the runtime twice unless the content-hashed skiko file is byte-identical and
served at the same path from both. It should be — the hash is of Compose's own artefact and both
bundles build against one version — but there is one bundle today, so this is a property to verify
when the second exists rather than a number taken here.

**And a third reading came free.** At 174 kB before DCE, route 4's whole tile pipeline — decoder,
renderer, curved labels, projection — is invisible beside the runtime it rides in. The cost §1.8b
priced in work is not a cost in bytes.
