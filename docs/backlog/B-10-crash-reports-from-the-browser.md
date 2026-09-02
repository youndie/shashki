---
id: B-10
title: "Crash reports from the browser go over katcher's ingest endpoint"
status: done
priority: P2
size: S
stage: stage-3-surface
---

# B-10 — Crash reports from the browser go over katcher's ingest endpoint

The brief has both clients reporting to katcher. Research §1.6 found the katcher client publishes
jvm, four native desktop targets, three iOS targets and mingw — and no browser target at all. The
ingest is documented instead: `POST {serverUrl}/api/reports`.

- **A Ktor call, not a library change.** Adding `wasmJs` to katcher would be modifying a library so
  a consumer can skip writing a request.
- The price is stated rather than discovered: the offline persistence and the build-uuid plumbing the
  Android client gets for free are not free here. What the browser sends is what we choose to send.
- Not covered: the server, which uses the JVM client normally.

- ~~AC: an uncaught exception in either client arrives in katcher with the build identifier
  attached.~~ **Done as far as it can be without a client, 2026-09-02.** Against a real
  `ghcr.io/youndie/katcher:0.6.2`: a report from `CrashReporter` appears as
  `IllegalStateException no MapSurface in composition`, tagged `production · 2026.09.02-b10`, and
  the release shows up in katcher's own release filter. What is missing is the *application* the
  exception escapes from — there is no rider or driver client yet — so the hook that catches it is
  written and compiled for both targets and exercised on neither. B-26 loads the first page.
- ~~AC: the katcher coordinate this adds is a release or a CI-numbered publish, not a `-SNAPSHOT`~~
  **— no coordinate was added, for the reason the item itself gives.** This is a Ktor call, not a
  client library, so nothing from katcher is on the classpath. What is pinned is the image the test
  runs against: `ghcr.io/youndie/katcher:0.6.2`, the newest published tag rather than `latest`. The
  clean-checkout guarantee from [B-13](B-13-pin-every-dependency.md) is untouched — this is the
  second item in a row where the honest answer to an inherited pinning criterion is that no
  dependency exists to pin.
- Anchors: `katcher/README.md`, `katcher/client/build.gradle.kts`

## What it turned out to be

**Four things about katcher's contract, none of them in its README, all of them load-bearing.**

- *The ingest is public by construction.* `route("api")` sits outside the
  `authenticate(HEADER_USER_AUTH)` block the pages are inside — which is right, because an
  application that has just crashed cannot be asked to sign in, and it is the opposite of what the
  header-auth section of the README leads a reader to expect.
- *It answers 202, not 200.* The report is queued. A reporter that accepted any 2xx would count a
  proxy's 200 or a captive portal as a delivered crash, silently, for as long as nobody looked.
- *An unknown key is 401 before anything is queued* — so a wrong key produces no reports and no
  errors, which is the failure this item's own test exists to make visible.
- *`katcher:shared` has no `wasmJs` either.* The client artifact having no browser target was known
  (§1.6); the module holding `CreateReportParams` not having one was not. So the payload here is a
  **copy of somebody else's wire type**, which the portfolio calls a future bug. `CrashReport`'s
  KDoc says that rather than presenting the duplication as a design, and research §1.6b1 proposes
  the two-line fix upstream instead of doing it.

**The reporter is deliberately small and deliberately unhelpful in two places.** It takes an
`HttpClient` rather than building one — the application has an engine and a second would be a second
connection pool — and it serialises the body itself rather than requiring `ContentNegotiation`, so
installing a plugin is not a hidden part of its contract. It never throws: it runs inside an
uncaught-exception handler, and a reporter that threw would replace the crash being reported with its
own. And it refuses a blank `release` at construction, because the Android client gets its build uuid
from a Gradle plugin and a browser has none — a report that reached the server without naming its
build is a report nobody can act on.

**Both platforms lose exceptions differently, and the hook admits it.** The JVM handler gets a
`Throwable` and keeps whatever handler was there before, because replacing it is how a crash reporter
breaks the process it was added to protect. The browser gets `window.onerror` *and*
`unhandledrejection` — either alone leaves half the failures unreported, and in a Compose/Wasm
application most failures are rejected promises. That half compiles for `wasmJs` and runs nowhere:
there is no browser on the build box, and saying so is better than a green tick over an untested path.
