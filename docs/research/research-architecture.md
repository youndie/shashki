---
id: research-architecture
title: shashki — architecture research
type: research
status: active
date: 2026-09-01
---

# Research: the architecture of shashki

shashki is a reference ride-hailing service: a rider client, a driver client and a dispatch server
that between them exercise every part of the kotlin.website stack in one product a stranger already
knows how to use. It is not a demo of one library. The point is the seams — a saga that survives the
process dying halfway, a broker that carries the events, server-driven screens beside natively drawn
ones, goldens that fail when the design moves — shown on a domain where "the driver was assigned but
the card was never charged" is a sentence anybody can judge.

This document records **verified facts** (read out of the artefacts named beside them), **decisions
taken**, and **risks**. Anything not verified is called a hypothesis and says where it gets settled.

There is no shashki source tree yet. On a greenfield project a verified fact is one read out of a
dependency at the version that will actually be pinned, or out of that dependency's published
metadata — not out of memory, and not out of the brief. Where the brief turned out to be wrong, the
correction is in §2 with the brief's original wording, because the wrong idea is the part that comes
back.

**Inputs.** The product brief (`shashki-spec.md`), the UI handoff derived from the design kit
(`shashki-ui-tech-handoff.md`), and two MapLibre style documents (`shashki-map-dark.json`,
`shashki-map-light.json`). None of the three is a source: each is a plan, and §1 is what happened
when the plan was checked.

---

## 1. Verified facts

Versions are the ones in the working copies of the stack repositories on 2026-09-01. Where a
repository publishes a snapshot, that is said rather than smoothed over — see Risk 3.

| Library | Version read | Library | Version read |
|---|---|---|---|
| kvadrant-ui | 0.1.0 | kompot | 0.34.1 |
| viddik | 0.3.0 (kvadrant pins 0.3.0.15) | booblik | 0.3.0-SNAPSHOT |
| petich | 0.1.0 | bochka | 0.6.0-SNAPSHOT |
| shildik | 0.2.0 | s3kn | 0.1.0-SNAPSHOT |
| katcher | 0.6.2 | tracy | 0.1.0-SNAPSHOT |
| telek | 0.1.2 | smtpkn | 0.1.0-SNAPSHOT |

The whole stack is on **Kotlin 2.4.10 / Compose Multiplatform 1.12.0**, and so is
`org.maplibre.compose` — which is what makes §1.3's problem a target problem rather than a version
problem.

**Amended 2026-09-01, same day.** The table above is what the working copies said; what shashki
actually pins is in `gradle/libs.versions.toml`, and the two already disagree on one line: viddik is
**0.3.3.19** there, not 0.3.0 — the CI-numbered publish kvadrant-ui's own catalog moved to after this
table was written. The table stays as the record of what was read; the catalog is the fact. This is
Risk 3's rule ("re-read rather than remembered") biting on day one, and the reason B-13 is a checklist
against the catalog rather than against this section.

**Re-verified 2026-09-02 against what actually resolves ([B-13](../backlog/B-13-pin-every-dependency.md)).**
The table above is a reading of working copies; this is a reading of the dependency graph, which is a
different thing and answers the question the table was standing in for.

| Fact | Where verified |
|---|---|
| Across `:protocol`, `:server`, `:shared-ui` and `:auth-client` — 18 940 lines of resolved graph — there is **no `-SNAPSHOT` and no dynamic version** | `./gradlew :server:dependencies` and the same for the other three |
| Nor on the plugin classpath: sborka 0.1.0.23, viddik 0.3.3.19, Kotlin 2.4.10, Compose 1.12.0, KSP 2.3.11, ktlint-gradle 14.2.0 | `./gradlew buildEnvironment` |
| Of the five libraries this table records as `-SNAPSHOT` — booblik, bochka, s3kn, tracy, smtpkn — **none appears in the graph at all**. Neither do katcher, telek, kompot or shildik | same, zero matches each |
| What does resolve from the portfolio is kvadrant-core 0.2.0, viddik 0.3.3.19 and petich 0.1.0.10, all CI-numbered publishes rather than `-SNAPSHOT` coordinates | same |

So the risk this table was raising is not yet *taken*: it arrives with
[B-07](../backlog/B-07-serve-pmtiles-from-bochka.md) (bochka),
[B-10](../backlog/B-10-crash-reports-from-the-browser.md) (katcher) and
[B-14](../backlog/B-14-receipt-over-smtpkn-jvm.md) (smtpkn), each of which adds one of the snapshot
libraries. That is a better place for it than a blanket item, and those three now carry it.

**A timestamped snapshot pins the root module and not its platform variants, and that is a hole in
the fallback above (found 2026-09-02, [B-33](../backlog/B-33-take-the-upstream-fixes.md)).**

The catalog named `io.github.youndie:smtp-client:0.1.0-20260902.062954-3`, it resolved, and the
build compiled **yesterday's code**. The root module's own Gradle metadata points its JVM variant at
`smtp-client-jvm:0.1.0-SNAPSHOT` — the moving coordinate — so the platform artefact came from
whatever the cache had last fetched, which was a build from before the fix this item exists to take.
Nothing failed to resolve and nothing warned; the symptom was a test that passed with
`--refresh-dependencies` and failed without it.

| Fact | Where verified |
|---|---|
| `smtp-core:0.1.0-SNAPSHOT -> …-3` while `smtp-core-jvm:0.1.0-SNAPSHOT` stayed unpinned | `./gradlew :server:dependencies --configuration compileClasspath` |
| The cached `smtp-client-jvm-0.1.0-SNAPSHOT.jar` was written at 02:43 and the fix was published at 06:29 | the file's own mtime on the build box |

A `resolutionStrategy` in `:server` now maps the variants onto the same builds, and the whole graph
of `:server`, `:auth-client` and `:crash-client` contains no `-SNAPSHOT`. It is a workaround for a
library that has released nothing; the real fix is a release. What is worth keeping is the shape:
**"pin the snapshot by build metadata" is advice written for a JVM library, and a Kotlin
Multiplatform one has a second coordinate it does not reach.** Every portfolio library here is
multiplatform.

**What a pinned version still does not guarantee.** `0.2.0` and `0.1.0.10` are release coordinates as
far as Gradle is concerned, so it caches them and never asks again — but they live on a self-hosted
`/snapshots` line, where nothing but convention stops the same coordinate being republished with
different bytes. The mechanism that would catch it is Gradle's dependency verification metadata
(checksums), not dependency locking, which pins coordinates this build has already pinned by hand.
It is **not** added here: it is a second file to regenerate on every dependency change, and the
larger exposure is anyway that the host itself is single-homed — no checksum makes a build work when
the repository is unreachable. Both are named so the next reader weighs them rather than assuming
the pinning covered them.

### 1.1 The design kit's foundation is not the library's foundation

The handoff's §1 opens with the kit's own claim that section 03 is "foundation, **as inherited**" —
that the designer took no decisions in this layer, so the work is to *check* the values and override
nothing. Colour is the one third of that layer where the claim holds.

Verified against the working copy of `youndie/kvadrant-ui` at 0.1.0.

| Fact | Where verified |
|---|---|
| The kit's seven dark brushes are the stock dark tokens, hex for hex: foreground `FFFFFF`, background `000000`, subtle `99FFFFFF`, disabled `66FFFFFF`, chrome `1F1F1F`, border `BFFFFFFF`, inactive `33FFFFFF` | `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/theme/KvadrantTokens.kt` |
| The token the kit calls "inactive" is `inactive`. `semitransparent` is a different token, `AA000000`, and is not a candidate | same file, `object Dark` |
| A dark-theme text box is light in both themes: `textBox = BFFFFFFF` with `textBoxForeground = 000000` | `.../theme/KvadrantColors.kt`, `dark()` |
| `KvadrantTokens` is `internal`. The public surface is `KvadrantColors.dark(accent)`, `KvadrantAccents.*` and `KvadrantTheme.colors` | `.../theme/KvadrantTokens.kt` line 15, `.../theme/KvadrantTheme.kt` |
| **Amber is stock**, `KvadrantAccents.Amber = #F0A30A`, one of twenty published accents; Cyan is `#1BA1E2` | `.../theme/KvadrantColors.kt` `object KvadrantAccents`, `KvadrantTokens.Accents` |
| `Red = #E51400` and `Green = #60A917` are also stock accents — the same two hexes the handoff proposes to hard-code as `ShashkiColors.negative` / `.positive` | `KvadrantTokens.Accents` |
| Tile grid: `TileSize.Small(1) / Medium(2) / Wide(4)` packed by `KvadrantTileGrid` against `COLUMNS = 4` | `.../components/KvadrantTile.kt` |
| Press feedback is the theme's: `KvadrantTheme` provides `LocalIndication = TiltIndication(...)` and `LocalOverscrollFactory = KvadrantOverscrollFactory(...)`; `remastered` defaults to `false` | `.../theme/KvadrantTheme.kt` |
| The font stack is bundled, not loaded by the consumer: Selawik at W200/300/400/600/700 for Latin, a Source Sans 3 variable for Cyrillic at compensated weights (`CYRILLIC_LIGHT_WEIGHT = 330`, `SEMILIGHT = 370`, `NORMAL = 420`, `SEMIBOLD = 640`, `BOLD = 690`) | `.../foundation/KvadrantFonts.kt`, `.../foundation/KvadrantText.kt`, `kvadrant-core/src/commonMain/composeResources/font/` |
| `KvadrantIcons` exposes 41 public entries, against the kit's "40 stock icons we do not touch" | `.../icons/KvadrantIcons.kt` |

**Consequence 1.1a — the kit departs from Metro here, deliberately, and the library has no opt-in
for that departure.** The kit specifies black ink on both accents. `KvadrantColors.onAccent` is
`contrastOn(accent)`, and `contrastOn` is `if (background.luminance() >= 0.5f) Black else White`.
Cyan's luminance is 0.312 and Amber's 0.447, so **both resolve to white**.

| Accent | luminance | white ink | black ink |
|---|---|---|---|
| Cyan `#1BA1E2` | 0.3121 | 2.90:1 — what `contrastOn` returns | 7.24:1 |
| Amber `#F0A30A` | 0.4474 | 2.11:1 — what `contrastOn` returns | 9.95:1 |

**Correction, made while writing this section.** An earlier draft presented the left column as the
library choosing the worse of two available inks, on the arithmetic that the contrast-optimal
threshold is `L > 0.179` rather than 0.5 — and treated the 0.5 threshold as a candidate defect worth
asking about. **Metro put white on cyan.** The threshold is a transcription, `contrastOn` is faithful,
and there is no defect to report. The arithmetic was right and the conclusion drawn from it was
wrong: a library whose premise is fidelity is not improved by a better number.

The same draft also claimed that `KvadrantColors.accessible()` — which reaches WCAG AA by walking the
accent towards black or white — rescues a set that flipping the ink would have rescued without moving
the colour. That does not hold either, and it is worth saying why, because the mistake is easy to
repeat: contrast is symmetric, so "cyan at 2.90:1" is the same number for *white ink on a cyan
surface* and for *cyan ink on a white page*. Those are two different problems with one ratio, and
flipping the ink only addresses the first. Which of them `accessible()` targets was not verified, and
nothing here depends on the answer.

