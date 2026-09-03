---
id: B-87
title: "B-14's recorded reproduction sets three variables and its control needs four, so following it runs the half that proves nothing"
status: done
priority: P3
size: S
stage: stage-6-what-running-it-said
---

# B-87 — B-14's recorded reproduction sets three variables and its control needs four, so following it runs the half that proves nothing

Found re-verifying [B-14](B-14-receipt-over-smtpkn-jvm.md) against a real Mailpit (2026-09-04) — the
item is closed on a measurement taken once, on 2026-09-02, behind an `assumeTrue` gate that CI does
not satisfy, so nothing has run it since.

**The item itself holds.** Standing up Mailpit with a fresh certificate and running the test with all
four variables set: `tests=2 skipped=0 failures=0` — the receipt goes out through smtpkn's JVM target
over a verified STARTTLS handshake and arrives, and the control that makes that worth saying (the
same code pointed at a CA which signed nothing must fail) fails as required.

**What does not hold is the command written down beside it.** `ReceiptOverSmtpTest`'s KDoc gives a
reproduction that sets `SHASHKI_MAILPIT`, `SHASHKI_MAILPIT_API` and `SHASHKI_MAILPIT_CA` — three —
and generates one certificate. The class defines a fourth, `SHASHKI_MAILPIT_WRONG_CA`, which the
control's own `assumeTrue` requires, and there is no second certificate in the block to point it at.
Run exactly as documented:

| | as documented (3 variables) | with the control's variable (4) |
|---|---|---|
| `a receipt … arrives in Mailpit` | passed | passed |
| `a certificate no configured CA signed is refused` | **skipped** | passed |
| the report | `tests=2 skipped=1` | `tests=2 skipped=0` |
| the build | `BUILD SUCCESSFUL` | `BUILD SUCCESSFUL` |

Both runs are green. B-14's own sentence is what the difference costs: *"a mail arrived" does not
prove a certificate was checked* — and following its instructions is exactly the run in which nobody
checked.

- AC: the recorded reproduction produces `skipped="0"`, and says how to tell.
- Anchors: `server/src/test/kotlin/io/github/youndie/shashki/server/feature/receipt/ReceiptOverSmtpTest.kt`

## What it turned out to be

**A documentation defect of the kind this repository keeps finding in its guards, this time in the
instructions for one.** The block now generates the unrelated CA beside the real certificate, sets
the fourth variable, and ends with the thing that separates the two runs above: read the report, not
the exit code — `assumeTrue` skips are green, so `skipped="0"` is the assertion, not `BUILD
SUCCESSFUL`.

Re-verified after the edit by following the corrected block from an empty `/tmp/mailpit-tls`:
`tests=2 skipped=0 failures=0`.

The gate itself stays. An integration test that needs a mail server is honestly gated, and B-14 chose
that deliberately — what was wrong was only that the way back in, written down for the next person,
walked past the control without saying so.
