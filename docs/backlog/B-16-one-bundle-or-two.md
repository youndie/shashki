---
id: B-16
title: "One wasm bundle or two"
status: question
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

- AC: one answer, recorded in the research with the number that decided it.
