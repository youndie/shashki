#!/usr/bin/env bash
# Three ways to ship the same server, measured rather than argued (B-50).
#
# B-35 shipped 569 MB and wrote down the split: the JRE base 104, the application 41, both bundles
# 31, the graph 14 — and roughly 380 MB of an operating system the server does not call. This script
# is where the argument about that 380 MB is had, and it is the only honest way to have it: the
# candidates differ in their C library, and a runtime linked against one and run on another fails at
# its first native call rather than at build time.
#
#   ./gradlew :server:image -PcommitSha=$(git rev-parse --short HEAD)   # assembles server/build/image
#   bash docker/measure-bases.sh
#
# What it prints: three sizes, three times to `/health`, and whether each one can answer
# `POST /api/routes` — the call that would fail if the memory-mapped graph or its native file lock
# did not survive the change of base.
set -euo pipefail

CONTEXT=${CONTEXT:-server/build/image}
PORT=${PORT:-18099}
NETWORK=${NETWORK:-docker_default}
DB_URL=${DB_URL:-jdbc:postgresql://shashki-db:5432/shashki}
DB_USER=${DB_USER:-shashki}
DB_PASSWORD=${DB_PASSWORD:-secret}
[ -d "$CONTEXT/app" ] || { echo "no image context at $CONTEXT — run ./gradlew :server:image first" >&2; exit 2; }

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# **The module list comes from `jdeps`, not from a guess.** A hand-written `--add-modules` is a list
# that is right until a library reaches for `java.sql` on a code path nobody exercised in a test —
# and `jlink` cannot report what it left out, because a missing module is a `NoClassDefFoundError` in
# production. `--ignore-missing-deps` is needed because the application's own dependency graph has
# optional pieces (a logging backend it does not use, an annotation processor's leftovers).
jlink_stage() {
  cat <<'DOCKERFILE'
FROM eclipse-temurin:25-jdk-noble AS runtime-builder
COPY app /app
RUN MODULES=$(jdeps --print-module-deps --ignore-missing-deps --multi-release 25 --recursive \
        --class-path "/app/lib/*" /app/lib/*.jar 2>/dev/null | tail -1) && \
    echo "modules: $MODULES" && \
    jlink --add-modules "${MODULES:-java.base}",jdk.crypto.ec,jdk.unsupported,java.management \
        --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
        --output /javaruntime
DOCKERFILE
}

app_layers() {
  cat <<'DOCKERFILE'
COPY --chown=1000:1000 graph /app/graph
ENV SHASHKI_GRAPH_DIR=/app/graph
COPY bundles /app/bundles
ENV SHASHKI_BUNDLES=/app/bundles
COPY app /app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
USER 1000:1000
ENTRYPOINT ["/app/bin/server"]
DOCKERFILE
}

# A — what ships today: Temurin's own JRE on Ubuntu 24.04.
cp docker/Dockerfile "$work/A"

# B — a jlink runtime on the same distribution the JDK that linked it runs on. **The pairing is the
# point**: glibc 2.39 in the builder, glibc 2.39 at runtime.
{ jlink_stage; echo "FROM ubuntu:noble"; echo "COPY --from=runtime-builder /javaruntime /opt/java"; \
  echo 'ENV JAVA_HOME=/opt/java'; echo 'ENV PATH=$JAVA_HOME/bin:$PATH'; app_layers; } > "$work/B"

# C — the same runtime on Alpine, which is musl rather than glibc, so the builder is Temurin's Alpine
# JDK. Mixing the two is the failure this script exists to make visible rather than to reason about.
{ jlink_stage | sed 's|25-jdk-noble|25-jdk-alpine|'; echo "FROM alpine:3.21"; \
  echo "COPY --from=runtime-builder /javaruntime /opt/java"; echo 'ENV JAVA_HOME=/opt/java'; \
  echo 'ENV PATH=$JAVA_HOME/bin:$PATH'; app_layers; } > "$work/C"

measure() {
  local name=$1 dockerfile=$2 tag="shashki/server:measure-$1"
  echo "=== $name"
  docker build -q --tag "$tag" --file "$dockerfile" "$CONTEXT" > /dev/null || { echo "  build failed"; return; }
  # **Both numbers, because B-35's is the first one.** `docker images` reports what the image
  # weighs with every layer of its base; `image inspect` on this daemon reports the content this
  # image adds. Comparing one against the other is how a 569 MB image becomes a 161 MB claim.
  echo "  size: $(docker images --format '{{.Size}}' "$tag" | head -1) total, $(docker image inspect "$tag" --format '{{.Size}}' | awk '{printf "%.0f MB", $1/1000000}') content"

  # **On the stand's network and against the stand's database.** A server with no `DB_URL` does not
  # get as far as opening the graph, so measuring its start-up would be measuring a failure.
  local id started
  id=$(docker run -d --rm --network "$NETWORK" \
        -e DB_URL="$DB_URL" -e DB_USER="$DB_USER" -e DB_PASSWORD="$DB_PASSWORD" \
        -p "127.0.0.1:$PORT:8080" "$tag")
  started=$(date +%s%3N)
  local healthy=""
  for _ in $(seq 1 200); do
    if curl -sf -o /dev/null "http://127.0.0.1:$PORT/health"; then healthy=$(( $(date +%s%3N) - started )); break; fi
    sleep 0.1
  done
  if [ -n "$healthy" ]; then echo "  healthy after: ${healthy} ms"; else echo "  never became healthy"; fi

  # **The first request is a route on purpose.** It is the call that touches the memory-mapped graph
  # and the native file lock, which is what a change of base image would break.
  local route
  route=$(curl -s -o /dev/null -w '%{http_code}' -X POST "http://127.0.0.1:$PORT/api/routes" \
    -H 'Content-Type: application/json' \
    -d '{"from":{"lat":46.0511,"lon":14.5051},"to":{"lat":46.2237,"lon":14.4576}}' || echo "000")
  echo "  POST /api/routes: $route"
  docker rm -f "$id" > /dev/null 2>&1 || true
}

measure current "$work/A"
measure jlink-ubuntu "$work/B"
measure jlink-alpine "$work/C"
