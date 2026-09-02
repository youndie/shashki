---
id: B-34
title: "A headless browser on the build box, so the wasm target is run and not only compiled"
status: done
priority: P2
size: M
stage: stage-3-surface
---

# B-34 — A headless browser on the build box, so the wasm target is run and not only compiled

Three items have now ended with the same sentence, in the same place, for the same reason: **there is
no browser on the build box, so `wasmJsBrowserTest` is switched off and the target the product ships
on is compiled but never executed.**

- [B-09](B-09-browser-side-pkce.md) proved the PKCE algorithm against RFC 7636's own vector — on the
  JVM, against the JDK provider.
- [B-10](B-10-crash-reports-from-the-browser.md) closed saying so outright rather than putting a
  green tick over an untested path.
- [B-26](B-26-sign-in-end-to-end.md) ran the whole sign-in against a live shildik and
  could not run it where it matters.

The switch is written down in three build scripts (`shared-ui`, `rider`, `auth-client`:
`browser { testTask { enabled = false } }`), which is honest and is also exactly the shape of a
guard nobody notices is off.

What is already there: the Kotlin plugin provisions its own Node (`~/.gradle/nodejs/node-v25.0.0`)
and Yarn, so karma and webpack run. What is missing is one binary and the environment variable that
names it. What is **not** yet known is whether that binary can be had on this box at all: Ubuntu
24.04 packages Chromium as a snap, and snap inside WSL is its own problem — so the first hour of this
item is finding out, and "it cannot" is a legitimate outcome to write down.

- AC: `./gradlew check` runs at least one `wasmJsBrowserTest` and it is not a task that passes over
  no tests — the vacuity guard is the point, since a suite with nothing in it is greener than one
  with a failure.
- AC: the sign-in flow from [B-26](B-26-sign-in-end-to-end.md) runs in the browser
  build, which is what proves WebCrypto computes the same `S256` as the JDK provider. This criterion
  is B-26's, moved here with the thing that blocks it rather than left ticked over an untested path.
- AC: the browser's crash path from [B-10](B-10-crash-reports-from-the-browser.md) is exercised —
  `window.onerror` reaches katcher's ingest.
- AC: how the browser is installed is written down and repeatable, and CI is told the same way; a
  binary that exists only because somebody typed a command once is a build that breaks on the next
  machine.
- AC: if it turns out the box cannot host one, that is recorded here with what was tried, and the
  three build scripts point at this item instead of repeating the reason three times.
- Anchors: `auth-client/build.gradle.kts`, `rider/build.gradle.kts`, `shared-ui/build.gradle.kts`

## What it turned out to be

**The box could host a browser all along, and the first hour was the whole risk.** Ubuntu 24.04
packages `chromium` as a snap with no apt candidate, which is what three items had recorded as the
obstacle — but a package was never the only way in. Chrome for Testing is a plain zip Google
publishes per version, it needs no root, and on this machine **not one shared library was missing**:
`scripts/install-chrome.sh` downloaded 152.0.7977.75, unzipped it, and it printed its version.

So the answer to "can this box host one" is yes, and the three items that closed against it closed
against an assumption rather than a measurement. That is the finding, and it is not a comfortable
one: the obstacle was written down once and then cited twice more without being re-tested.

**Fifty-six tests now run in a real Chrome**, across five modules, in the same `./gradlew check`:

| Module | In a browser | What it is worth |
|---|---|---|
| `:auth-client` | 8 | **B-26's unmet criterion.** `S256` computed by WebCrypto is the challenge shildik verifies, rather than an assumption that it matches the JDK's |
| `:crash-client` | 9 | **B-10's.** Two of them dispatch an `ErrorEvent` and an `unhandledrejection` at the window and require the ingest request out the other end |
| `:rider` | 18 | the view models, on the target they ship on |
| `:driver` | 15 | the same |
| `:shared-ui` | 6 | `MapViewportTest` moved to `commonTest`: the camera decides what gets fetched, and it is arithmetic with no resources in it |

**The `@JsFun` was the thing most worth running.** It is a string of JavaScript the Kotlin compiler
cannot check, reviewed and compiled and never executed — a typo in `event.reason` would have shipped
as a crash reporter that reports nothing, which is the failure mode a crash reporter has.

**One test had to be rewritten to be about the browser rather than about the clock.** The first
version launched the handler into the `TestScope` and called `advanceUntilIdle`, which returned
instantly with nothing sent: the HTTP call resumes on the browser's event loop and virtual time
knows nothing about it. Awaiting the request the engine received is the only condition that is about
the thing under test.

**The switch and the guard live in the root build**, which says it holds no shared configuration —
and the exception is argued rather than taken. What that rule forbids is a module's own decisions
being made centrally; whether the machine has a Chrome is not one, it is the same fact for every
module, and five copies of it would be five places to forget. That is precisely the shape this item
was filed to end: three items closed against one wall while the switch sat in three build scripts.

Both paths are exercised. With `CHROME_BIN` the suites run and each prints its count; without it they
are skipped with a line naming the script — a checkout on a laptop with no browser must still build,
and silence is what this item exists to end. **And a suite that runs nothing does not pass**: the
guard reads the task's own report and fails with the task's name, verified by pointing it at the
wrong extension, which turns the green run red.

**The residual hole, stated.** A module with no wasm test sources at all makes the task `NO-SOURCE`,
so the guard's `doLast` never runs — `:protocol` is in that state today, correctly. It means a module
that *lost* all its tests would go quiet rather than red. That is not specific to the browser suite:
the desktop and JVM tasks behave the same way, and closing it properly is a per-module expectation
list, which is a thing that rots. Written down rather than built.

CI installs the same pinned browser with the same script rather than using the runner image's own
Chrome: the product's one executed target running in a different browser on every machine would make
"it passes locally" stop meaning anything.
