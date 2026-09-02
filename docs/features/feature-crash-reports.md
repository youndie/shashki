---
id: feature-crash-reports
title: Crash reports from the browser
type: feature
status: active
owner: unassigned
involved_services:
  - shashki-server
client_entries: []
api: []
tags: [client, observability]
---

# Crash reports from the browser

## 1. Overview

When either bundle throws, the failure reaches katcher. There is no crash reporting library for a
browser in this stack — katcher's own client has no `wasmJs` target — so what the browser sends is an
HTTP request this product makes, against katcher's own contract classes.

**It is a feature and not a line of wiring because the thing being reported is what a browser loses.**
A synchronous throw and a coroutine that fails after a suspension point surface differently, and
either alone leaves half the failures unreported.

## 2. Business rules

* Both `window.onerror` and `unhandledrejection` are installed. In a Compose/Wasm application the
  second is most of them.
* The handler returns `false`, so the browser still logs the error to the console: a reporter that
  swallowed it would take away the thing a developer looks at first.
* A report carries the build it came from. Without it a stack trace names lines in a bundle nobody can
  identify.
* No katcher configured means no reporter — a demo pointed at nothing does not report to nothing and
  pretend otherwise.

## 3. Flow

1. The application installs the handlers inside its Koin scope, so the reporter uses the application's
   own HTTP client rather than opening a second connection pool for the rarest request it makes.
2. A failure reaches the handler as a message and a stack.
3. The reporter posts katcher's own `CreateReportParams` to its ingest.

## 4. Code anchors

| Service | Code |
|---|---|
| shashki-server | `crash-client/src/commonMain/kotlin/io/github/youndie/shashki/crash/CrashReporter.kt` |
| shashki-server | `crash-client/src/wasmJsMain/kotlin/io/github/youndie/shashki/crash/UncaughtExceptions.wasmJs.kt` |

## 5. Scenarios

### Scenario: a synchronous throw reaches the ingest

* **Given:** the handlers installed in a real browser
* **When:** an `ErrorEvent` is dispatched at the window
* **Then:** the ingest request carries the message, the stack and the build identifier
* **Automated:** `shashki BrowserCrashHookTest`

### Scenario: a rejected promise

* **Given:** the same
* **When:** an `unhandledrejection` is dispatched
* **Then:** the report says what kind of failure it was and carries the stack
* **Automated:** `shashki BrowserCrashHookTest`

### Scenario: a real katcher accepts it

* **Given:** a running katcher and a key it issued
* **When:** a report is sent
* **Then:** it is accepted, and one sent with an unknown key is not
* **Automated:** `shashki KatcherIngestTest`

## 6. Out of scope

* Breadcrumbs, sessions and user identification. What an Android client gets from a Gradle plugin is
  not free here.
* Source maps and symbolication of a wasm stack.

## 7. Quirks

* **The `@JsFun` was compiled and never executed until B-34.** It is a string of JavaScript the Kotlin
  compiler cannot check; a typo in `event.reason` would have shipped as a crash reporter that reports
  nothing, which is the failure mode a crash reporter has.
* **The report's shape is katcher's own class**, since katcher published a browser target
  ([B-33](../backlog/B-33-take-the-upstream-fixes.md)). The copy that preceded it was already wrong in
  a way that compiled and would have had every report rejected whole.
