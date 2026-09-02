---
id: B-34
title: "A headless browser on the build box, so the wasm target is run and not only compiled"
status: open
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
