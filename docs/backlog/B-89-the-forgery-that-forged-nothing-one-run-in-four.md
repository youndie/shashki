---
id: B-89
title: "The signature the acceptance test forges is unchanged one run in four, and the test then accuses the server"
status: done
priority: P2
size: S
stage: stage-6-what-running-it-said
---

# B-89 — The signature the acceptance test forges is unchanged one run in four, and the test then accuses the server

Found by running the stand's dormant guards ([B-88](B-88-the-guards-that-need-the-stand-never-ran.md)):

```
ProtectedRidesTest.a token the rider client signed in for is one this server accepts
  a broken signature was accepted ==> expected: <401 Unauthorized> but was: <201 Created>
```

Read cold this says the server took a token whose signature does not verify — the one failure this
repository would drop everything for. It is the opposite: **nothing was forged, so the server was
right to accept it, and the test is what is wrong.**

```kotlin
val forged = token.dropLast(1) + if (token.last() == 'A') 'B' else 'A'
```

An ES256 signature is 64 bytes and an RS256 one is 256; both leave a remainder of one modulo three,
so base64url spends its **final** character on two significant bits and four of padding. Two
consequences, and the second is the defect:

- that character can only ever be `A`, `Q`, `g` or `w` — the other sixty encodings are unreachable;
- `A` and `B` differ only in padding, so they decode to the same byte.

The rule above turns `A` into `B` and everything else into `A`. When the token ends in `A` — a
quarter of tokens — the "forged" token is byte-identical to the real one after decoding. Measured
over 3 000 random signatures rather than argued:

| mutation | forged nothing (ES256) | forged nothing (RS256) |
|---|---|---|
| last character, as written | **755 / 3 000** | **713 / 3 000** |
| a character mid-signature | 0 / 3 000 | 0 / 3 000 |

- AC: the control's mutation changes the signature's bytes every time, and the reason the last
  character cannot is written where the next person will change it back.
- Anchors: `server/src/test/kotlin/io/github/youndie/shashki/server/feature/auth/ProtectedRidesTest.kt`

## What it turned out to be

**A flaky accusation, which is worse than a flaky failure.** A test that goes red at random is
ignored; a *security* test that goes red at random and says "a broken signature was accepted" is
investigated — it took ten minutes tonight before the arithmetic said the server had done nothing
wrong. The failure mode of this defect is a person distrusting a validator that works.

The mutation now lands mid-segment, where all six bits of the character are significant, so the
decoded signature always differs. The comment beside it carries the arithmetic and the measurement,
because the shorter version — mutate the last character — is the one anybody would write.

**Why it never showed before**: this guard is one of the twelve that only run against a stand
(B-88), so it had been executed by hand a handful of times, each of which had three chances in four
of drawing a token that hides this. The defect needed the suite to be run repeatedly, which is what
B-88 makes possible; it appeared on the second such run.
