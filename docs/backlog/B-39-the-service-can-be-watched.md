---
id: B-39
title: "The service can be watched: metrik, tracy, telek and kompot's degradation sink"
status: open
priority: P2
size: M
stage: stage-4-elsewhere
blocked_by: [B-35]
---

# B-39 — The service can be watched: metrik, tracy, telek and kompot's degradation sink

Four of the stack's own tools are absent from a product whose purpose is to show the stack, and for a
Ktor server three of them are configuration rather than work. The fourth is a hook this repository
already noticed it had not connected.

| Tool | What it would answer | State |
|---|---|---|
| metrik | is it up, how slow, how many errors | not wired |
| tracy | where did this request's time go, and what did the saga do | not wired |
| telek | tell somebody when it breaks | not wired |
| kompot's degradation sink | which server-driven component did a client fail to render | named in [B-32](B-32-which-screens-the-server-sends.md) and not bound |

- **The last row is the one that is not boilerplate.** kompot's whole degradation story is that a
  client which does not know a component draws `UnknownComponent` instead of crashing — which is
  correct and silent. The sink is how a server finds out that half its users are looking at a hole,
  and B-32 recorded that it exists and is not connected. That is the same shape as this repository's
  other findings: a mechanism built at both ends and joined at neither.
- **[B-35](B-35-the-server-as-an-image.md) is the natural moment**, which is why this is blocked by
  it rather than merely related. An exporter with nowhere to scrape it from and a trace with no
  collector are configuration nobody can check; once there is an image and a stand, all four become
  observable in the ordinary sense of the word.
- The rejected alternative is wiring them now against a laptop. It produces green configuration and
  no evidence, and the first real deployment finds out which of it was wrong.
- Deliberately **not** covered: dashboards and alert rules as artefacts in this repository. Those
  describe an installation.

- AC: a request through `/api/quotes` appears in tracy as a trace whose spans name the estimator and
  the pricing, and a saga's phases are visible in it — a trace that shows one span called "POST" is
  the library installed rather than used.
- AC: metrik answers for the service, and one of the numbers is checked against something else that
  measures it, rather than believed because it appeared.
- AC: a component the client cannot render reaches the server, and a test shows the count moving —
  the degradation sink asserted by data, not by the binding existing.
- AC: telek receives one alert that somebody caused on purpose.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/Application.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/kompot/`
