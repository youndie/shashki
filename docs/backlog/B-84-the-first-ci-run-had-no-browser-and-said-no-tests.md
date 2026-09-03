---
id: B-84
title: "Every wasm suite on the first public CI run reported no tests, and neither half of the reason was the tests"
status: done
priority: P1
size: S
stage: stage-6-what-running-it-said
---

# B-84 — Every wasm suite on the first public CI run reported no tests, and neither half of the reason was the tests

The repository went public (2026-09-03) and every run of the `gradle` job was red, on `main` and on
each of Renovate's branches. What it said:

```
Execution failed for task ':auth-client:wasmJsBrowserTest'.
> There are test sources present and no filters are applied, but the test task did not discover any
  tests to execute. This is likely due to a misconfiguration.
```

— for `:auth-client`, `:crash-client` and `:driver`. The documentation gate beside it was green, and
so was `./gradlew check` on the build box, on the same commit.

**The message names the wrong subject, and it did that twice over.** Reading up the log rather than
at the failure gave the first half:

```
[FATAL:zygote_host_impl_linux.cc:129] No usable sandbox! If you are running on Ubuntu 23.10+ or
another Linux distro that has disabled unprivileged user namespaces
ChromeHeadless failed 2 times (cannot start). Giving up.
```

The browser never opened a page. Chrome's own sandbox needs unprivileged user namespaces and the
runner image has them off; the runner's packaged Chrome carries a setuid helper for exactly this and
the archive `scripts/install-chrome.sh` unpacks does not. Gradle reports the empty result and not
the dead browser, so the failure names the suites rather than the machine.

The build box has the same script and the same Chrome and was green — because `check` there ran with
no `CHROME_BIN`, and the root build disables `wasmJsBrowserTest` without one (B-34). **The one
target the product actually ships had not been executed locally at all.** Running it with the
variable set produced the second half, which CI had never reached:

```
:crash-client:wasmJsBrowserTest FAILED
  FatalBandTest.a synchronous throw puts a band on the page with its message  FAILED
> Test running process exited unexpectedly.
```

- AC: `wasmJsBrowserTest` starts a browser and runs its tests on a GitHub runner, and the suites
  are green there and on the build box for the same reason.
- Anchors: `scripts/install-chrome.sh`,
  `crash-client/src/wasmJsTest/kotlin/io/github/youndie/shashki/crash/FatalBandTest.kt`

## What it turned out to be

**Two independent defects that the same message hid, and one of them was in the harness's idea of a
failure.**

*The browser.* `install-chrome.sh` now writes a `chrome-for-karma` launcher beside the binary —
`exec chrome --no-sandbox --disable-dev-shm-usage "$@"` — and `--export` points `CHROME_BIN` at the
launcher rather than at the binary. The flag lives there and not in the workflow so that every
machine launches the browser the same way; a launcher that differs between a laptop and CI is what
makes "it passes locally" stop meaning anything. What is given up is the renderer sandbox of a
browser that only ever loads this build's own test bundle over localhost.

*The test.* `FatalBandTest` dispatches a synthetic `ErrorEvent` at `window`, which is the whole
point of it: the band exists because a Compose/Wasm bundle loses exceptions. karma installs
`window.onerror` to catch exactly that and reports one as a failure of whichever test is running —
**so the test failed for doing its job.** The Kotlin karma reporter then died inside `specFailure`
(`Cannot read properties of undefined (reading 'forEach')`), the browser process exited mid-suite,
and the modules still running reported the empty result at the top of this file. The two `@JsFun`
dispatches now blank `window.onerror` and `window.onunhandledrejection` for the length of the event
and put back what was there; the band listens with `addEventListener`, which is untouched, so what
is under test still receives exactly the event a browser delivers.

Measured after both, with `CHROME_BIN` set, on the build box: `:crash-client` 12 tests,
`:shared-ui` 12, `:auth-client` 14, `:driver` 42, `:rider` 53 — **133 tests in a browser that had
run none of them in the check that was called green.**
