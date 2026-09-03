---
id: B-35
title: "The server as an image, and the graph that has to be inside it"
status: done
priority: P1
size: M
stage: stage-4-elsewhere
---

# B-35 — The server as an image, and the graph that has to be inside it

`:server` has the `application` plugin and a `mainClass`, and that is the whole of its packaging:
there is no Dockerfile, no image task, and nothing in CI that produces an artefact. The demo runs
from Gradle on a machine that already has the repository, a Postgres and an OSM extract — which is
every machine except the one somebody would want to show it on.

- **It is a JVM image and stays one, and that is worth saying because the rest of the portfolio is
  not.** shildik and katcher ship Kotlin/Native binaries; this server embeds GraphHopper, which is a
  JVM library, so `linuxX64` is not an option and nobody should spend an afternoon discovering that.
  Research §1.6e is the measurement.
- **The interesting content is not the jar, it is the prepared graph.** §1.6e measured it: importing
  Ljubljana's extract costs **3 168 ms** and opening an already-prepared graph directory costs
  **22 ms**. An image that imports on start is a container that takes three seconds to answer and
  pays it again on every restart and every replica; an image that carries the prepared directory
  answers immediately and is larger. The extract itself is 41 MB and deliberately not in git
  ([B-06](B-06-city-extract-and-tiles.md)), so "carry it" means the build fetches it.
- **The bundles have nowhere to be served from.** `:rider` and `:driver` produce static files and
  this server serves none — no `staticFiles`, no SPA route. Either the image carries them and the
  server serves them, or a static host does and the deployment has to place the page contract
  (`globalThis.SHASHKI`) beside them. That decision belongs here, because it is the difference
  between one artefact and three.
- The rejected alternative is Jib with no graph and an import at start. It is smaller and simpler
  and it makes the first request of every restart three seconds slow — on the one screen the demo
  opens with.
- Deliberately **not** covered: the pmtiles archive. [B-07](B-07-serve-pmtiles-from-bochka.md)
  settled that shashki links nothing from bochka — the browser fetches tiles over ordinary HTTP from
  wherever they are hosted, so the archive is not this image's content.

- AC: `./gradlew :server:image` (or whatever the task is called) produces an image, and
  `docker run` against a Postgres answers `/health` — with no repository, no Gradle and no OSM
  extract on the host.
- AC: the start-up cost is measured and written down, and it is the prepared-graph number rather
  than the import one. If the decision goes the other way, the three seconds are stated as the price
  rather than discovered by whoever demos it.
- AC: the tag names a commit rather than `latest`, so an image that misbehaves can be read.
- AC: the base images are pinned and, if the build is multistage, the build and runtime images are a
  pair by their C library — a jar built against one glibc and run on another fails at the first
  native call, which for this server is GraphHopper's.
- AC: where the two browser bundles are served from is decided and written down, not left to the
  reader of a compose file.
- AC: `docker compose -f docker/compose.yaml up` brings up **the whole stand** — this server on its
  image beside the Postgres, shildik and bochka that are already there — and a browser reaches a
  priced class picker. Today that file stands up the services shashki *talks to* and says in its own
  first line that it is not a deployment; what it cannot do is run shashki. An image nobody has
  started next to its dependencies is an artefact rather than a demo.
- Anchors: `server/build.gradle.kts`,
  `server/src/main/kotlin/io/github/youndie/shashki/server/feature/route/RoutingConfig.kt`,
  `docker/compose.yaml`

## What it turned out to be

**Four things were wrong, and every one of them presented as something else.**

| What happened | What it actually was |
|---|---|
| The container died on start with `Profiles do not match: car\|-1705186244` against `car\|26199302` | GraphHopper stores a hash of the profile beside the data. The image had baked a graph that *happened to be on the build machine*, from an older configuration |
| `FileNotFoundException: /app/graph/gh.lock (Permission denied)` | `load` takes a native file lock **in** the graph directory before it maps anything. "The server writes nothing, so nothing needs to be writable" was wrong, and it was written in a comment as a reason |
| `GET /` answered **200 with an empty body**, and the browser showed a blank window | `index.html` was written with a restrictive umask, reached the image as `600 root`, and the container runs as 1000. Ktor sizes the response from a file it can stat and then cannot read |
| `exec: "/app/bin/server": permission denied` | the fix for the line above — a blanket `filePermissions` on the copy task — took the execute bit off the launcher. The mode belongs on the content, not on the task |

**The graph is prepared by the build, from the extract, with this server's own code.** That is the
answer to the first row and it is not a workaround: an option to supply a ready-made graph saves the
three seconds of import and buys the mismatch back, so `:server:prepareGraph` takes one input and the
image is made of one thing. `RoutingConfig` also learned that a prepared graph stands on its own — it
had checked for the extract first, so a container carrying 14 MB of graph and no 41 MB of extract
fell back to straight lines.

**The bundles had never been loaded by anything.** `:rider` and `:driver` produce `rider.js`,
`driver.js` and their wasm — and **no `index.html`**, because Kotlin/Wasm generates one only if you
provide it. The wasm compiled, the goldens were taken on the desktop target, and no page had ever
opened either bundle. So this item wrote the two pages, and with them the missing half of B-28's page
contract: both bundles read `globalThis.SHASHKI` and nothing had ever set it. `/config.js` is served
from the server's own environment, escaped, with a test that a value cannot leave its string literal.

**Where the bundles are served from: here, and the cost is named.** The rider at `/`, the driver at
`/driver`, `default("index.html")` under both so `/trip/abc` is the client's route rather than a 404.
The trade is that D10's saving does not apply: the two prefixes each fetch the 8.6 MB Compose
runtime, and they are byte-identical — `md5sum` inside the image says so. A deployment that wanted
the saving would put the content-hashed runtime on one path, which this arrangement does not.

**And the API still wins over a wildcard mounted at the root.** Ktor matches by specificity rather
than by declaration order, which means it works and means it works for a reason nobody wrote down —
so `BundleRoutingTest` is what says so, because the failure mode is the whole API answering with a
web page.

**Measured rather than asserted**, on the running container, three restarts: healthy after
**1 482 / 2 124 / 3 707 ms**, of which the graph is **180 / 324 / 400 ms** — against 3 168 ms to
import. The prepared directory turns the map from most of the start-up into a tenth of it.

**The stand runs the whole thing.** `docker compose up` brings up Postgres, shildik, bochka and
shashki; a browser at `http://127.0.0.1:18080` fetches the page, the config, `rider.js`, both wasm
files and then **`POST /api/quotes`** — which is the class picker asking what the journey costs, on
the real road graph (22 806 m, the city measurement's own number). That request sequence is the
evidence, and it is what there is: headless Chrome will not settle on a continuously-drawing Compose
canvas long enough to write a `--screenshot`, so there is no picture of it.

**Two smaller things worth keeping.** `pull_policy: never`, because a compose pointed at a locally
built image otherwise fails with `pull access denied … or may require docker login` — a message about
credentials for a problem that is "you have not built it yet". And the tag is passed in rather than
read from git: the build machine is a checkout with no `.git`, so `git rev-parse` there
answers about nothing.

**Not covered:** the image is 569 MB, of which 104 MB is the JRE base, 41 MB the application, 31 MB
both bundles and 14 MB the graph — the rest is the base image's own layers. Nothing here tries to
make it smaller; a jlink runtime or a distroless base is a separate argument.
