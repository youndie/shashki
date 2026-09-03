---
id: B-86
title: "The waiting screen's own count reads as a taxi-rank position, not the socket heartbeat it is"
status: done
priority: P3
size: S
stage: stage-6-what-running-it-said
---

# B-86 — The waiting screen's own count reads as a taxi-rank position, not the socket heartbeat it is

Seen raising the stand for a design comparison sweep (2026-09-03): a driver online with no offer
shows, under "on shift", a line like `57 positions taken · position: configured`. Read cold, on a
phone, it reads as a queue — as though 57 other drivers are ahead of this one for the next ride.

`DriverShift.kt`'s own KDoc says what it actually is: `reported` is a count of position updates the
**server acknowledged**, shown specifically so a driver whose socket has quietly died sees a number
that has stopped moving rather than nothing at all (B-54 is the incident this exists to catch —
frames the server refused were still being counted as sent, client-side). That is a good, deliberate
instrument and not a leak; "waiting is deliberately dull… a word and a count and nothing that moves"
is the documented intent, not an oversight.

**What is not deliberate is the word "positions".** Nothing in the kit's D2 or its states shows a
count like this at all — it is engineering telemetry surfaced verbatim because the honest number was
easier to ship than a word for it, and "positions taken" is a phrase that already means something
else in this domain (a taxi rank, a candidate queue) and isn't it.

- AC: the heartbeat stays — a driver whose socket has died should still see the count stop — but its
  label does not borrow a phrase this domain already uses for something else.
- Anchors:
  `shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/screens/DriverShift.kt:52-90,175-183`

## What it turned out to be

**A wording fix, confirmed against the code before being filed as anything more.** The tiles missing
from the same screen (acceptance, sometimes rating) and the absence of a map turned out, on reading
the same file, to be already-documented decisions — B-81's KDoc says acceptance has no server metric
behind it yet, rating is `null` for a fixture driver with none seeded, and the KDoc above the state
says explicitly that offline "had nothing there at all, which its screen document called a decision".
None of those were filed; reporting them would have repeated the mistake #113 already cost this
session once — a screenshot read as a defect without checking whether the gap was already chosen.

Changed `"$taken positions taken"` to `"$taken pings"` in `DriverShift.kt` — the smallest label that
keeps the number honest without the taxi-rank reading. Re-recorded the one golden that carries this
state (`driver_shift_waiting`).
