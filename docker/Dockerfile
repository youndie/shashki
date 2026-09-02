# The server, as one artefact: the application, the road graph it routes on, and the two browser
# bundles it serves.
#
# **One stage, and that is a decision.** A multistage build would run Gradle inside the image, which
# means fetching the whole dependency graph on a machine that has no Gradle cache — minutes, every
# time — and it would put a build image and a runtime image in the same file, which have to be a pair
# by their C library or the JVM fails at its first native call. `./gradlew :server:image` assembles
# the context outside and this copies it, so there is one base image and nothing to pair it with.
#
# The base is pinned by digest as well as by tag. A tag moves; a digest is the thing that was
# actually tested, and research §3 Risk 3 is about exactly this class of surprise.
FROM eclipse-temurin:25-jre-noble@sha256:b4c93a50fc67612798db73d68ca3b0ee4ebdd51736e59cca370e689b9797037e

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
