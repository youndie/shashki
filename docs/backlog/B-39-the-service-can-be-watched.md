---
id: B-39
title: "The service can be watched: metrik, tracy, telek and kompot's degradation sink"
status: done
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
- ~~AC: telek receives one alert that somebody caused on purpose.~~ **Dropped: the premise is
  wrong.** telek is a toolkit for building Telegram bots as state machines, not an alerting system —
  nothing in it receives an alert. "Tell somebody when it breaks" belongs to metrik's own notifier
  (`METRIK_TELEGRAM_TOKEN` plus a rule), which is configured on the metrik installation, and this
  item already puts alert rules out of scope because they describe an installation.
- Anchors: `server/src/main/kotlin/io/github/youndie/shashki/server/Application.kt`,
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/kompot/`

## What it turned out to be

**Three of the four were configuration, and configuration is not the distance to a true number.** The
installs are two lines. What stood between them and a collector holding numbers somebody can act on
was five findings, each visible only from what actually arrived — the full table is
[research §1.6f](../research/research-architecture.md), and the two that would have shipped silently
are these:

- **metrik and tracy both publish `agent-jvm-<version>.jar`, on the same version number.** Two
  different files want one name in `lib/` and `installDist` refuses. Every value of
  `duplicatesStrategy` resolves that by keeping one and dropping the other — and the one dropped is
  an agent that then reports nothing, from inside a running deployment, indistinguishable from a
  service nobody is looking at. Both are kept and renamed by group (konekt met this first and its
  fix is ported with attribution), and `imageContext` now fails unless two agent jars are in the
  image: the guard exists because the failure is silent by construction.
- **Every saga span arrived named `saga.order.$phase.QuoteStep`.** One unexpanded template, written
  once in a final `intercept` so that no step could forget it — and therefore wrong in every span at
  once, grouping five phases under one row. Nothing type-checks a name; `spanName` is now a property
  and `OrderSagaTest` asserts that it carries its phase.

**The evidence, rather than the wiring.** `POST /api/quotes` on the stand produces a trace of
`route.estimate`, `pricing.quote` and three `dispatch.pickupEta` spans under one request span, and a
ride produces `saga.order.VALIDATION.QuoteStep` through `saga.order.EXECUTION.OfferStep`. The number
that is checked against something else: **8 requests sent, metrik's `requests` moved by 8, tracy held
8 `POST /api/quotes` spans** over the same window — two collectors, two transports, and a count made
by hand. The first attempt at that delta said 20 for 8, because a reading taken seconds after traffic
has not counted it yet; the check that holds needs a quiet period first.

**The fourth row was the one that was not boilerplate, and it stayed that way.** kompot's degradation
sink is now bound — `ReportingDegradationSink` on the client, `DegradationCounter` and
`POST /api/screens/degradations` on the server — and the pair of tests meets on `:protocol`'s own
`DegradationReport` rather than on a string, because "built at both ends and joined at neither" is
what this backlog keeps finding. Writing the client half found that the sink depended on the
application client's `defaultRequest` for its content type: with any other client the report failed
to serialise and its own `runCatching` swallowed it.

**And one criterion was dropped rather than met.** telek is a bot toolkit; nothing in it receives an
alert. The item's own table asserted otherwise, which is the third time here that a line naming a
library has turned out to name a capability it does not have.