**What survives is narrower and does not need anybody's opinion.** shashki is not reproducing Metro;
it is a product whose kit made the other choice, on a domain where a fare on an accent tile has to be
legible. The library's own accessibility policy is exactly this shape — canonical visual by default,
higher-contrast variants opt-in (its B-11, its research D7) — and it has one opt-in lever,
`accessible()`, which moves the accent. There is no lever that keeps the accent at the kit's hex and
changes the ink, because `onAccent` is a computed property with no parameter behind it (§1.1f). That
is a missing opt-in under a policy the library already states, not a disagreement with it. See
[D3](#d3-kvadrant-ui-grows-the-two-hooks-the-kit-needs).

**Consequence 1.1b — two of the three semantic colours already exist.** `ShashkiColors.negative` and
`.positive` in the handoff are `#E51400` and `#60A917`, which are `KvadrantAccents.Red` and
`KvadrantAccents.Green`. Writing them as literals creates a second place where the same number
lives. See [D4](#d4-semantic-colours-are-named-stock-accents-not-literals).

#### Typography: the sizes are stock, four of the seven pairings are not

| Kit style | Kit sp / weight | Nearest stock slot | Verdict |
|---|---|---|---|
| pageTitle | 54 / W200 | `pivotHeader` 54 / W300 | size stock, **weight new** |
| figure | 32 / W200 | `extraLarge` 32 / W300 | size stock, **weight new** |
| stateHeadline | 24 / W300 | `large` 24 / W300 | matches |
| pivotItem / tileLabel | 19 / W300 | `mediumLarge` 19 / W400 | size stock, **weight new** |
| rowEmphasis | 17 / W400 | `title` 17 / W600 | size stock, **weight new** |
| body | 15 / W400 | `normal` 15 / W400 | matches |
| meta | 14 / W400 | `subtle` 14 / W400 | matches |

Verified against `.../theme/KvadrantTypography.kt` (`KvadrantTypography.default`, `KvadrantFontSizes`,
`KvadrantWeights`) and `KvadrantTokens.FontSizesSp`. Every *size* the kit asks for is in the stock
ramp — 14, 15, 17, 19, 24, 32, 54 — and every *weight* exists in `KvadrantWeights` and in the bundled
Selawik files. What does not exist is four of the seven pairings.

**Trap.** `KvadrantTypography.pageTitle` is **14 sp / W400** — Metro's `ApplicationTitle`, the small
line above a page header. The kit's `pageTitle` is 54 / W200. The two documents use one word for two
objects, and a mapping written from the names alone lands the wrong one on every page header in the
product.

**The same trap, one slot over — found in review after `toKvadrant` was written.** The projection
puts the kit's `rowEmphasis` (17 / W400) into the library's `title` slot because the sizes match. But
a slot is not a size: it is the style a library *component* reads, and which components read `title`
was not checked — if `KvadrantListItem` draws its primary line from it, every stock list row in the
product comes out at 17 / W400 where the kit's list title is 15 / W400 (`body`), the row R3 draws.
Matching the kit's ramp to the library's slots by size assumes each slot's *usage* is the kit's usage
of that size, which is exactly the assumption `pageTitle` just broke. Settled by a golden, not by
reading: [B-21](../backlog/B-21-ramp-projection-against-stock-components.md) renders the stock
components shashki will actually use under `ShashkiTheme` beside the kit's rows, before B-04 builds on
them.

**Settled, same day.** `KvadrantListItem` reads `normal`/`subtle` and the kit's row comes out exactly
right — the specific fear was unfounded. What the golden found instead were two components that use
a *correct* slot at a size the kit does not: `KvadrantPivotHeaders` draws pivot headers at the
library's 54 sp (the kit's page title) where the kit draws them at 19; `KvadrantButton` draws at an
emboldened 19 where the kit's button is 15. Neither can be remapped without moving something else
that reads the slot, so both are withdrawn from the projection and drawn by shashki's own composables
— the projection's KDoc names them. The lesson generalises: the projection is a mapping of *sizes*,
and it is correct; what has to be checked per component is *usage*, and a golden is the only reading
of usage that stays true across the library's versions.

#### Metrics: 12 dp is not a stock number at any scale

| Fact | Where verified |
|---|---|
| `KvadrantMetrics()` defaults: `margin = 9.dp`, `tileGap = 9.dp`, `tileSmall = 74.25.dp`, `tileMedium = 157.5.dp`, `tileWide = 324.dp` | `.../theme/KvadrantMetrics.kt` |
| The library's own note derives 9 dp from `PhoneMargin` **12 px** × 0.75, not from 16 px | same file, KDoc on `margin` |
| `scaledToWidth(width) = scaled(width / (margin * 2 + tileWide))`; the divisor is 342 dp, and the KDoc says so and calls the difference from the 360 dp canvas unexplained | same file |
| `KvadrantTheme` multiplies the type ramp by `metrics.scale`: `typography.scaled(metrics.scale)` | `.../theme/KvadrantTheme.kt` |
| App bar numbers are private vals in the component, not tokens in `KvadrantMetrics`: `HEIGHT = 54.dp // 72 px`, `BUTTON = 36.dp // 48 px`, `RING = 1.125.dp // 1.5 px`, `KvadrantAppBarGlyphSize = 19.5.dp` | `.../components/KvadrantAppBar.kt` |

**Consequence 1.1c — every spacing number in the kit is exactly 4/3 of the library's, and the type
ramp is not.** Read against `KvadrantMetrics` and `KvadrantAppBar`, the kit's layout numbers line up
one to one:

| Kit, section 03/04 | Stock dp | Ratio |
|---|---|---|
| page margin 12 | `margin` 9 | 1.3333 |
| tile gap 12 | `tileGap` 9 | 1.3333 |
| app-bar button 48 | `BUTTON` 36 | 1.3333 |
| app-bar ring 1.5 | `RING` 1.125 | 1.3333 |
| glyph box 26 | `KvadrantAppBarGlyphSize` 19.5 | 1.3333 |
| app bar height 54 | `HEIGHT` 54 | **1.0** |
| type ramp 54 / 32 / 24 / 19 / 17 / 15 / 14 | the same seven sizes | **1.0** |

4/3 is 1 / 0.75 — the kit's own header states `px → dp = 0.75` and then adds "nothing on this row is
a decision". Two readings fit: the conversion was skipped on those five rows, so they are Metro
pixels labelled dp; or the layout is deliberately scaled by 4/3 while the type ramp is left alone.

**Answered: 12 dp, as drawn.** The reasoning below stands and the decision went the other way from
it, which is worth keeping rather than tidying. The evidence says the kit's spacing is Metro's pixel
column with the conversion skipped; the choice says it does not matter. The kit is this product's
design authority, its artboards are what the goldens are diffed against, and the look was approved at
12. Metro fidelity is kvadrant's job. shashki's is to look like the kit, and a screen that is
authentically tighter than the drawing is a screen the designer has to accept twice.

**The evidence, kept because the next reader will re-derive it.** kvadrant generates its tokens from
`reference/metro-compose-brief/references/metro-tokens.json`, and every one of the kit's five numbers
is the raw pixel value in that file: `pageMarginPx` 12, tile `gapPx` 12, `appBarIconPx` 48,
`appBarGlyphPx` 26. The fifth, the app bar's ring at 1.5, is 1.5 px in the component's own comment
and — as kvadrant B-49 established when this was checked — is in no token dump at all; wherever the
kit read it, it was not the dp column either. Meanwhile the
kit's type ramp matches the *converted* column exactly, 14 / 15 / 17 / 19 / 24 / 32 / 54 dp. A
deliberate 4/3 scale-up that happens to land on the px column for spacing while leaving type at the
dp column is not a story that holds together; a skipped `× 0.75` on five rows is. It is still the
designer's call to confirm, and [B-15](../backlog/B-15-answer-the-kits-open-questions.md) is where
that happens — but the reading to confirm is now named rather than balanced.

**The scaling knob cannot deliver either reading.** `scaledToWidth(390.dp)` fits the row at
390 / 342 = 1.1404 and gives a 10.26 dp margin, not 12; a 12 dp margin implies a 456 dp row. And the
knob is not independent — at any factor `KvadrantTheme` rescales the ramp with it, so the 4/3 reading
would put the kit's 54 sp page title at 72 sp, which the kit's own invariant ("nothing between 32 and
54") rules out. Whichever reading wins, the numbers are written down rather than derived.

**One number is ambiguous twice.** 48 dp is also `KvadrantMetrics.touchTargetMin`, the modern
minimum touch target the library enforces around a 36 dp visual. A kit row reading "48 dp circle,
1.5 dp ring" may be naming the target or the ring, and those are different pictures.

**Consequence 1.1f — two of the four divergences are API gaps, and two are not.** Which of them can
be expressed against the published library at all was checked rather than assumed:

| What has to differ | Expressible today? | Why |
|---|---|---|
| the type ramp | **yes** | `KvadrantTypography` is a `data class` with a public constructor and public `val`s — `copy()` replaces any slot |
| page margin, tile gap | **yes** | `KvadrantMetrics` likewise, and `KvadrantTheme` takes a `metrics` argument |
| ink on an accent surface | **was no, now yes** | `onAccent` was a computed `val … get() = contrastOn(accent)`. It is a constructor parameter now, defaulting to the same derivation |
| app bar height, button, ring | **was no, now yes** | `HEIGHT`, `BUTTON` and `RING` were `private val`s inside `KvadrantAppBar.kt`. They are `appBarHeight`, `appBarMiniHeight`, `appBarButton`, `appBarGlyph` and `appBarRing` in `KvadrantMetrics` now, and `scaled()` carries them |

**Closed upstream, and this table is the record of what the gap was.** kvadrant-ui B-48 and B-49
landed both hooks additively, with every default unchanged: `KvadrantColors(onAccent = …)` and the
five `appBar*` fields on `KvadrantMetrics`. Two breaking changes, one moved golden, and
`KvadrantAppBarGlyphSize` deprecated rather than deleted. They are **unreleased** — the change sits
on `main` above 0.1.0 — so shashki's dependency on them is a version that does not exist yet, which
is [Risk 3](#risk-3-half-the-stack-is-a-snapshot)'s problem and not a new one.

**The ring turned out to be nobody's transcription.** B-49 was filed claiming all five numbers were
upstream of the code; four are, and the fifth had no source. Settled by looking rather than by
failing to find: the WP8 SDK's design assembly carries ten control templates and the ApplicationBar
is not among them, because on the phone it was a shell control rather than a XAML one — so there is
no template to transcribe, and 1.5 px is the library's own number, now marked as such in KDoc. That
sharpens §1.1c rather than disturbing it: the kit's ring of 1.5 cannot have come from Microsoft
either, so it came from the component's `// 1.5 px` comment — the pixel column again.

Verified in `.../theme/KvadrantColors.kt`, `.../theme/KvadrantMetrics.kt` and
`.../components/KvadrantAppBar.kt`. See [D3](#d3-kvadrant-ui-grows-the-two-hooks-the-kit-needs).

**Consequence 1.1d — the app bar does not scale with the theme.** Its height, button and ring are
constants inside the component, so a scaled theme moves the page around a fixed bar. At the kit's own
numbers this is invisible (54 dp is 54 dp), which is exactly why it is worth writing down before
somebody reaches for `scaled()` to fix 1.1c.

### 1.2 Goldens: what viddik makes portable and what it does not

Verified against the working copy of `youndie/viddik` at 0.3.0 and `youndie/kvadrant-ui`'s own notes.

| Fact | Where verified |
|---|---|
| The capture engine renders through `ComposeScene` and skiko on a plain JVM. `viddik-testing-core` publishes **JVM variants only** at every published version; `viddik-testing-core-android` is a 404 | `viddik/README.md` ("Compatibility", "Declaring the dependencies by hand"), `kvadrant-ui/gradle/libs.versions.toml` |
| Therefore neither the Android nor the wasm renderer can have goldens — kvadrant's own build file says so beside its `wasmJs` target | `kvadrant-ui/kvadrant-core/build.gradle.kts` |
| 0.3.x is bound to Compose Multiplatform 1.12.x / Kotlin 2.4.x, and a mismatch appears at runtime (`NoSuchMethodError` on the first frame), not at compile time | `viddik/README.md`, compatibility table |
| Reading metadata off `@Preview` needs 0.3.0+, and that `@Preview` is the one CMP 1.12 ships in `commonMain` | `viddik/README.md` |
| Goldens are cross-OS portable **given** a bundled font run through `normalizeVerticalMetrics()`; glyph rasterisation is neutralised by the capture engine itself | `viddik/README.md`, "Cross-platform goldens" |
| Bundling the font was **not enough** in practice: kvadrant's first Linux CI run failed on twenty-odd images, all of them text, because the rasteriser differs even when the file does not | `kvadrant-ui/CLAUDE.md`; `kvadrant-ui/kvadrant-core/src/desktopTest/kotlin/io/github/youndie/kvadrant/type/PortableTypography.kt` |
| The fix is `ViddikPlatformTextStyle` — a `PlatformTextStyle` pinning `FontHinting` and `FontSmoothing` — applied to **every** slot of the ramp and to every hand-built `TextStyle` | same file |
| That helper is `internal` and lives in `desktopTest`. It is not published | same file, `internal fun portableTypography` |
| kvadrant runs `./gradlew check` on macOS because its **calibration** tests fit the Cyrillic companion's weight by comparing ink coverage, and those numbers came from a mac | `kvadrant-ui/CLAUDE.md` |
| `ViddikGlyphCoverage.missingGlyphs(text)` reads the bundled font's `cmap` and reports characters that would fall through to a host font | `viddik/README.md` |
| A match is ≤ 0.05 % of pixels with a ±2 per-channel allowance; for scale, one extra character in a button label moves 1.32 % of the pixels | `viddik/README.md` |

**Consequence 1.2a — the handoff's reason for recording on a mac is not the consumer's reason.**
The handoff says goldens are written on macOS because of "kvadrant's Cyrillic/FreeType limitation".
The limitation is real and it belongs to *kvadrant's own suite*: it calibrates a variable font's
weight by counting ink, and ink counts differ per rasteriser. A consumer that only photographs
screens has the other problem — hinting and smoothing — and that one has a fix, `portableTypography`.
Whether shashki's goldens are portable is therefore an open measurement, not an inherited fact.
See [Risk 2](#risk-2-the-golden-suite-may-be-tied-to-one-machine).

**Consequence 1.2b — shashki re-implements the pin.** `portableTypography` cannot be imported.
It is ten lines over `ViddikPlatformTextStyle`, which *is* public in `ru.workinprogress.viddik.core`,
and shashki needs its own version anyway because [D2](#d2-kvadrant-ui-is-the-base-and-it-is-pulled-towards-the-kit)
gives it a ramp built by hand rather than by `KvadrantTypography.default`.

**Consequence 1.2c — the currency sign was a defect in waiting, and the answer was to change the
currency.** Every fare in the kit carries `₽` (U+20BD), thirteen times. The bundled fonts were read
directly, `cmap` by `cmap`: **none of the five Selawik faces contains U+20BD**; only the Source Sans 3
companion does. `KvadrantText` splits a string **by script** and hands Cyrillic runs to that
companion, so `₽` — neither Latin nor Cyrillic — stays on the Latin run, in the one font that lacks
it, and is drawn by whatever the host offers. The library predicted this exact failure in the same
file's KDoc: "the rule is coverage, and *is it Cyrillic* is only an approximation of it… anything
added that is neither Latin nor Cyrillic has to be checked against Selawik before it is drawn", with
`U+25CF`, the password mask, as the precedent that already fell out of both runs once.

**Resolved by decision: the product prices in `$`.** `U+0024` is covered by all five Selawik faces,
as are every other non-alphabetic character the kit uses — `·`, `—`, `×`, `…` — so nothing else in
the kit falls through. The defect is gone rather than fixed, and this paragraph stays because the
*mechanism* is what [B-05](../backlog/B-05-glyph-coverage-guard.md) guards against: the next
character somebody adds gets the same treatment, and the failure is silent — the screenshot renders.

`€` is equally covered and would sit better beside a European city; the choice of `$` was taken
before the city was, and changing it later costs one constant and a re-record.

**Consequence 1.2d — the licence plate was the same trap wearing Latin clothes, and is also
resolved.** The kit set the plate as `А 123 ВС 177`, whose `А`, `В`, `С` are **Cyrillic** codepoints
indistinguishable from the Latin letters. `splitByScript` would have cut it into three runs and drawn
one short string in two typefaces at one weight — in the element the kit calls "the only inverted
element on a rider screen, which is why it is the thing you find first". **Resolved by decision: the
plate is European, in Latin letters**, so it is one run in one font.

**Consequence 1.2e — a European city costs nothing typographically.** Checked before choosing one:
`selawik_regular` covers `č ž š ä ö ü é å ø ł ą ő ș ć`, so street names with diacritics stay on the
Latin run. This was worth checking rather than assuming, because it is the same class of defect as
the two above and the same silence.

**Amended 2026-09-02, against the city rather than against a guess at it.**
[B-06](../backlog/B-06-city-extract-and-tiles.md) collected the alphabet of all **3 629 distinct
street names** in the Ljubljana extract, not a plausible sample of diacritics. It is
`ć Č č ř Š š Ž ž` plus an en dash — `ř` was not on the list above and arrives with *Dvořákova*, and
`ž Ž` appear in upper case as well as lower. All of them are in `selawik_light.ttf` and
`selawik_semilight.ttf`, the two faces the fixtures actually draw with, so the conclusion holds and
is now measured: nothing in a Ljubljana street name falls through to a host font. The lesson is the
list, not the verdict — a hand-written set of diacritics was short by one letter that the city uses
eleven times.

### 1.3 The browser target — MapLibre Compose does not publish for Kotlin/Wasm

This is the finding that moves the plan, so it is the one with the most addresses.

| Fact | Where verified |
|---|---|
| `org.maplibre.compose:maplibre-compose` 0.15.0 publishes **android, jvm, iosArm64, iosSimulatorArm64, js**. `wasmJs` is not among them | `https://klibs.io/package/org.maplibre.compose/maplibre-compose` |
| The project's own documentation: maps render through MapLibre Native on Android/iOS/Desktop and through **MapLibre GL JS on Kotlin/JS**; **"Kotlin/Wasm: not yet supported"**; offline downloads exist on every platform except the browser | `https://maplibre.org/maplibre-compose/` |
| Android and iOS are Beta; Desktop and Web are **Alpha**, and the README attributes that to relying on implementation details in Compose and Skia | `https://github.com/maplibre/maplibre-compose` README |
| Issue **#209 "Support Wasm"** is open — filed 2024-12-31, last touched 2026-08-25 | `https://github.com/maplibre/maplibre-compose/issues/209` |
| Its first blocker, spatial-k, was cleared on 2025-09-23. The maintainer's assessment on 2025-12-15: "We don't yet have WASM support… Kotlin's wasmJs external declarations are more limiting than its JS external declarations, so I'm unsure how much of the JS work will trivially port to WASM" | issue #209, comments |
| PR **#1081**, opened 2026-08-25, moves the GL JS platform to `webMain`, publishes the browser libraries for Kotlin/Wasm and composites through Compose's `WebGLRenderTarget` (from #1114). It reports 236 JS browser tests passing, the Kotlin/Wasm library and demo compiling, and the wasm demo rendering a map that survives viewport resize | `https://github.com/maplibre/maplibre-compose/issues/1081` |
| It is pinned to the JetBrains build `1.12.10-alpha01+dev4710` and carries `blocked-upstream` "until a non-prerelease Compose release contains merge commit `dca97b20a50006b78bd0e777aeafbf2749d77915`" | same |
| The newest non-prerelease Compose Multiplatform is **1.12.0, published 2026-08-25** — one week before this document | `https://api.github.com/repos/JetBrains/compose-multiplatform/releases` |
| maplibre-compose itself builds on Kotlin 2.4.10 / Compose 1.12.0 / maplibre-gl-js 6.6.0 — the same line as the rest of the stack | `https://raw.githubusercontent.com/maplibre/maplibre-compose/main/gradle/libs.versions.toml` |

**Consequence 1.3a0 — checked a second way, on 2026-09-02, and the absence is older than the
project.** klibs.io reports variants for one version; the Maven Central directory listing reports
every artefact ever published under a group, so it answers a different question — has the map *ever*
crossed to wasm, under any name. Both namespaces were listed:

| Fact | Where verified |
|---|---|
| `org.maplibre.compose` publishes `maplibre-compose-js` at 0.11.0 … 0.15.0 and **no `maplibre-compose-wasm-js` at any version** | `https://repo1.maven.org/maven2/org/maplibre/compose/` |
| The predecessor namespace `dev.sargunv.maplibre-compose` likewise has `maplibre-compose-js` and no `maplibre-compose-wasm-js` — **but it does publish `maplibre-compose-expressions-wasm-js` and `compose-html-interop-wasm-js`** | `https://repo1.maven.org/maven2/dev/sargunv/maplibre-compose/` |

The second row is the informative one. The parts of this library that *have* shipped for Kotlin/Wasm
are the expression compiler, which is pure Kotlin and touches no renderer, and the DOM-interop
helper. What never crossed is the map. That is not a release that has not happened yet; it is the
same boundary #209 describes from the inside, visible from the outside.

**Consequence 1.3a1 — the `js` map is a well-behaved Compose element, and the earlier reading of
route 3 as a category was wrong about that.** §1.8c read WorldWind and found a full-window WebGL
canvas inserted behind Compose, and D1's route 3 was written as if that were what "the map on the
web" means. It is what WorldWind does. It is **not** what maplibre-compose does on `js`:

| Fact | Where verified |
|---|---|
| The browser map draws into an ordinary Compose `Canvas(modifier = modifier.onSizeChanged { … })`, and the GL frame is blitted **into Compose's own canvas** — `drawIntoCanvas { canvas.skiaCanvas.drawImageRect(image = target.image, …) }` | `lib/maplibre-compose/src/jsMain/.../gljs/GlJsMapSurface.kt` |
| Input arrives through a Compose pointer modifier — `modifier.mapInput(session, options.gestureOptions, …)` — not through DOM listeners | `.../jsMain/.../map/JsMapView.kt` |
| Their own build file says it in one sentence: "The browser platform **composites MapLibre GL JS into the Compose scene**, so its tests need a real WebGL context" | `lib/maplibre-compose/build.gradle.kts` |

So on `js` the map *is* a sized element and Compose draws over it normally. Route 3's objection is
therefore not "the map cannot be a sized element" — that is WorldWind's problem specifically — but
the one already in 1.3b: this stack has no `js` target and would have to grow one in two libraries.
The distinction matters because it says which of the two things would have to change.

**Consequence 1.3a2 — the `jvm` map renders through the host's GPU, reached by reflection.** Route 1
is the one that works on released artefacts today, so what it costs was read the same way:

| Fact | Where verified |
|---|---|
| The desktop host reaches Compose's GPU context through Skiko internals: "Compose Desktop exposes **no supported hook** for any of this, so it is read reflectively" | `.../jvmMain/.../desktop/skiko/AwtComposeMapPresentationHost.kt` KDoc |
| The backend is chosen by operating system — `LINUX -> OPENGL`, `MACOS -> METAL`, `WINDOWS -> DIRECT3D12` | same file, `HostOperatingSystem.composeBackend` |
| "One native runtime is loaded per test process; `maplibre.desktop.backend` selects which, and **a CI matrix adds processes for additional applicable backends**" | `lib/maplibre-compose/build.gradle.kts` |

The last row is the one that decides something here. A golden in this project is one image compared
byte-for-byte on the mac, on the Linux box and on CI — that is what B-02 measured and what
`verifyOnCheck = true` rests on. A map rendered by Metal on one host and OpenGL on another is not
that image, and the library's own answer to the difference is a process per backend, which is the
opposite of one golden.

**Consequence 1.3a — the brief's map row is wrong as written.** It says the official wrapper has "a
wasm target (on the web, bindings to maplibre-gl-js)". The bindings exist; the target is `js`. On
released artefacts there is no way to put this library into a Kotlin/Wasm bundle.

**Consequence 1.3b — Kotlin/JS is not the way out.** `kvadrant-core` builds `jvm("desktop")`,
`wasmJs`, `iosArm64`, `iosSimulatorArm64` and `android`
(`kvadrant-ui/kvadrant-core/build.gradle.kts`); `kompot-client` builds `jvm("desktop")`, `iosArm64`
and `wasmJs` (`kompot/kompot-client/build.gradle.kts`). Neither has a `js` target. Retargeting the
clients to Kotlin/JS to reach the map means adding a target to two libraries and re-verifying both.

**Consequence 1.3c — the spike changes its question.** The brief made "MapLibre Compose in wasm:
map, markers, route" step one, to retire the main risk. That spike as phrased has a known answer:
no. What is worth building instead is a comparison — see [D1](#d1-the-browser-is-a-decision-with-a-date-not-a-precondition).

**Consequence 1.3d — the style documents are unaffected.** `shashki-map-dark.json` and
`shashki-map-light.json` are MapLibre style v8 with a `pmtiles://` source, a `route` GeoJSON source
carrying a `phase` property of `travelled`/`ahead`, and a `cars` GeoJSON source. Nothing in either is
specific to a Kotlin target; both remain valid whichever route §1.3 takes. Both name a glyph endpoint
that does not exist yet and say so in their own metadata: the PBFs must be generated from Source Sans
3, and until then `text-font` has to fall back to `["Noto Sans Regular"]`.

### 1.4 petich carries the order saga as the brief describes it

| Fact | Where verified |
|---|---|
| The phases are exactly `ENRICHMENT, VALIDATION, AUTHORIZATION, EXECUTION, POST_PROCESSING` | `petich/petich-core/src/commonMain/kotlin/Petich.kt`, `enum class PetichPhase` |
| A saga can pause for a human and continue on a later HTTP request, holding neither a thread nor a database connection; a suspended saga nobody returns to is rolled back by a background sweeper | `petich/README.md` |
| With an outbox-aware repository the intent to emit an event is written in the same transaction as the state change. `PetichEngineConfig(requireOutbox = true)` refuses to build an engine whose repository cannot store events; `PetichEngineMetrics.onDroppedEvents` counts the fallback. Both are off by default | `petich/README.md` |
| Default per-phase timeouts: ENRICHMENT 1000 ms, VALIDATION 2000 ms, AUTHORIZATION 30000 ms, EXECUTION 10000 ms | `petich/petich-core/src/commonMain/kotlin/Petich.kt`, `PetichPhase.timeoutMs` |
| `petich-postgres` is the outbox-aware repository, on Exposed | `petich/README.md`, module table |

**Consequence 1.4a — the 15-second offer cannot be a blocking step.** EXECUTION's default timeout is
10 s, and a cascade is several offers deep. Waiting for a driver has to be the engine's suspend/resume
with its own deadline, which is the mechanism the README describes, rather than a step that sleeps.
The distinction is invisible while one driver accepts immediately and decides the whole matching
design. See [D5](#d5-the-driver-offer-is-a-suspended-saga-not-a-step-that-waits).

**Consequence 1.4b — `requireOutbox = true` is the setting the brief implies and the default does
not give.** The brief has the outbox publish `ride-events` into booblik; with the default the engine
silently drops events when the repository cannot store them, and the saga still completes correctly.
Turn it on at construction.

**Consequence 1.4e — recovery after a dead process is `process()` re-reading the row, and a
suspended row is not that.** Verified against a real Postgres in B-11: `PetichEngine.process()` on
a `PROCESSING` row continues from the persisted phase and interceptor index, so a saga killed after
AUTHORIZATION committed resumes at EXECUTION with the hold it already took and does not take a
second one. A row left by `InterceptorResult.Suspend` is `PENDING_SIGNATURE` and re-processing it
without a resume payload is a `SystemFailure` — correctly, because that row is waiting for a human,
not for a restart. The first draft of the recovery test conflated the two and the engine refused the
conflation. The distinction matters for B-12: the sweeper handles the suspended shape, and the
`PROCESSING` shape has no sweeper because it needs none — the next call for that id is the recovery.

**Consequence 1.4f — petich's expiry is a rollback, not a cascade, so the per-offer deadline is the
application's.** `expireSuspended` sends an expired suspension straight to `triggerCompensation`.
The brief wants "decline → cascade to the next driver" and the kit draws fifteen seconds per driver
inside ninety of asking; those are two clocks. Built in B-12 as: an in-process timer per offer that
resumes the saga with `IGNORED` (the cascade), and petich's `ttl` set to the ninety-second budget
(the rollback). The `Resuspend` result is what makes the cascade a loop inside one step rather than a
step per candidate. After a restart the timers are gone and the budget is what survives — a
degradation, named, and the only thing petich-scheduler would buy back.

**Consequence 1.4c — one ride is two sagas and a stretch of no saga, and `RideStatus` had said
otherwise.** The first cut of `protocol/.../Ride.kt` documented the status enum as "the order of the
saga". It is not: the *order saga* runs the five phases once, `REQUESTED → ASSIGNED` — quote, hold,
match, the suspended offer of D5. `ARRIVING → ARRIVED → IN_PROGRESS` is the trip, driven by the
driver's transitions and by location, with nothing to compensate. `COMPLETED` opens the *settlement
saga* — capture, payout, receipt, events. Cancellation is therefore two mechanisms under one word:
before `ASSIGNED` it is the order saga compensating from the middle; after it, a trip ending early
and a settlement that charges a fee. B-11's acceptance — "no held payment and no reserved driver at
any phase boundary" — is a claim about the first saga only, and the KDoc now says which is which.

**Consequence 1.4c1 — the second saga was built and the trip turned out to need a table
(2026-09-02, [B-37](../backlog/B-37-the-settlement-saga.md)).** §1.4c described three things and only
the first existed. What building the other two found:

| Fact | Where |
|---|---|
| The trip has no saga and needs a **row**: four states, driver-driven, nothing to compensate. The row appears on the driver's first transition, because creating it inside the order saga would be a side effect with no compensation in a saga step | `trips`, `TripRepository` |
| The ride's status is the order saga's row **overlaid** by the trip's, and only while the saga says `ASSIGNED`. A cancelled saga stays cancelled whatever a stale trip row says | `PetichRideRepository` |
| One petich engine runs **both** sagas: `supports(payload)` is what the interceptor list is filtered by, so two step lists in one engine is the design rather than a compromise | `sagaEngine`, renamed from `orderSagaEngine` |
| A capture needs an **amount**. The first version captured the whole hold, which is right for a fare and charges a rider the entire journey for a car they sent away | `PaymentGateway.capture(hold, amountCents)` |
| A settlement's AUTHORIZATION compensation is a **refund**, not a release: the money moved. `PaymentGateway` grew a fourth method rather than reusing the third | `CaptureStep.compensate` |

**The amount is the finding worth keeping.** `capture(hold)` read correctly, passed the fare tests,
and was wrong for the other half of §1.4c — the cancellation fee — in a way no fare test could see.
It was the *fee* test that caught it, expecting a quarter and getting the lot: a settlement is five
phases and one number, and the number was the one thing not being carried.

**And three pieces were already written and joined to nothing**: `capture` implemented since B-11 and
called by nobody, `SendReceiptUseCase` tested against a real SMTP server (B-14) and bound in no DI
module, `PayoutRepository` not existing at all. That is the same shape as
[B-32](../backlog/B-32-which-screens-the-server-sends.md)'s kompot finding and
[§1.7e](#consequence-17e)'s 409 — a mechanism built at one end and joined at neither.

**Consequence 1.4d — EXECUTION has nothing to offer until something produces candidates.** The
offer cascade (D5, B-12) is only a cascade if there are several drivers to cascade over: a geo-index
of online drivers, a candidate query by class and distance, and — for a demo with no real drivers —
a simulator that keeps virtual cars moving on the real road graph. None of that was an item.
[B-20](../backlog/B-20-matching-geo-index-and-driver-simulator.md) is; it blocks B-12, not B-11,
because the saga itself can be built and killed at phase boundaries against a stub candidate list.
Routing and ETA, verified in §1.6 as GraphHopper embedded, are
[B-23](../backlog/B-23-routes-and-eta-on-embedded-graphhopper.md), and the pricing step of
ENRICHMENT is the first consumer.

### 1.5 kompot, and the parts of it shashki does not need

| Fact | Where verified |
|---|---|
| Version 0.34.1 | `kompot/gradle.properties` |
| `@KompotComponentMarker` is real and drives a KSP registry; the processor rejects a marked class that implements neither `KompotComponent` nor `KompotComponentRenderer<T>` | `kompot/kompot-registry-processor/src/main/kotlin/io/github/youndie/kompot/registry/processor/KompotRegistrySymbolProcessor.kt` |
| `kompot-client` targets `jvm("desktop")`, `iosArm64`, `wasmJs { browser() }` | `kompot/kompot-client/build.gradle.kts` |
| Live updates are three modules: `kompot-realtime` (the frame contract), `kompot-realtime-server` (delivery to one instance's subscribers plus the bus contract), `kompot-realtime-redis` (the pub/sub bus for more than one instance) | `kompot/README.md`, module table |

**Consequence 1.4g — the geo-index is a grid, and the geohash string is deferred on purpose.**
Built in B-20. The property research §1.6a asks for is that positions never leave the process and
never enter a topic; the property a query needs is that it touches a bounded number of buckets. A
lat/lon grid gives both. A base-32 geohash *string* additionally gives a sortable key that a shared
store can range-scan, which buys nothing until the index is shared — and if it ever moves to Redis
(the shape `kompot-realtime-redis` has for the same reason, §1.5a), the string is the change. Written
down because "we should have used geohashes" is a review comment that arrives without the second
half.

**Consequence 1.5a — no Redis.** The brief's architecture is one Ktor process. Per-user live updates
inside a single instance are `kompot-realtime-server`'s job on its own; `kompot-realtime-redis` exists
for the case shashki deliberately does not have. Pulling it in for a demo would add an infrastructure
dependency to demonstrate something the demo does not do.

### 1.6 Which of the remaining libraries can reach a browser, and which cannot

The brief assumes several of these run in the wasm clients. Most do not have the target.

| Library | Targets published | Where verified |
|---|---|---|
| `booblik-client` | **JVM only** — the module is `kotlin("jvm")` | `booblik/booblik-client/build.gradle.kts` |
| katcher client | jvm, linuxX64, linuxArm64, macosX64, macosArm64, iosArm64, iosSimulatorArm64, iosX64, mingwX64 — **no `wasmJs`, no `js`** | `katcher/client/build.gradle.kts` |
| shildik | jvm, linuxX64, linuxArm64, macosArm64; `ktor-role-based-auth` JVM only; `storage-sqlx4k` and `server-boot` jvm + linuxX64; `distribution` linuxX64 | `shildik/README.md` §Targets, `shildik/oidc-auth-client/build.gradle.kts` |
| smtpkn | `linuxX64` is "the platform this is built for and the only one it is claimed to work on"; `jvm` shares the code and runs in CI but is "not claimed yet only because nothing has been released". 181 tests on linuxX64, 175 on the JVM | `smtp-client/build.gradle.kts`, `smtp-tls-jvm` |
| bochka | serves `Range`, conditional reads and writes, SigV4 including `aws-chunked`; `bochka-embedded` starts a server on a random port from a test and stops it after | `bochka/README.md` |
| GraphHopper | Apache-2.0, "use it as Java library or standalone web server", not archived | `https://api.github.com/repos/graphhopper/graphhopper` |
| katcher ingest | `POST {serverUrl}/api/reports` | `katcher/README.md` |
| shildik token endpoint shape | `POST /realms/<realm>/protocol/openid-connect/token`; image `ghcr.io/youndie/shildik` | `shildik/README.md` |

**Consequence 1.6e — GraphHopper embedded, measured 2026-09-02 ([B-23](../backlog/B-23-routes-and-eta-on-embedded-graphhopper.md)).**

| Fact | Where verified |
|---|---|
| `com.graphhopper:graphhopper-core` 11.0 embeds in the Ktor process; a car profile is `Profile("car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json"))` with `setEncodedValuesString("car_access, car_average_speed, road_access")` — the three names `car.json`'s own header comment lists | the jar's `com/graphhopper/custom_models/car.json` |
| On Ljubljana's extract: import **3 168 ms**, opening the prepared graph **22 ms**, a centre-to-airport route **2.57 ms median / 6.6 ms worst of 201**, 22 806 m over 364 points | `CityGraphMeasurement`, on the Linux box |
| **A point outside the graph's bounding box is refused before any snapping is attempted** — "Point 0 is out of bounds" — while a point *inside* it snaps from as far as 1 500 m off the nearest road | measured against the four-node test fixture |

The third row is the one that cost time and is the one worth keeping. The two behaviours look alike
from the outside — both come back as "no route" — but they mean opposite things: outside the box is a
request for a city this server does not have, and far-from-a-road inside the box is answered
generously. A test whose points strayed outside the fixture's box got straight lines from a fallback
and would have passed while proving nothing, which is how it was found.

**The 50 ms criterion holds with two orders of magnitude to spare**, so the interesting cost is not
the route but the start: 3.2 seconds of import that the prepared directory turns into 22 ms. That is
the difference between a container that answers immediately and one that does not, and it is why the
graph directory is configuration rather than a temporary file.

**Consequence 1.6e1 — the graph goes in the image, and a graph is only valid for the profile that
built it (2026-09-02, [B-35](../backlog/B-35-the-server-as-an-image.md)).** §1.6e measured 3 168 ms
to import and 22 ms to open a prepared directory and concluded the directory is configuration rather
than a temporary file. Packaging it found the part that measurement could not:

| Fact | How it presented |
|---|---|
| GraphHopper stores a hash of the profile beside the data and refuses a mismatch | the first image baked a graph that happened to be on the build machine, from an older configuration, and died on start with `Profiles do not match: car\|-1705186244` against `car\|26199302` |
| `load` takes a native file lock — `gh.lock`, created *in* the graph directory — before it maps anything | a read-only graph directory is refused: `FileNotFoundException: /app/graph/gh.lock (Permission denied)`. "It only reads it" was wrong |
| A prepared graph needs no extract, and `RoutingConfig.fromEnv` did not know that | it checked for the `.osm.pbf` first, so a container carrying 14 MB of graph and no 41 MB extract fell back to straight lines |

**So the image takes one input — the extract — and prepares the graph with the server's own code.**
An option to supply a ready-made graph saves three seconds and buys the mismatch back; it was in the
first version and is not in this one.

Measured on the running container, three restarts: **healthy after 1 482 / 2 124 / 3 707 ms**, of
which the graph is **180 / 324 / 400 ms**. Against 3 168 ms to import, the prepared directory turns
the map from most of the start-up into a tenth of it.

**Consequence 1.6a — booblik being JVM-only costs nothing.** The brief already keeps the broker on
the server; driver coordinates go straight into the geo-index over WebSocket and never enter a topic.
The fact is worth recording because the opposite arrangement is the one people reach for.

**Consequence 1.6b — crash reporting from the browser is an HTTP call, not a client library.** The
brief has both wasm clients reporting to katcher. The client artifact has no browser target, and the
ingest endpoint is documented. See [D6](#d6-the-browser-clients-post-to-katchers-ingest-endpoint-directly).

**Consequence 1.6b1 — built and measured against a running katcher, 2026-09-02
([B-10](../backlog/B-10-crash-reports-from-the-browser.md)).**

| Fact | Where verified |
|---|---|
| The ingest is **public by construction**: `route("api") { reportRoute(…) }` sits outside the `authenticate(HEADER_USER_AUTH)` block the pages are inside. An application that has just crashed cannot be asked to sign in, and the `appKey` is what identifies it | `katcher/server/src/commonMain/.../ConfigureRouting.kt` |
| The ingest answers **202 Accepted**, not 200 — it queues the report. An unknown or revoked key is **401** before anything is queued | `katcher/core/src/commonMain/.../report/ReportRouting.kt`, and measured |
| End to end against `ghcr.io/youndie/katcher:0.6.2`: a report from shashki's own reporter appears as `IllegalStateException no MapSurface in composition`, tagged `production · 2026.09.02-b10`, with the release in katcher's own release filter | measured against the container |
| `ru.workinprogress.katcher:shared` — the module holding `CreateReportParams` — publishes jvm, four native desktop targets, three iOS ones and mingw. **No `wasmJs`**, so a browser cannot reach the type either | `katcher/shared/build.gradle.kts` |

The last row is the same shape as §1.6c1's finding about `shared-oidc`, and it has the same answer:
not a missing *variant* but a missing **target**, on a module that depends on nothing but
kotlinx-serialization. Until it grows one, shashki carries a copy of somebody else's wire type, which
this portfolio's own rule calls a future bug — `CrashReport` says so in its KDoc rather than pretending
the duplication is a design. **Filed upstream 2026-09-02**: youndie/katcher#32, which also notes the
public ingest and the 202 as documentation gaps rather than asks.

**Fixed the same day, and the copy turned out to be already wrong (B-33).** `shared` grew a
browser target and `:crash-client` now sends katcher's own `CreateReportParams`. Deleting the
transcription showed what a copy of somebody else's contract costs: shashki's `Breadcrumb` was
`(message, timestamp: Long, category)` and katcher's is
`(timestamp: LocalDateTime, type, message, data)` — a different shape, a different name and a
different encoding for the time. Nothing had sent a breadcrumb yet, so it never fired; any report
that carried one would have been rejected whole. **The type compiled, the tests passed, and it
was wrong** — which is the argument for sharing a wire type rather than transcribing it, made by
the case rather than by assertion.

The 202 is worth its own line because the obvious implementation is wrong: a reporter that accepted
any 2xx would count a proxy's 200, a redirect target or a captive portal as a delivered crash, and
would do it silently for exactly as long as nobody looked in katcher.

**Consequence 1.6c — the browser half of OIDC is shashki's code.** `oidc-auth-client` is jvm +
linuxX64. Authorization code with PKCE from a browser is a redirect, a verifier, an `S256` challenge
and a token exchange; the challenge needs SHA-256, which in the browser means WebCrypto and therefore
an asynchronous call. Small, but it is work the brief books as "shildik modules".

**Consequence 1.6c1 — built 2026-09-02, and three things about it were different from the
sentence above.**

| Fact | Where verified |
|---|---|
| shildik already has PKCE — `Pkce.matches(challenge, verifier)`, `S256` only, constant-time — but only the **verifying** half. There is no generator anywhere in it, published or not | `shildik/crypto/src/commonMain/.../Pkce.kt` |
| It verifies with `dev.whyoleg.cryptography`, which publishes `cryptography-core-wasm-js`, `cryptography-random-wasm-js` and `cryptography-provider-webcrypto-wasm-js` at 0.6.0 | `https://repo1.maven.org/maven2/dev/whyoleg/cryptography/` |
| shildik's `crypto` and `shared-oidc` modules are `jvm, macosArm64, linuxX64, linuxArm64`. `shared-oidc` — the module holding the `@Resource` endpoint types — depends only on `ktor-resources` and `kotlinx-serialization-json`, both of which publish `wasmJs` | `shildik/crypto/build.gradle.kts`, `shildik/shared-oidc/build.gradle.kts` |
| shildik's `authorize` serves shildik's **own** sign-in page; the choice between a magic link and Google is made there and returns through `callback/{method}`. The client names no method | `shildik/server/src/commonMain/.../oidc/OidcRoutes.kt`, `startAuthorization` |

What this changes. The half that is genuinely ours is smaller than "a browser OIDC client": there is
no `method` parameter, no branch for Google, and no hand-written WebCrypto — the same library
shildik verifies with does SHA-256 and secure random on `wasmJs`, so the asynchrony stays in the
signature (`suspend fun challenge`) and nowhere else. What is bigger is the reason: shildik is not
missing a browser *variant*, it is missing a browser **target**, on modules whose dependencies
already have one. `shared-oidc` in particular is two lines away from giving the browser the typed
addresses that the rest of this portfolio insists on — which is why shashki's client builds the
authorize URL by hand today, the one place in this repository where a path exists as a string. That
is worth proposing upstream rather than working around twice. **Filed 2026-09-02**:
youndie/shildik#20, which carries the module's own dependency list as the argument that the
target costs two lines. **Fixed the same day**: `shared-oidc-wasm-js` exists at 0.2.0.13, and
`SignInAttempt` builds its address from `OAuth2.Authorize` through `href` with the query assembled
by Ktor's `URLBuilder` — so neither the path nor the percent-encoding is this module's own any
more. The eight tests that were already there passed unchanged, including the one that pins the
whole URL character by character, which is what says the replacement was equivalent. `crypto` is deliberately not part of that ask — shildik has PKCE's
verifying half and a client needs the generating half, which is genuinely the client's.

**Consequence 1.6c2 — run against a shildik that was actually running, and the sentence above was
missing its other end (2026-09-02, [B-26](../backlog/B-26-sign-in-end-to-end.md)).**

Everything in §1.6c and §1.6c1 is about the **client** half. The criterion, though, said the client
should hold "a token the server accepts" — and shashki's server had no authentication at all. Nothing
in the research had noticed, because every note about shildik was written from the browser's side.

| Fact | Where verified |
|---|---|
| `oidc-auth-server` and `oidc-auth-core` publish at 0.2.0.13 and the whole surface is three names: `configureAuth(config, engine, validate: (AuthData) -> Boolean)`, `JWT_AUTH_OIDC`, `AuthData(roles, email, azp)` | the published modules; `server/src/main/.../Application.kt` |
| The validator fetches JWKS itself and does not need to be reachable to refuse a request that carries no token — the 401 happens before any network call | `ProtectedRidesTest`, whose unattended test points at `http://127.0.0.1:1` |
| A token this repository's `SignInAttempt` obtained through the full PKCE dance is accepted by this repository's server, and the same token with one character of its signature changed is refused | `ProtectedRidesTest`, against shildik 0.2.0.8 on the build box |
| Standing shildik up locally is 18081/19001 rather than 8080/9000: the shared build box already has projects on those ports, and a container that never starts presents as `Created` with an empty log | `docker/compose.yaml` |
| Creating a user through the admin API does not set a password; that is a separate `PUT /admin/tenants/{realm}/users/{id}/password` | `docker/bootstrap-shildik.sh` |

What this changes. The auth tier of every route is now a decision this repository has made rather
than one it inherited: the rider's mutating routes are behind `authenticate(JWT_AUTH_OIDC)`, and
`/api/routes`, `/api/quotes` and the promo screen are public on purpose. The switch is off when no
provider is configured — a demo has nobody to sign in against — which is why the refusal is tested
unattended and the acceptance carries a forged-signature control: a validator that accepted anything
would satisfy every other assertion.

What it did **not** change on the day was §1.6c's claim about WebCrypto: the whole flow ran on the
JVM against the JDK provider, and the browser build compiled without ever being executed. That was
the third item to end that way, so it became one of its own
([B-34](../backlog/B-34-a-browser-on-the-build-box.md)) — **and the obstacle turned out not to
exist.** Ubuntu 24.04 has no apt candidate for `chromium`, which is what had been written down; Chrome
for Testing is a plain zip that needs no root, and on this machine not one shared library was
missing. The eight PKCE tests now run in Chrome as well as on the JDK, so `S256` from WebCrypto is
the challenge shildik verifies rather than an assumption about it.

**The general lesson is the uncomfortable one.** "No browser on the build box" was measured once,
cited twice more, and was never re-tested — each citation reading as evidence because the previous
one had been written down. A constraint recorded in prose does not expire, and this one was wrong
for at least three items' worth of decisions.

**Consequence 1.6d1 — it was, and it found a defect on the first try (2026-09-02,
[B-14](../backlog/B-14-receipt-over-smtpkn-jvm.md)).**

The JVM target works: a receipt goes out through `SslEngineTlsProvider` over a real `STARTTLS`
handshake and arrives in Mailpit, with the certificate **verified** — the control points the same
code at a CA that signed nothing and it fails. That is Risk 4's question answered.

| Fact | Where verified |
|---|---|
| `SmtpSession.encrypted` is `private var encrypted = false` and is **assigned nowhere in the module**, so `isEncrypted` is permanently `false` — on every platform, not only the JVM | `smtp-client/src/commonMain/.../SmtpSession.kt:105`, grep over the module |
| `authenticate()` refuses to run when `!isEncrypted`, so **`AUTH` after a successful `STARTTLS` always throws** unless the caller passes `allowOverPlaintext = true` — a flag whose name asserts the opposite of the truth | same file, line 407 |
| The library's own README shows exactly that sequence — `startTls(...)` then `authenticate(...)` — as its usage example | `kmp-smtp-client/README.md` |
| Its own tests do not catch it because they pass `allowOverPlaintext = true` throughout, with a comment saying that is better than "pretending the scripted transport" is encrypted | `smtp-client/src/commonTest/.../SmtpAuthTest.kt:332` |

The last row is the mechanism: the flag is only wrong when a *real* provider has upgraded a *real*
connection, and no test does both. Deciding to be honest about a scripted transport is what hid a
defect in the unscripted one — which is worth recording as a shape, not only as a bug.

**Nothing is worked around in shashki.** Mailpit needs no credentials, so the receipt path does not
call `authenticate` and is not blocked; a real relay would be, on the first attempt. The check that
`SmtpReceiptSender` would naturally make — "am I encrypted?" — cannot be made, so it is written as
the test's negative control instead, which demonstrates more anyway. **Filed upstream 2026-09-02**:
youndie/smtpkn#4, with the suggested one-line fix and the reason the suite misses it. **Fixed the
same day** — `encrypted = true` after the upgrade — so `SmtpReceiptSender` now makes the check it
always wanted, and the run against Mailpit passes with it.

**Consequence 1.6d — shashki would be smtpkn's first JVM consumer of consequence.** The library says
plainly that the JVM target is unclaimed. That is a feature of this project, not a defect — a
reference service is exactly what turns "compiles and runs in CI" into "claimed" — but it has to be
gated by a test rather than assumed. See [Risk 4](#risk-4-smtpkns-jvm-target-is-unclaimed).

### 1.7 The kit's composition rules are half contract and half renderer invariant

Read at source in the kit's section 08 ("composition rules for the screens the server sends as a
tree"), not through the handoff. Six rules: one accent surface per screen, with the second falling
back to chrome; tiles never reflow (four columns, gap 12, sizes 1/2/4, unknown sizes dropped rather
than guessed); figures only at 32 and 54, nothing else in a card above 19; a row leads with a route
stack, one 20 dp glyph, or nothing, never a photo and never both; the pivot holds the top level and
the server may reorder its items but not nest them; an empty list renders one server-supplied string
as a headline in the disabled brush, with no action.

**Consequence 1.7a — three of the six belong in the renderer, not in the protocol.** "The first
accent surface wins", "an unknown tile size is dropped" and "a primary figure goes to 54" are all
statements about what the client does when the server sends something the rule does not allow. A
protocol can describe the allowed shape; only the renderer can decide what happens to the
disallowed one, and kompot's own posture — degrade rather than crash — is the same posture. Encoding
them as server-side validation would put the guarantee on the wrong side of the wire, where a second
implementation of the server silently drops it.

**Consequence 1.7b — the tile rule restates §1.1c's open question.** "Four columns, gap 12, page
margin 12" is the same 4/3 as everything else in the kit. The renderer takes its grid from
`ShashkiMetrics`, so it inherits whatever B-15 answers and needs no separate decision.

**Consequence 1.7c — built 2026-09-02 ([B-17](../backlog/B-17-kompot-renderer-invariants.md)), and
three things came out of it.**

The three rules are renderers now: `TripRowRenderer`, `EarningsTileRenderer`, `FareBreakdownRenderer`
in `:shared-ui`, registered by kompot's KSP processor into `generatedShashkiUiRenderers` and
`generatedShashkiUiSerializersModule`. Each has a golden fed the payload that breaks its rule — two
rows both asking for the accent, a tile at `size = 3`, a card with a second figure in it — and each
image is the degraded form. A fixture sent a *legal* tree would have photographed the rule working
and proved nothing.

| Fact | Where verified |
|---|---|
| `@Serializable` **without the serialization plugin compiles**. The annotation resolves through a transitive dependency, the class builds, the generated registry builds, and the first decode throws `Serializer for class 'TripRow' is not found` | `:shared-ui` had no `kotlinSerialization` plugin; adding it fixed three failing tests |
| kompot's `KompotDegradationSink` has three kinds — unknown component, unrenderable component, unknown action — and **none for a property outside its allowed set** | `kompot-client/.../Degradation.kt` |
| The registry is a KSP side effect: a component that lost its annotation would still compile, still render locally, and be an `UnknownComponent` on the wire | the control test decodes the same payload without the module and gets exactly that |

The first row is the one worth keeping. It is a build-configuration mistake with no compile-time
symptom at all, in a module that already had six plugins and looked complete — the failure surfaced
only because something decoded, and nothing in this repository had decoded before.

The second is a gap rather than a defect, and it has consequences for the rule it touches: a dropped
tile is invisible. kompot's own file argues that "a hole is reported by nobody" and builds a sink for
exactly that, but its vocabulary covers types and actions, not values. Reporting a bad `size` through
`UNRENDERABLE_COMPONENT` would be a lie — the component is renderable, its size is not — so the drop
stays silent and `EarningsTileRenderer` says so where it happens.

**Consequence 1.7d — building the shell found two questions the server could not answer
(2026-09-02, [B-28](../backlog/B-28-the-client-application-shell.md)).**

Every screen in `:shared-ui` was built from values and photographed that way, and both were correct.
What neither could show is that the values have nowhere to come from:

| The screen asks | The server had | What it needed |
|---|---|---|
| R4 draws three prices, one per class, before anything is ordered | `POST /api/rides`, which *creates* a ride, and `POST /api/routes`, which prices nothing | `POST /api/quotes` — one road estimate, three prices |
| The trip screen draws the car moving | `RideView.driverId` and nothing about where that driver is | `GET /api/rides/{id}/driver`, and a `whereIs` on the index to answer it |

Neither is a large piece of work — forty lines each — and both are the kind of gap a screenshot
suite cannot have: a golden is a picture of a screen fed values, so a screen whose values do not
exist photographs exactly as well as one whose values do. **That is the argument for the shell being
an item rather than a formality**: the first thing it did was find two endpoints nobody had missed.

The pricing one is the more instructive. The alternative to adding it was the client holding a copy
of the fare formula to fill in three tiles — and a copy of a rule is a rule that drifts, so the rider
would have been shown one number and charged another the first time a coefficient moved.

**One thing the server still cannot answer, and the screen says so rather than inventing it.**
`RideView` carries a `driverId` and nothing about the person: no name, no car, no plate, no rating.
The trip screen draws those four as em dashes, because the registration is the field a rider checks a
real car against and fiction there is worse than a blank.

**Consequence 1.7f — one of the two dashes had an answer already in the building
(2026-09-02, [B-31](../backlog/B-31-the-wait-for-a-car.md)).** The class tile reads `4 min · Kia Rio`
and B-28 drew both halves as a dash. The wait was not a missing capability: the geo-index answers
"who is near, of this class" (B-20) and the router answers "how long from here to there" (B-23), and
nothing had applied the second to the first. It is now a field of the same `POST /api/quotes` answer,
because a screen that asked twice could show a price from one moment and a wait from another.

| Decision | Why |
|---|---|
| the nearest candidate by straight line, then routed — not the fastest by road | routing every candidate is one graph search per online driver per class, for a number shown before anybody has ordered; and the index's ordering is the one the offer cascade uses, so the rider is told about the driver they would actually be offered |
| `null` rather than a number when there is no candidate, or no road to one | the wait is the most-looked-at figure on the screen; a constant there is a decoration, and the kit's tile already has an unavailable state |
| unavailable means no car, not no price | pricing is arithmetic and answers for every class — what a rider cannot do is order a class nobody is driving |

**The test discriminates rather than merely ordering.** The fixture graph is an L, so a car at the
north end is 3 772 m away by road and 2 710 m by hypotenuse: a wait taken from the index's own
straight-line metres gives a ratio of 3.5 between the two classes and a routed wait gives 4.9. The
assertion sits between them, and the mutation confirms it — replacing the route with the index's
distance produces exactly 3.52 and fails.

The car stays a dash. `RideView` carries a `driverId` and nothing about the vehicle, and the
registration is the field a rider checks a real car against.

**Consequence 1.7e — the second bundle found a defect the first could not have
(2026-09-02, [B-29](../backlog/B-29-the-driver-bundle.md)).**

`DriverAnswerStep` refuses an answer from a driver who is not the one currently being offered: it
resuspends, keeps waiting for the right driver, and is completely correct. It is also completely
silent — the route then answered `200 OK` with the ride unchanged, so a driver whose tab had been
asleep for twenty seconds would have been shown a trip that belonged to somebody else.

Nothing on the server was wrong, which is why nothing on the server had found it. `RideRoutesTest`
covered accept and decline by the driver who *was* asked; there was no client to make the other call
until there was one, and no screen to be wrong. The fix is `OfferGoneException` → 409, checked by
comparing the answer to the ride that came back rather than trusting that nothing threw; the test
was verified by removing the guard, which turns the 409 back into the `200 OK` described above.

The same item found the second half of the same shape on the client: **the countdown had no clock it
could trust.** `OfferView` carried only `expiresAtEpochMs`, so a browser had to subtract its own wall
clock from it — and a laptop an hour out draws fifteen seconds that never start. It now carries
`nowEpochMs` beside it, and the client counts a duration it was handed. The deadline is still the
server's where it matters: reaching zero drops the card, and whether an answer was in time is settled
by the saga.

### 1.8 A tile renderer of our own: what it would actually have to implement

Added after the first pass, when the brief's "own render on Compose Canvas — an optional v2 demo, not
a blocker" was promoted to a candidate route for §1.3. Research §1.3's problem is that the map is the
one part of this product that cannot be drawn in Compose; a renderer that draws it in Compose does
not have that problem on any target. What follows is what it costs, measured rather than guessed.

#### The style documents are the specification, and they are small

Both style files were parsed rather than read. Per file: **13 layers** — 1 background, 4 fill, 6 line,
2 symbol — over two source types, `vector` (pmtiles) and `geojson`.

| Surface | What the two styles actually use |
|---|---|
| paint properties | `background-color`, `fill-color`, `line-color`, `line-width`, `line-dasharray`, `text-color`, `text-halo-width` — seven |
| layout properties | `line-cap`, `line-join`, `symbol-placement`, `text-field`, `text-font`, `text-size`, `text-letter-spacing` — seven |
| layer keys beyond the basics | `filter`, `minzoom`. Nothing else |
| expression and filter operators | `==`, `in`, `get`, `coalesce`, `downcase`, `interpolate` with `exponential`, `zoom` — seven |
| sprites, icons, halos | **none.** The styles' own metadata says POI icons are off at every zoom, and `text-halo-width` is 0 everywhere |

Verified by parsing `shashki-map-dark.json` and `shashki-map-light.json`. That is a specification a
person can hold in their head, and it is small because the designer made it small on purpose — the
metadata line "nothing in the basemap is saturated — the accent has no competition" is the same
decision seen from the design side.

#### The toolchain reaches far enough, and the hardest part was checked first

The part that sinks a hand-written renderer is text along a curved street, because Compose has no
API for it. It is reachable, and on the target that matters:

| Fact | Where verified |
|---|---|
| `org.jetbrains.skia.PathMeasure.getRSXform(distance)` returns the per-glyph rotate-scale-translate for a point along a path | `skiko-awt-0.150.1.jar`, `org.jetbrains.skia.PathMeasure` |
| `TextBlob.Companion.makeFromRSXform(glyphs, xforms, font)` builds a blob from those, and `Canvas.drawTextBlob` draws it | same jar, `org.jetbrains.skia.TextBlob$Companion`, `org.jetbrains.skia.Canvas` |
| `Font.getStringGlyphs`, `Font.getWidths`, `Font.getXPositions` supply the glyph run and its advances | same jar, `org.jetbrains.skia.Font` |
| **All five exist in the Kotlin/Wasm build of skiko**, not only the JVM one | `skiko-wasm-js-0.150.1.klib` — `getRSXform`, `makeFromRSXform`, `PathMeasure`, `getStringGlyphs`, `drawTextBlob` all present |
| The bridge from Compose is `Canvas.skiaCanvas` (and `Paint.skiaPaint`). `nativeCanvas` / `NativeCanvas` were deprecated in 1.11 and their deprecation level was raised to **ERROR** in 1.12.0 | Compose Multiplatform 1.11.0 and 1.12.0 release notes |
| `kotlinx-serialization-protobuf` 1.11.0 publishes a Wasm target, so MVT decoding needs no new dependency shape | `https://klibs.io/package/org.jetbrains.kotlinx/kotlinx-serialization-protobuf` |
| Compose 1.12.0 loads Noto automatically for unresolved symbols **on web** | Compose Multiplatform 1.12.0 release notes, "Web" |

**Consequence 1.8e — decoding a tile found a defect in the styles, which is the kind of thing a
prototype is for.** Building route 4's decoder (B-01) meant reading a real tile out of
`city.pmtiles`, and the smallest tile carrying a named road turned out to carry the A2 — which in
OpenMapTiles has a `ref` and no `name`. The styles label through
`["coalesce", ["get", "name:latin"], ["get", "name"]]`, so they draw nothing on it. Measured over all
810 tiles: **654 of 11 437 named roads are `ref`-only** — including the road from the city to the
airport, which is the one road this product's flagship journey follows.
[B-24](../backlog/B-24-motorways-carry-ref-not-name.md) is the one-token fix. Neither reading the
style nor reading the schema would have produced this; decoding the data did.

**Corrected 2026-09-02, when B-24 re-measured with a script instead of an impression.** This used to
say the 654 were "every one a motorway". The count was right and the characterisation was not:
194 are `motorway`, and the rest are **secondary 195, tertiary 152, primary 107, path 4, minor 1,
primary_construction 1**. The wrong half came from generalising the one road the prototype happened
to draw. It matters because it changes what the defect is: not "the motorway shields are missing", a
thing a map might reasonably leave out, but *every* numbered road in the city going unlabelled — a
third of them ordinary secondary and tertiary streets that a rider would expect to read.

The re-measurement is repeatable rather than recounted: `map/label_coverage.py` reads the archive and
takes the keys out of the style document's own `text-field`, so it measures the map rather than the
author's memory of it. Run before the change it reproduces 654 exactly, which is what makes the 0
afterwards worth anything. Every one of the 654 carries `ref`, so the third branch closes all of
them: **0 of 11 437**, in both documents.

**And the same measurement found the mistake pointing the other way.** The styles say
`["downcase", …]`; route 4's renderer did not, so every label in `map_canvas_tile_dark` was drawn in
the source's own case — "A2" and "Voglje" where the design says "a2" and "voglje". A renderer that
draws a different string from the one the style specifies makes a golden that certifies the wrong
picture, which is worse than no golden. `labelText()` lower-cases now and the three map goldens were
re-recorded.

**Correction, found while building it (B-01, 2026-09-02): the hardest piece has a second solution,
and it is the better one here.** Everything above about skia is true — `getRSXform`,
`makeFromRSXform` and `drawTextBlob` are all in the Kotlin/Wasm build and would place glyphs on a
curve. What the table did not ask is *which font they would place*. They need an
`org.jetbrains.skia.Typeface`, and this product's faces are bundled through Compose by kvadrant, so
the skia route would have to find them again by some other path — and a label drawn in whatever the
host offers is a golden that records the machine, which is exactly what §1.2 spent B-02 ruling out.

Compose has its own `PathMeasure` with `getPosition` and `getTangent`, and its own `drawText`. One
glyph at a time, positioned and rotated, draws in the theme's own typography: the label is in
Selawik, the golden is portable, and nothing reaches around the framework. It costs kerning between
glyphs, which is invisible at label sizes and is the trade every renderer that curves text makes.
`map_canvas_tile_dark` is the A2 with its number along it, recorded on the mac and verified on Linux.

The skia route stays true and stays the answer for anything Compose's text stack cannot express.
This was not that, and the table above did not know it because it asked whether the mechanism existed
rather than what it would need.

**Consequence 1.8a — this route deletes four open items rather than adding to them.** Glyph PBFs stop
existing as a problem: a Compose renderer draws labels with the fonts kvadrant already bundles, so
[B-06](../backlog/B-06-city-extract-and-tiles.md)'s PBF generation and the styles' interim
`["Noto Sans Regular"]` fallback both go away. (B-06 has since generated them anyway, 1.2 MB across
two stacks — on route 4 that is 1.2 MB nobody serves, not work that has to be undone.) The
compositing problem goes away, because there is no
second canvas. The pmtiles protocol-handler question (Risk 5) goes away, because we read the archive
ourselves over ranged HTTP. And the map becomes **screenshot-testable**: a MapLibre map is a GL
surface or a DOM element and can never appear in a viddik golden, while a Compose-drawn map is a
golden like any other — which matters more here than usual, because the viddik component browser *is*
the design acceptance for this project.

**Consequence 1.8b — the cost is not the drawing, it is the tiling.** Fills, strokes, joins, caps and
dash arrays are `DrawScope` calls. What has to be built underneath is the part with no shortcut:
pmtiles directory traversal and ranged reads, MVT geometry decoding including the zig-zag command
encoding, tile-to-screen transforms in Web Mercator, per-zoom tile selection with a cache, clipping at
tile boundaries so a road does not end at a seam, and label placement — collision, de-duplication of
the same street name across adjacent tiles, and the curved baseline above. Pan and pinch are Compose
gestures and are the easy end.

**Consequence 1.8b1 — that list was built, and it cost less than it reads (2026-09-02,
[B-30](../backlog/B-30-tiles-over-the-wire.md)).** Every item above is now in `:shared-ui`, and the
two surprises were not on it.

| What §1.8b named | Where it is | What it actually cost |
|---|---|---|
| pmtiles directory traversal and ranged reads | `tiles/Pmtiles.kt`, `tiles/HttpRangeReader.kt` | ~200 lines; the header is five offset pairs and the directory is four runs of varints |
| MVT geometry decoding | `tiles/Mvt.kt` — done in B-01 | — |
| tile-to-screen transforms in Web Mercator | `MapViewport` | ~60 lines, and the arithmetic was already in `TileProjection` |
| per-zoom tile selection with a cache | `MapViewport.tiles`, `PmtilesTileSource` | the cache is a `mutableStateMapOf` and a miss counter |
| clipping at tile boundaries | **not needed** | see below |
| label collision and de-duplication | `TileRenderer.drawStreetLabels` | ~20 lines, greedy, longest road first |

**The clipping turned out to be the wrong instruction.** MVT geometry runs *past* the tile's own
edge by a buffer, precisely so a neighbour can draw the same road and the two meet; clipping at the
boundary is what makes a road end at a seam, not what prevents it. What the seam actually needs is
**ordering across tiles rather than within them** — every tile's areas, then every tile's roads — or
one tile's water covers its neighbour's street network in the overlap. That is a two-line change and
an unobvious one, and it is why `drawTile` became `drawTileAreas` and `drawTileRoads`.

**And gzip was not on the list at all.** The archive is gzipped inside and out — the directory and
every tile — and there is nowhere to borrow an inflater from on the target that matters: okio's
wasmJs klib carries none (checked in the artefact, not assumed), and the browser's
`DecompressionStream` would make every read asynchronous and leave the desktop target, the one that
can be photographed, on a different implementation from the one that ships. So `tiles/Inflate.kt` is
DEFLATE and the gzip wrapper in common code, checking the trailer's CRC-32 — because a hand-written
inflater that is subtly wrong does not throw, it produces plausible bytes, and a tile made of
plausible bytes draws a city that is not there.

Two things measured rather than asserted:

- **The reader agrees with a tool chain that is not ours.** `ljubljana-14-8850-5815.mvt` was
  extracted for B-01 by `pmtiles`; the archive fixture is a cut of `city.pmtiles` with nothing
  re-encoded. The bytes this reader hands back for that tile are that file, 4 068 of them, identical
  — one assertion covering the header parse, the directory walk, the Hilbert id and the inflater.
- **The read count is held to.** Opening is two requests, each tile is one, a miss is none, and a
  second look at the same viewport is none. Against the real archive on bochka: 810 tiles, two
  requests to know it, four to draw a corner.

**And the goldens are still portable.** The whole new pipeline — inflate, decode, project, place
labels — is in the path that produces `map_canvas_tiles_at_a_seam`, recorded on macOS and verified
unchanged on Linux by the same `check`. B-02's claim now covers arithmetic as well as text.

**Consequence 1.8c — WorldWind Kotlin is route 3 with a Kotlin API, not a fourth option.**
`earth.worldwind:worldwind` (Apache-2.0, active — last push 2026-08-25) is the one Kotlin
Multiplatform library found that publishes a wasm target and carries an MVT layer, and it has a
`worldwind-compose` module with source sets for `androidMain`, `iosMain`, `jvmMain`, `jsMain` and
`wasmJsMain`. How it reaches a browser was read out of that module rather than inferred, and its own
KDoc states the constraint plainly:

> Skia-backed Compose/Web renders the whole UI into a single `<canvas>` and — unlike the DOM-based
> Compose HTML used on the `js` target — it cannot embed WorldWind's own WebGL `<canvas>` inside that
> surface. So this binding takes the only workable route on wasmJs: it creates a full-window WebGL
> `<canvas>` in the DOM *behind* the (transparent) Compose surface and lets the Compose UI overlay it.

| Target | How the map gets on screen | Where verified |
|---|---|---|
| `wasmJs` | its own `<canvas id="worldwind-canvas">`, `position: fixed`, `z-index: 0`, inserted as the **first child of `<body>`** — behind Compose's transparent canvas | `worldwind-compose/src/wasmJsMain/kotlin/earth/worldwind/compose/WorldWindow.wasmJs.kt` |
| `js` | **Compose HTML** — `org.jetbrains.compose.web.dom.Canvas`, and the overload takes an `AttrsScope` instead of a `Modifier` "because Compose HTML's element-attribute model is incompatible with Compose UI's `Modifier`" | `.../jsMain/.../WorldWindow.js.kt` |
| `jvm` | a JOGL `GLCanvas` inside a `SwingPanel`, because "Skia and JOGL share no GL context" | `.../jvmMain/.../WorldWindow.jvm.kt` |

Three things follow, and each is a fact from the source rather than a worry:

1. **The map cannot be a sized element in a layout.** The `wasmJs` binding's own KDoc: "The
   [modifier] is accepted for API symmetry with the other targets; the globe canvas itself is always
   full-window." The kit's phone screens are close enough to full-bleed to live with that; a map
   inside a card is not available.
2. **Pointer events are all-or-nothing, and the library says so without saying it.** The sample page
   ships `body > canvas { pointer-events: none }` with `#worldwind-canvas { pointer-events: auto }`,
   and the KDoc adds that "apps that overlay interactive Compose UI on the globe must instead
   re-enable pointer-events on their own controls". Compose UI controls are not DOM elements — there
   is one canvas — so there is nothing to re-enable them on. Either Compose receives every gesture
   and the map never pans, or the map receives them and no button on the screen works. Making both
   work means toggling `pointer-events` on the Compose canvas from hit-test knowledge pushed out to
   JavaScript. shashki needs both on one screen: the rider drags a pickup pin on the map and taps a
   class tile below it.
3. **On no target does the map end up inside Compose's own canvas,** so it can never appear in a
   viddik golden. That is true of `jvm` too, where a heavyweight `SwingPanel` is the mechanism.

So this library does not remove §1.3's compositing problem; its source documents the problem as
unsolvable on wasmJs and ships the workaround. It belongs in D1 as a better-packaged route 3, not as
an escape from the choice.

**Consequence 1.8d — but its MVT package is the size estimate route 4 was missing.**
`worldwind/src/commonMain/kotlin/earth/worldwind/layer/mvt/` is **30 files**, and the list reads as
route 4's tiling half itemised: `ProtobufReader`, `MvtDecoder`, `MvtGeometry`, `MvtFilter`,
`MvtExpression` + `MvtExpressionParser`, `MvtZoomInterp`, `MvtTile` + `MvtTileSource`,
`MvtBatchedLineTile` + `MvtBatchedPolygonTile`, `MvtLabelCollider`, `MvtLabelGroup`,
`MvtCurvedTextPlacer`, `MvtCurvedLineLabel`, `CurvedGlyphScale`, `MvtSchemaDetector`,
`MvtSpriteAtlas`, `MvtMapboxStyleLoader`. Under Apache-2.0 that is a corpus to read, and in places
to borrow with attribution, rather than a wall to reinvent — for the **decode, filter, expression and
label-placement** half. The **drawing** half does not transfer: it renders through WorldWind's GL
pipeline, and route 4's premise is that it renders through Compose.

Two smaller findings, recorded because they cost a run to rediscover:

* **No pmtiles.** `UrlTemplateMvtTileSource` is `{z}/{x}/{y}.pbf` over HTTP. A pmtiles archive needs
  a new `MvtTileSource`, wherever the code ends up living.
* **Run, and the answer is eleven of thirteen** (B-01, 2026-09-02, `earth.worldwind:worldwind:2.0.10`
  from Maven Central — whose published variants include `wasmJs`, confirming §1.8c's target claim
  from the artefact rather than from the README). Fed the whole document the loader throws; fed one
  layer at a time, **every `fill` and `line` layer loads and both `symbol` layers fail.** So the
  `interpolate` worry was unfounded — the road widths and the rail's `line-dasharray` go through, as
  the body rather than the header predicted — and the single thing it cannot read is `text-field`,
  which the styles write as `["downcase", ["coalesce", ["get", "name:latin"], ["get", "name"]]]`
  where the loader takes a plain `"{name}"`.

  Two consequences. The basemap is not the obstacle it looked like: a one-property change to the
  styles, or a patch to that one function, gets all thirteen. And **the KDoc oversells its
  diagnostics as well as its coverage** — it promises an `MvtStyleParseException` "pointing at the
  offending node" and delivers a bare `IllegalArgumentException: … JsonArray … is not a
  JsonPrimitive`, which names a type and no layer, no property and no file. That is worth more than
  the parsing result: a library that reports a configuration error without naming where it is costs
  an hour every time somebody edits a style.

**Open: where such an engine would live.** Every other self-hosted piece of this stack is its own
library rather than a folder in the product that needed it first. A tile renderer is the same shape
of thing. That is a question about the portfolio and not about shashki, and it does not have to be
answered before the spike — but it should be answered before the second screen depends on it.

---

## 2. Decisions

### D1. The browser is a decision with a date, not a precondition

Brief: step one of the plan is a spike, "MapLibre Compose in wasm — map, markers, route", to retire
the main risk; both clients are Compose Multiplatform → wasm.

Decision: the spike stays first and its question changes. It no longer asks whether the wrapper works
in wasm — §1.3 answers that — but which of four routes to the browser this project takes, and it
produces a measured comparison and a written choice rather than a green tick.

The routes, with what each costs:

1. **Desktop first (JVM), browser second.** Everything the product needs is published today:
   `maplibre-compose` has a `jvm` variant, kvadrant-ui has `jvm("desktop")`, kompot-client has
   `jvm("desktop")`, and viddik's capture engine is JVM-only anyway, so the goldens and the component
   browser run against the same target the app runs on. The price is that the demo is not a URL.
2. **Wasm on an unreleased Compose.** Take PR #1081's branch and the pinned
   `1.12.10-alpha01+dev4710`. The price is that kvadrant-ui is published against 1.12.0 and viddik
   binds to a Compose line at runtime rather than at compile time — a mismatch shows up as
   `NoSuchMethodError` on the first frame (§1.2) — so the whole client stack rides on a dev build
   until Compose ships the merge commit named in that PR.
3. **Wasm with the map outside Compose.** Run maplibre-gl-js in its own DOM element and put the
   Compose canvas over it. The price is paid on every screen the kit draws: Compose for Web renders
   into one canvas, so the map is visible only through a hole punched in it, and the kit's markers,
   route stack and panels sit over the map by design.
3b. **Route 3, packaged: WorldWind Kotlin.** `earth.worldwind:worldwind` has a Compose module with a
   `wasmJs` target. §1.8c read it: it inserts its own full-window WebGL canvas behind Compose's
   transparent one, the map cannot be a sized element, and pointer events are all-or-nothing on one
   canvas. It is route 3 with a Kotlin API rather than a way past route 3.
4. **Draw the map ourselves, in Compose, from the tiles.** The brief listed this as an optional v2
   demo; it is a candidate route. §1.8 measured what it costs: the two style documents use 13 layers,
   seven paint properties, seven layout properties and seven expression operators between them, with
   no sprites, no icons and no halos, and the hardest missing piece — a label following a curved
   street — is reachable through `PathMeasure.getRSXform` and `TextBlob.makeFromRSXform`, both
   present in the **Kotlin/Wasm** build of skiko. It is the only route with no target problem on any
   platform, and the only one whose map appears in a viddik golden.

**What the spike needs from the city is smaller than B-06.** The prototypes need *a* `city.pmtiles`
with real roads, not the full extract with a GraphHopper import behind it — and Protomaps serves a
bounding-box extract of Ljubljana as a single pmtiles in about a minute. So B-01 no longer waits on
B-06 (amended 2026-09-01): the spike takes the bbox extract, B-06 produces the archive the demo ships
and the graph the router imports, and the two are reconciled when B-06's numbers come in.

Why not choose now: routes 1 and 2 differ by a Compose release whose date nobody here controls;
route 3's cost is a layout question that a prototype answers and an argument does not; and route 4
trades a dependency risk for a quantity of work, which is a trade only a prototype prices. What is
decided now is that **the map lives behind one interface with a platform implementation per target**,
so the choice is a module and not a rewrite, and that no other work waits on it — §1.4, §1.5 and §1.6
are all server-side or target-independent.

**Route 4 is not judged on the same axis as the others**, and that is why it is worth prototyping
even though it is the most work. Routes 1–3 buy a map and leave four items open — glyph PBFs, the
compositing hole, the pmtiles protocol handler, and a map that can never be in a golden. Route 4
closes all four and opens one: how much of a tile pipeline we are willing to own (§1.8b). Given that
this stack already owns its broker, its object store, its identity provider, its log store and its
crash reporter, that question has a precedent — but a precedent is not a measurement.

**Decided, 2026-09-02: route 4. The map is drawn in Compose, from the tiles, by this project.**

The measurement that decided it is a compilation and a pair of images, in that order.

**The compilation.** The brief specifies both clients as Kotlin/Wasm. `:shared-ui` and `:protocol`
now declare `wasmJs { browser() }`, and the whole map package — the protobuf reader, the MVT decoder,
`TileRenderer`, `drawTextOnPath`, `CanvasMapSurface` — compiles for it, together with
`RiderClassPicker`, the screen that consumes it. Nothing was added to make that happen and nothing
was moved to a platform source set: route 4 is Compose and arithmetic, so it goes where Compose goes.
This is checked rather than stated — `check` depends on `compileKotlinWasmJs`, and the control is
that one `java.io.File` in `Mvt.kt` fails the build with `Unresolved reference 'java'`. The day this
route reaches for something only a JVM has, the build says so and this decision reopens.

None of the other three has that property, and each fails it differently:

| Route | What it would take to put a map in a Kotlin/Wasm bundle |
|---|---|
| 1 — desktop first | Not a bundle at all. The demo becomes a binary rather than a URL, which is the thing the brief asked for by name |
| 2 — wait for the wrapper | There is no artefact to wait *with*: no `maplibre-compose-wasm-js` at any version in either namespace (§1.3a0), and the maintainer's own note is that the JS externals do not trivially port |
| 3 — Kotlin/JS instead | A `js` target in `kvadrant-core` and in `kompot-client`, then re-verifying both. The map itself would behave — §1.3a1 corrected that — but the price is paid in two libraries this project does not own |
| 3b — WorldWind on wasmJs | Compiles, and is the only other thing that does. Its canvas is full-window and behind Compose by its own KDoc, so it cannot take the 360 dp the kit gives the map |

**The images.** `screens_rider_class_picker` and `map_rider_class_picker_on_canvas` are the same
screen with the same panel, differing only in what is in the map's slot. The kit gives the map 360 of
844 dp with the class panel below it, so on this screen the map is a *sized element* — and route 4's
is a composable like any other, inside the modifier's bounds, in a golden that verifies unchanged on
Linux. Routes 1 and 3b cannot produce that image: one renders through the host's GPU backend
(§1.3a2), the other outside Compose's surface entirely.

**What route 4 costs, stated plainly, because it is the expensive one.** Everything a map library
would have given us is now a line item: gestures and camera, tile fetch and cache, the style
interpreter (§1.8 priced it at 13 layers, seven paint and seven layout properties, seven operators,
no sprites and no icons), label collision, and every zoom level beyond the one the prototype draws.
The prototype transcribes the styles' filters and widths rather than reading the documents, and that
gap is real work, not a detail. Against it: §1.8a's four items close — glyph PBFs, the compositing
hole, the pmtiles protocol handler, and a map that can never appear in a golden — and owning the
decode has already paid once, in [B-24](../backlog/B-24-motorways-carry-ref-not-name.md), a defect in
the styles that only reading the data could find.

**What is deliberately not decided.** Route 1 stays useful as a fidelity reference — when our tile
looks wrong, the way to find out is to render the same tile with the real library on desktop — and
that costs nothing, because it never enters the shipped client. And route 4's renderer is a candidate
to leave this repository later, on the same argument as every other library in this portfolio
(§1.9); it stays here until a second product wants it.

### D2. kvadrant-ui is the base and it is pulled towards the kit

Brief / handoff: section 03 of the kit is inherited, so the work is "to check and override nothing;
a divergence is a bug in the kit or in kvadrant, escalate rather than hard-code".

First reading of the research: since the library's divergent numbers are documented as deliberate,
escalation is closed, so shashki forks the foundation and ships its own.

**Decision, taken by the owner of both repositories: escalation is open.** kvadrant-ui is the base,
and it is pulled towards the kit rather than worked around. What that means concretely is settled by
§1.1f, which asked what can be expressed against the published library at all, and got two different
answers:

- **the type ramp and the spacing are already expressible.** `KvadrantTypography` and
  `KvadrantMetrics` are both `data class`es with public constructors, and `KvadrantTheme` takes
  both. `ShashkiTypography` and `ShashkiMetrics` are therefore values supplied to the library, not a
  fork of it — the same relationship the library already has with `accent`. They live in shashki
  because they are the kit's numbers, and the kit belongs to this product;
- **the ink and the app bar are not expressible at all,** and that is a gap in the library rather
  than a disagreement about a number. Both are closed upstream — [D3](#d3-kvadrant-ui-grows-the-two-hooks-the-kit-needs).

Why not move kvadrant-ui's **defaults** to the kit's values, which is the other way to read "pull it
towards the kit": every metric in that library carries the Metro pixel count it was converted from,
and `margin = 9.dp` is documented as `PhoneMargin` 12 px × 0.75. Changing the default falsifies a
claim the library makes about a verified source, and it would do it for every consumer in order to
suit one. Growing the library's *vocabulary* costs nothing and takes nothing away; changing its
*answers* takes away the reason to trust the rest of them. If that is wanted anyway it is a separate
decision with its own reasoning, not a consequence of this one.

`ShashkiMetrics` starts as `KvadrantMetrics(margin = 12.dp, tileGap = 12.dp, …)` with `scale` left at
1f, so the type ramp is not rescaled behind our back. Whether that constant is 12 or 9 is Open
question 1's fourth part; the module ships either way and only the number waits.

### D3. kvadrant-ui grows the two hooks the kit needs

**Done — kvadrant-ui B-48 and B-49, merged.** Both additive, both keeping the stock behaviour as the
default, and neither asking the library to stop being what it is. What follows is the argument as it
was made; the shapes it asked for are the shapes that landed.

**Published as 0.2.0 on 2026-09-01, later the same day** — on Reposilite `/snapshots`, where this
library's releases actually live (`/releases` is a 404 for 0.2.0 as it was for 0.1.0), and without a
`v0.2.0` tag as of that evening. shashki pins it; B-03 and B-22 closed on it. The paragraph below is
kept as the record of the gap between a merge and an artefact, because the gap recurs.

**Merged is not published, and the difference was the only thing holding B-03 open.** Checked on
2026-09-01: Reposilite holds `kvadrant-core 0.1.0` from before the merge, `/releases` is empty, the
only tag is `v0.1.0`, and the CHANGELOG files both changes under `Unreleased`. The blocker is a
publication the owner of both repositories controls, so it is an item rather than a wait —
[B-22](../backlog/B-22-publish-kvadrant-ui-with-the-hooks.md): two breaking changes on `data class`es
with ABI validation are a minor bump by the library's own rules, and 0.2.0 is what B-03 and B-04 pin.

**`onAccent` becomes overridable.** Today it is `val onAccent: Color get() = contrastOn(accent)` — a
computed property, so no consumer can supply a different answer. It becomes a constructor parameter
of `KvadrantColors` defaulting to `contrastOn(accent)`. Nothing changes for anyone who does not pass
it; shashki passes black, which is what the kit specifies and what §1.1a showed the computed value
contradicts at about 2.2:1 on Amber. The library's own trade (D7 in its research — the authentic
Metro result, an accepted WCAG failure) survives as the default, which is the point: a default that
can be overridden is still a position.

**The app bar's dimensions become theme tokens.** `HEIGHT`, `BUTTON` and `RING` are `private val`s
inside the component and `KvadrantAppBarGlyphSize` is a top-level constant, so the app bar is the one
surface a theme cannot reach — and, per Consequence 1.1d, the one surface that does not scale when
the rest of the theme does. Moving them into `KvadrantMetrics` fixes both at once: the kit can state
its numbers, and a scaled theme stops moving a page around a fixed bar.

The price is stated rather than discovered: `KvadrantColors` and `KvadrantMetrics` are `data class`es
with `abiValidation` switched on, so adding a parameter to either is a binary-incompatible change and
will show up as a diff somebody has to approve. That is the mechanism working, not an obstacle —
those two signatures have changed before without anybody noticing, which is why the validation is
there.

### D4. Semantic colours are named stock accents, not literals

`ShashkiColors.negative = KvadrantAccents.Red`, `.positive = KvadrantAccents.Green`,
`.inactive = KvadrantColors.dark().inactive`. The handoff's hexes are correct and are the same
numbers (§1.1b); writing them again puts one value in two files.

The kit's rule that red is reserved for cancellation in both apps — and that this is why the driver
accent is amber rather than red — survives unchanged, and is the reason `negative` is a semantic
name rather than an accent parameter.

### D5. The driver offer is a suspended saga, not a step that waits

The 15-second offer, and the cascade of offers behind it, are `petich`'s suspend/resume with a
deadline, not work performed inside an EXECUTION step. §1.4a: EXECUTION's default timeout is 10 s and
a cascade is several offers long, so a blocking implementation is correct exactly until the first
driver ignores an offer.

Rider cancellation is compensation from the middle of the saga, which is the same mechanism read
backwards, and is the demo's whole point.

### D6. The browser clients post to katcher's ingest endpoint directly

`POST {serverUrl}/api/reports` (§1.6). The katcher client artifact has no browser target, and adding
one to katcher is a change to a library in order to save a Ktor call in a consumer.

The consequence is honest and belongs here: the offline persistence and the build-uuid plumbing the
Android client gets for free are not free for us. What the browser clients send is whatever we choose
to send.

### D7. One process, so no Redis

`kompot-realtime-server` alone (§1.5a).

### D8. Goldens pin hinting and smoothing, and every fixture string is checked for coverage

Fixtures build their ramp through a shashki-local `portable()` over `ViddikPlatformTextStyle`
(§1.2b), applied to every slot **and** to every hand-built `TextStyle` — kvadrant's note is explicit
that fixtures constructing their own styles went on failing after the ramp was pinned.

Every fixture string goes through `ViddikGlyphCoverage.missingGlyphs`, and the check fails the
fixture rather than warning. `₽` is the reason (§1.2c), and it is in most of them.

### D10. Two bundles, and the number is that the roles are 5 % of one

Brief: two bundles, rider and driver, for a cleaner demo.
[B-16](../backlog/B-16-one-bundle-or-two.md) held the question open until
[D1](#d1-the-browser-is-a-decision-with-a-date-not-a-precondition), because two of the four routes
carried a per-bundle cost that would have decided it — a pinned Compose dev build paid once per
build, a DOM-overlay map paid once per page.

**Decision: two, as the brief said. The reason the question dissolved is that route 4 has neither of
those costs**, and the reason two is right anyway is a measurement, taken 2026-09-02 by building
`:shared-ui`'s own `wasmJsBrowserProductionWebpack`:

| | raw | gzipped |
|---|---|---|
| skiko's wasm — the Compose runtime | 8 640 316 | **3 328 940** |
| the webpack JavaScript glue | 377 025 | 75 589 |
| **everything shashki has written**, before dead-code elimination — both themes, every component, both screens, the map, the tile decoder, the projection | 698 377 | **173 808** |

The fixed cost of *being a Compose/Wasm bundle at all* is 3.4 MB gzipped and is identical for both
roles. Everything this project has authored is **5 % of it**. So:

- **One bundle saves a person nothing.** They fetch the same 3.4 MB either way, and get the other
  role's screens as a slice of 174 kB on top.
- **Two bundles cost the server a second copy** and cost nobody who uses one role. A person who uses
  both pays the runtime twice **unless** the content-hashed skiko file is byte-identical and served
  at the same path from both.

**That last clause was written as an assumption and is now measured (2026-09-02, B-28).** The rider
bundle exists, so there are two:

| Bundle | File | Size | sha256 |
|---|---|---|---|
| `:shared-ui` | `bfa5198fb2fe683c613a.wasm` | 8 640 316 | `089052ba37e2a6e8345117bf…` |
| `:rider` | `bfa5198fb2fe683c613a.wasm` | 8 640 316 | `089052ba37e2a6e8345117bf…` |

Same name, same size, same hash, from two independent webpack runs. So the second bundle is free for
a person who has already loaded the first, **provided both are served from one path** — which is now
a deployment instruction rather than a hope. The rider's own half is 1 087 188 gzipped bytes of wasm
and 100 128 of JavaScript: still a third of the runtime it rides in, and still the smaller number.

The third row is also the answer to a question nobody asked: at 174 kB before DCE, the cost of route
4's whole tile pipeline is invisible next to the runtime it rides in.

**Both bundles now exist, and the third measurement is the one this decision was really resting on
(2026-09-02, [B-29](../backlog/B-29-the-driver-bundle.md)).** Two bundles are only cheap if the
second is *assembled* rather than written, and that was an intention until there was a second one.
What it cost:

| What the driver bundle bound | Where it came from | New code |
|---|---|---|
| the address bar, both directions | `:rider`, moved to `:shared-ui` unchanged | none |
| `installCrashReporting` | `:crash-client`, already a port | none |
| money, distance, duration, coordinates | `:rider`'s `format`, moved to `:shared-ui` | one function, `asCoordinates` |
| `OfferCard` and its countdown | `:shared-ui`, drawn in B-04 | none |
| the theme | `DriverTheme`, which existed and had never been used | none |

Two of those moved rather than being copied, and moving them is the evidence: a port that only ever
had one binder is an arrangement somebody has called a port. The formatting had written its own
argument in advance — "how a price ends up rendered differently in the two bundles" — and a copy in
each bundle would have been that failure with extra steps.

What is genuinely the driver's and could not be borrowed is the **socket**: the rider polls, and each
of its requests fails on its own; a driver's shift *is* a connection being up. That is one repository
and one use case, and it is the only place the two applications are a different shape.

### D11. The server owns one screen, and it is the promo

Brief: "server-driven screens beside natively drawn ones". The kit's section 08 is "composition rules
for the screens the server sends as a tree". **Which screens was never recorded**, and the question
surfaced when somebody asked — [B-32](../backlog/B-32-which-screens-the-server-sends.md) is where it
was written down and this is the answer.

**Decision: one screen, entirely the server's, with no native version — the promotion. Nothing in the
ride flow is server-driven.**

**Why a whole screen rather than a panel.** A panel inside a native screen shows the seam at its
narrowest and never exercises the thing BDUI is for: a client meeting a component it does not know
and continuing. With a fallback beside it, the degradation is never reached and the kit's three
composition rules stay decorative. A screen with no native version has nowhere to fall back to, so
the property is load-bearing or the screen is blank — and which of those it is, is now a golden.

**Why the promo and not something in the flow.** Because it is the piece a product actually changes
between releases, and because nothing depends on it: if the server says nothing, the rider sees
"nothing on offer today" and orders a car exactly as before. Putting the fare breakdown or the trip
screen behind a tree would make the demo's core flow depend on a mechanism the demo is meant to
*show*, which is the opposite way round.

**The boundary, in one sentence: the server names roles and never values.**

| | |
|---|---|
| On the wire | component types and **token names** — `page_title`, `accent`, `body` — from `ShashkiTokens` in `:protocol`, so the vocabulary is declared once for both sides |
| In the client | `ShashkiDesignSystem` resolves those names into kvadrant's palette and shashki's ramp, and an unknown name falls back rather than failing |
| Never on the wire | a colour, a size, a font, a shape. A backend cannot paint an unreadable screen because it has no way to say what a colour is |

That property is kompot's, not ours — its tokens are open strings precisely so the toolkit assumes no
design system — and it is what makes handing a screen to a backend safe rather than reckless.

**What is built.** `GET /api/screens/promo` on the server, in kompot's own DSL out of kompot's own
components; `ServerScreen` in `:shared-ui` combining three renderer registries — kompot's core, its
standard set, and shashki's three from
[B-17](../backlog/B-17-kompot-renderer-invariants.md); a `/promo` route in the rider. Two goldens: the
tree drawn in this kit, and the same tree with one component type this build does not know, where the
rest still draws.

**Two costs, stated.**

- **The hole is silent.** kompot's `KompotDegradationSink` exists because "a hole is reported by
  nobody" and this application binds none, so a component nobody rendered is invisible to everyone
  including the deployment that shipped it. §1.7c already records that the sink has no kind for a
  *property* outside its allowed set; this is the other half.
- **The server names tokens with no compiler behind it.** `ShashkiTokens` is a shared vocabulary and
  nothing makes the server use it — kompot's tokens are strings by design. `PromoTreeTest` walks the
  encoded document and fails on a token outside the vocabulary, which is the only place that check
  can live.

**Deliberately not done: live updates.** `kompot-realtime` is a separate mechanism and §1.5a already
decided against Redis. A tree that changes under the reader is a second demo, and this one has not
been shown yet.

### D9. Documentation is in English

Code, comments, test names, exception messages and commit subjects in English, as everywhere in this
stack; this documentation tree in English too, which is this project's departure from the usual
Russian `docs/`. The kit and the brief are Russian and stay Russian: they are evidence, and evidence
is not translated.

---


### D12. The object store is a host, not a dependency — and s3kn has no scenario left

[B-07](../backlog/B-07-serve-pmtiles-from-bochka.md) closed with a sentence in its tail that is
actually a decision about the stack, and it belongs here rather than there: **shashki links nothing
from bochka.** The browser fetches tiles over ordinary ranged HTTP from wherever the archive is
hosted, so bochka is the host and not a library — what is pinned is the image, `ghcr.io/youndie/bochka:v0.5.0`,
and the dependency graph is untouched. That is the right shape and it was reached by building the
thing, not by preferring it.

**The consequence nobody stated is that s3kn left the stack silently.** §1 records it among the five
`-SNAPSHOT` libraries the brief assumed and the graph never contained; four of the other five have
since been used or explicitly excluded, and this one has not. An S3 client from Kotlin has exactly
one plausible scenario left in this product — **a driver uploading documents at onboarding**: a
licence, an insurance certificate, a photo of the car, written by a client that is not a browser
fetching a public object.

Three ways this can go, and the point of writing it down is that all three are decisions rather than
drift:

| | What it means |
|---|---|
| build the onboarding | s3kn gets its scenario, the driver bundle gets a screen, and the stack's object store is shown from both ends — a host for public reads and a client for authenticated writes |
| drop it | the stack table says so, with this paragraph as the reason, and nobody spends an afternoon looking for where it went |
| leave it | the version table keeps naming a library the product does not use, which is the state that produced this note |

Not resolved here: this is the record that a choice is owed, and the first option is the only one
that is an item.

## 3. Risks and open questions

### Risk 1. The browser target has no released path

**Mechanism.** Both clients are specified as Kotlin/Wasm; the map library publishes no `wasmJs`
variant, the upstream work is open and pinned to an unreleased Compose, and the alternative target
(`js`) is missing from two of our own libraries (§1.3).

**Mitigation.** D1: the map behind one interface, a spike that compares the four routes and writes a
choice down, and no other workstream blocked on it. For routes 2 and 3 that means watching for a
non-prerelease Compose Multiplatform containing merge commit
`dca97b20a50006b78bd0e777aeafbf2749d77915`, which is the condition upstream itself states.

**Checked again 2026-09-02 and nothing has moved.** The newest non-prerelease Compose Multiplatform
is still 1.12.0 of 2026-08-25; maplibre-compose #1081 and #209 both still carry `blocked-upstream`.
A week of no movement says nothing about the next one, and it is recorded so the following check is
a comparison rather than a first look.
**Route 4 removes the dependency instead of waiting on it** — a map drawn in Compose has no target
problem on any platform — and §1.8 is what makes that a priced option rather than a hope.

**Open.** Whether route 3 (map in the DOM, Compose over it) can carry the kit's screens at all, and
what fraction of route 4's tile pipeline a prototype has to build before the rest can be estimated.
Both are answered with the class picker and the trip screen, which put the most Compose on top of the
most map.

### Risk 2. The golden suite may be tied to one machine — measured, and it is not

**Mechanism, as it stood.** kvadrant records on macOS, and the reason given is a calibration suite of
its own (§1.2a). If shashki's fixtures turned out host-dependent too, the golden suite could only run
on the mac — and this stack's builds otherwise run on the Linux box, so a check that cannot run there
is a check that runs rarely.

**Measured on 2026-09-01, and the answer is that they are portable.** `skeleton_themes` — 390 × 844,
text-heavy, `$ 249` at 32 sp on a filled accent surface — was recorded on macOS and verified
unchanged on Ubuntu under WSL2, on the same commit, with the ramp pinned through
`ViddikPlatformTextStyle` (D8). `viddikVerify` passes there; the golden's md5 is identical before and
after, so nothing was quietly re-recorded.

**The number that makes the pass mean something is the failure.** A pass on its own says nothing —
a check that cannot fail passes everywhere. With one extra character in a label the same comparison
on the same machine reports **627 of 329 160 pixels differing, 0.19 % against a 0.05 % tolerance**,
and passes again when the character is removed; both runs under `--rerun-tasks`, so neither was
served from the build cache.

**Two earlier attempts at that control proved nothing and looked like proof**, which is worth more
than the result:

* the first read `$?` after a pipe, so it reported the exit status of `tail` rather than of Gradle —
  a green number produced by a command that cannot fail;
* the second edited the fixture on the remote machine, where the one-way file sync reverted the edit
  before Kotlin compiled it. The task genuinely ran, genuinely passed, and genuinely tested the
  unmodified fixture. The sync had to be paused for the control to be a control.

**Consequence.** `verifyOnCheck = true` in `:shared-ui`, so the goldens are inside `./gradlew check`
rather than beside it, and the same gate runs on the mac, on the Linux box and on CI's
`ubuntu-latest` — the second of which is now a host the claim has been tested against.

### Risk 3. Half the stack is a snapshot

**Mechanism.** booblik, bochka, s3kn, tracy and smtpkn are `-SNAPSHOT` in the working copies. A
reference service that cannot be rebuilt is a screenshot.

**Mitigation.** Before the demo is published, every dependency is a release or a resolved snapshot
pinned by build metadata, and the version table in §1 is re-read rather than remembered. This is a
backlog item, not a note.

**Closed 2026-09-02 for what is built, and handed to three items for what is not.** The graph was
re-read rather than the table: no `-SNAPSHOT` and no dynamic version anywhere across the four
modules or the plugin classpath, and a clean checkout of `HEAD` built green on an empty Gradle cache
(B-13). None of the five snapshot libraries is a dependency yet — the risk arrives with the items
that add them, [B-07](../backlog/B-07-serve-pmtiles-from-bochka.md),
[B-10](../backlog/B-10-crash-reports-from-the-browser.md) and
[B-14](../backlog/B-14-receipt-over-smtpkn-jvm.md), and each now carries it as an acceptance
criterion of its own. Closing it here and nowhere else would have been the failure this risk is
about: a mitigation recorded as done while the thing it mitigates has not happened yet. What pinning
still does not cover — republication under the same coordinate, and a single-homed repository host —
is in §1's amendment.

### Risk 4. smtpkn's JVM target is unclaimed

**Mechanism.** The library states that only `linuxX64` is claimed; the JVM target compiles and its
175 tests run in CI, but nothing has been released and nothing has used it in anger (§1.6d). TLS on
the JVM goes through `SSLEngine`, which is the one part not shared with the native path — that is,
the untested part is the part that talks to a real server.

**Mitigation.** A receipt test against Mailpit in the integration suite, on the JVM target, with TLS
on. It gates the feature, and a failure is reported upstream rather than routed around.

**Closed 2026-09-02: the JVM target works, and the first real use of it found a defect elsewhere.**
The receipt arrives over a verified `STARTTLS` handshake — `SSLEngine` and all — and the control
proves the verification rather than the socket. What broke is not TLS but `isEncrypted`, which is
never assigned and therefore makes `AUTH` after `STARTTLS` impossible without a flag that lies;
§1.6d1 has it. This is the risk behaving exactly as written: "compiles and runs in CI" became
"claimed for what a reference service exercised", and the part nobody exercised is the part that was
wrong.

### Risk 5. pmtiles in the browser is untested from Kotlin

**Mechanism.** A `pmtiles://` source is not native to MapLibre GL JS: it needs the `pmtiles` protocol
handler registered on the GL JS instance, which is a JavaScript call. Whether that registration is
reachable from Kotlin/Wasm — and whether the wrapper of D1's chosen route exposes the hook at all —
has not been checked.

**Mitigation.** It is part of the D1 spike: the spike's success criterion is a rendered city from
`city.pmtiles`, not a rendered blank style.

**Half of it is now off the table (2026-09-02).** [B-06](../backlog/B-06-city-extract-and-tiles.md)
drew both style documents from `city.pmtiles` through `pmtiles.Protocol` in a browser, so the
archive, the protocol handler and the glyph endpoint are known to work together and the spike
starts from a picture rather than from a blank. What is still unchecked is the half this risk is
actually about: reaching `maplibregl.addProtocol` **from Kotlin/Wasm**. B-06's page is JavaScript.

**This risk belongs to routes 1–3 only.** Route 4 (§1.8) reads the archive itself over ranged HTTP,
so there is no protocol handler and no JavaScript call — one of the four items that route closes.

### Risk 6. Nobody has served a pmtiles archive out of bochka

**Mechanism.** bochka supports `Range` (§1.6), which is the requirement on paper. Browser tile
traffic is many small ranged reads against one large object, which is a different load from the ones
bochka's own measurements cover.

**Mitigation.** Measure with the real archive before the tile endpoint is part of any demo, and
record the numbers with what was measured. If it does not hold, the fallback is a static file served
beside the app, which costs the demo one talking point and nothing else.

**Closed 2026-09-02, and it holds with room to spare ([B-07](../backlog/B-07-serve-pmtiles-from-bochka.md)).**
`ghcr.io/youndie/bochka:v0.5.0` on the Linux box, the real 16.6 MiB archive, `map/tile_serving.py`
as the client. Both load shapes the map needs, because they are not the same shape:

| Load | Requests | Bytes | p50 | p99 | max | Wall |
|---|---|---|---|---|---|---|
| the whole archive by range — 810 tiles plus header and root directory | 812 | 17 400 568 | 0.85–1.13 ms | 1.77–3.20 ms | 2.54 ms | 740–1 103 ms |
| the glyph PBFs as whole objects | 512 | 1 180 298 | 0.75 ms | 2.25 ms | 2.83 ms | 453 ms |

Ranges over one large object are not slower than whole reads of small ones, which was the thing in
doubt. Three runs rather than one: the spread above is across them, and the first run's 27.67 ms
outlier is a cold start and is not in the table for that reason.

**What the numbers are and are not.** Loopback on one machine, so this isolates bochka's ranged-read
path and says nothing about the network a browser is on. That is the right thing to isolate here —
the question was whether the store's own behaviour changes shape under many ranges, and it does not.

**The load-bearing finding is not a number.** A browser cannot sign a request, so the whole
arrangement depends on reads that are not signed, and **two things have to be switched on for that
and neither is a default**:

| Fact | Where verified |
|---|---|
| With `BOCHKA_ANONYMOUS=1` alone, an unsigned `GET` is **403** | measured against the running container |
| It becomes 206 after a public-read bucket policy — `s3:GetObject` for `Principal: *` | same |
| Without a CORS configuration a preflight is **403**, and a plain `GET` carrying `Origin` comes back with **no `Access-Control-Allow-Origin` at all** — so the browser would refuse a response `curl` accepts | same |
| With a CORS rule exposing `Content-Range`, `Accept-Ranges` and `Content-Length`, the preflight answers 200 and the ranged read comes back 206 with `Content-Range` and the allow-origin header | same |

`Range` is not on the CORS safelist, so a browser preflights every tile read. A deployment that set
the bucket policy and forgot the CORS rule would work from `curl` and fail in the product, which is
the most confusing shape a failure can take — hence it being written down rather than left to be
rediscovered.

### Open question 1. The kit's questions — answered, except the one that moves a number

The three the kit addressed to the client side, and the fourth the research added, were put to the
owner and came back:

| Question | Answer |
|---|---|
| May the 54 dp app bar carry a filled accent accept button? | **Simplify for now** — no new app-bar variant; the offer's accept is the filled surface the kit already draws in the card |
| May the offer screen suppress the app bar entirely? | Same: **not for now**. The screen keeps the bar |
| Is the light theme kvadrant's stock light, or does it wait for the kit's next pass? | **Stock `KvadrantColors.light()`**, verified by goldens. The light half of the fixture set is scoped work rather than a wait |
| §1.1c's 4/3: is the kit's spacing a deliberate scale-up or a skipped `× 0.75`? | **12 dp, as drawn** — the kit wins over the derivation, and §1.1c records why the derivation says otherwise |

Three of the four remove the reason fixtures were dark-only, so the suite doubles: every
fixture gains a light variant, and `KvadrantColors.light()` is checked by a golden rather than
assumed to be the kit's intent.

### Open question 2. One wasm bundle or two

The brief proposes two, for a cleaner demo, and that is very likely right. It is worth re-asking once
D1 lands, because route 2 and route 3 have different fixed costs per bundle.

### Open question 3. The city — delegated and chosen, revocably

"Some neutral European city." Chosen on the criteria already written down — a compact graph, an
airport the fixtures can name, and nothing that reads as a statement — plus one checked in §1.2e,
that its street names do not leave the bundled Latin face.

**Proposal: Ljubljana.** About 295 000 people and a genuinely small road graph; one airport at
roughly 26 km, which is the scale the kit's own fixture already assumes ("Airport, terminal B ·
18.4 km · 26 min"); Latin script with `č ž š`, all covered. This is a choice taken under delegation
rather than a finding, and [B-06](../backlog/B-06-city-extract-and-tiles.md) records what would
overturn it: the OSM extract and the pmtiles archive turning out large, or the GraphHopper import
turning out slow.

**Settled 2026-09-02: nothing overturned it, and the numbers are in B-06.** A 41 MB extract, a
**16.6 MiB** `city.pmtiles` whose biggest tile is 124 kB gzipped, and a GraphHopper import of
**under four seconds** on a 98 566-node graph. The proposal's own arithmetic was the one thing that
missed: the airport is at **26.3 km · 20 min** by road, not the kit's 18.4 km · 26 min, so the
fixtures took the router's numbers. The city is no longer a proposal.

### Open question 4. Whether `shashki-api` is published — **answered: no, and the condition is named**

A separate KMP artifact for the protocol, as groundwork for mobile clients. It was deferred because
deferring costs nothing and getting it wrong costs something, and because the answer would be clearer
once a client existed.

**Two exist now, and the answer is no** (2026-09-02). Both consume `:protocol` as a project
dependency and neither has ever needed it as a coordinate; publishing it would add a release cycle,
a compatibility promise and a version to keep in step, in exchange for nothing this repository can
use. The condition that would change it is a consumer that cannot be a module in this build — a
mobile client, or somebody else's service — and until one appears the artifact would be published
for an audience of nobody.

---

## 4. What happens next

The order of work and the acceptance criteria live in [backlog.md](../../backlog.md). Four things
have to be nailed down before anything else is worth building:

1. **D1's spike** — the map, on all four routes, judged against the same two screens. Everything
   about the client's shape follows from it, and route 4 (§1.8) is the one whose cost nobody can
   estimate without building part of it.
2. **The two kvadrant-ui hooks** (D3) — an overridable `onAccent` and the app bar's dimensions as
   theme tokens. Small, additive, and upstream of everything else on the client: until they land,
   the two components below cannot be built the way the kit draws them.
3. **The foundation values and the two components** — `ShashkiTypography`, `ShashkiMetrics`,
   `ShashkiColors` and the `portable()` pin, with `ClassTile` and `OfferCard` on them. Those two
   carry the whole of §1.1's divergence: the accent fill with black ink, the 54/200 figure, the
   tabular timer. If they come out right the rest of the kit is transcription.
4. **The golden host measurement** (Risk 2), because it decides where the acceptance gate lives, and
   an acceptance gate decided late is an acceptance gate nobody has.

The server layers wait on none of these and can start in parallel: §1.4 and §1.5 verified that the
saga and the server-driven screens map onto the brief as written.

## Code anchors

There is no shashki source tree yet. What follows is what §1 was verified against; the paths are
inside the sibling repositories of this stack, and they are what `code_anchors.py --repos ..`
resolves.

| Subject | Code |
|---|---|
| kvadrant-ui foundation | `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/theme/` |
| kvadrant-ui tiles, app bar, text | `kvadrant-ui/kvadrant-core/src/commonMain/kotlin/io/github/youndie/kvadrant/components/KvadrantTile.kt`, `.../components/KvadrantAppBar.kt`, `.../foundation/KvadrantText.kt` |
| kvadrant-ui targets and viddik wiring | `kvadrant-ui/kvadrant-core/build.gradle.kts` |
| the golden pin to re-implement | `kvadrant-ui/kvadrant-core/src/desktopTest/kotlin/io/github/youndie/kvadrant/type/PortableTypography.kt` |
| viddik contract | `viddik/README.md`, `viddik/viddik-testing-core` |
| petich phases and timeouts | `petich/petich-core/src/commonMain/kotlin/Petich.kt` |
| kompot component registry | `kompot/kompot-registry-processor/src/main/kotlin/io/github/youndie/kompot/registry/processor/KompotRegistrySymbolProcessor.kt` |
| kompot client targets | `kompot/kompot-client/build.gradle.kts` |
| booblik client | `booblik/booblik-client/build.gradle.kts` |
| katcher client targets and ingest | `katcher/client/build.gradle.kts`, `katcher/README.md` |
| shildik targets | `shildik/oidc-auth-client/build.gradle.kts`, `shildik/README.md` |
| smtpkn platform claims | `smtp-client/build.gradle.kts`, `smtp-tls-jvm` |
| bochka object surface | `bochka/README.md`, `bochka/bochka-embedded` |
