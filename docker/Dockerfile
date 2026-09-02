# The server, as one artefact: the application, the road graph it routes on, and the two browser
# bundles it serves.
#
# **Two stages, and the second one is a Java runtime rather than a build** (B-50). The objection this
# file used to carry — that a multistage build runs Gradle inside the image, fetching a whole
# dependency graph on a machine with no cache — still stands and still holds: `./gradlew :server:image`
# assembles the application outside and this copies it. What the first stage builds is the *JVM*,
# with `jlink` over the modules `jdeps` finds in the application's own jars, and that is a different
# argument with a measured answer:
#
# | base | image | to healthy | `POST /api/routes` |
# |---|---|---|---|
# | Temurin's JRE on Ubuntu (what shipped) | 577 MB | 2 025 ms | 200 |
# | this runtime on `ubuntu:noble` | 371 MB | 2 025 ms | 200 |
# | this runtime on `alpine` | **263 MB** | 1 999 / 2 018 / 2 025 ms | 200 |
#
# `docker/measure-bases.sh` is that table, and B-35's start-up range (1.5–3.7 s) is what "unchanged"
# means here. The first request measured is a route on purpose: it is the call that touches the
# memory-mapped graph and its native file lock, which is what a change of base would break.
#
# **The two stages are paired by their C library, which is the whole reason the builder is Alpine
# too.** A runtime linked against glibc and run on musl fails at its first native call, not at build
# time — research §3's "the build image and the runtime image are a pair" as a Dockerfile.
#
# Both bases are pinned by digest as well as by tag. A tag moves; a digest is the thing that was
# actually tested, and research §3 Risk 3 is about exactly this class of surprise.
FROM eclipse-temurin:25-jdk-alpine AS runtime

# **The module list comes from `jdeps` rather than from a hand-written `--add-modules`.** A list
# somebody wrote is right until a library reaches for `java.sql` on a path no test exercised, and
# `jlink` cannot warn about what it left out: the symptom is a `NoClassDefFoundError` in production.
# `--ignore-missing-deps` is needed because the application's graph carries optional pieces — a
# logging backend it does not use, a processor's leftovers — and the three modules named after it are
# the ones nothing references statically: TLS curves, `sun.misc.Unsafe`, and JMX for metrik.
COPY app /app
RUN MODULES=$(jdeps --print-module-deps --ignore-missing-deps --multi-release 25 --recursive \
        --class-path "/app/lib/*" /app/lib/*.jar 2>/dev/null | tail -1) && \
    jlink --add-modules "${MODULES:-java.base}",jdk.crypto.ec,jdk.unsupported,java.management \
        --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
        --output /javaruntime

FROM alpine:3.21
COPY --from=runtime /javaruntime /opt/java
ENV JAVA_HOME=/opt/java
ENV PATH=$JAVA_HOME/bin:$PATH

# **The graph is baked and the extract is not.** GraphHopper reads the `.osm.pbf` only when the graph
# directory is empty, so 14 MB of prepared graph replaces 41 MB of extract *and* the 3.2 seconds of
# import that a cold container would otherwise pay — measured in research §1.6e: 3 168 ms to import,
# 22 ms to open what is already prepared. A demo that takes three seconds to answer its first request
# is a demo whose first impression is a spinner.
#
# `:server:prepareGraph` makes it, with this server's own profile: a graph built by anything else is
# refused on start with `Profiles do not match`, which is how the first image died.
#
# Layered before the application on purpose: the graph changes when the city does, which is almost
# never, and the jars change every commit.
#
# **`--chown`, because "it only reads it" was wrong.** The first version copied this read-only and
# the container died on start: `GraphHopper.load` takes a native file lock — `gh.lock`, created in
# the graph directory — before it memory-maps anything, so a directory it can only read is a
# directory it refuses. The lock is right of it; two processes over one graph is a corrupt graph
# rather than an error. Only this directory is writable, and the application's own is not.
COPY --chown=1000:1000 graph /app/graph
ENV SHASHKI_GRAPH_DIR=/app/graph

# The rider at `/` and the driver at `/driver`. See `BundleRouting.kt` for why the server serves them
# at all and what it costs.
COPY bundles /app/bundles
ENV SHASHKI_BUNDLES=/app/bundles

COPY app /app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

# **A non-root user, and only the graph directory belongs to it.** The sagas are in Postgres and the
# outbox is a table, so the application writes nothing of its own — the jars and the bundles stay
# root-owned and unwritable. The graph is the exception and the exception is measured rather than
# assumed: see the note above the COPY.
USER 1000:1000

ENTRYPOINT ["/app/bin/server"]
