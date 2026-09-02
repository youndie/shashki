---
id: B-35
title: "The server as an image, and the graph that has to be inside it"
status: open
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
