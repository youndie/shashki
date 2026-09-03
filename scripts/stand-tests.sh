#!/usr/bin/env bash
# The guards that need the stand, run against it.
#
#   docker compose -f docker/compose.yaml up -d
#   bash docker/bootstrap-shildik.sh && bash docker/bootstrap-documents.sh
#   bash docker/upload-tiles.sh build/city/city.pmtiles
#   bash scripts/stand-tests.sh
#
# **`./gradlew check` runs 468 tests and skips twelve, and the twelve are the seams.** Every test
# that talks to another service is gated on an `assumeTrue` — a running shildik, a booblik, a bochka,
# a Mailpit — because CI has none of them, and that gate is the right call: a suite that fails for
# want of a container is a suite people learn to ignore. What was missing is this file. The services
# are up on the build box for hours at a time and nothing pointed the gated tests at them, so the
# evidence for sign-in, the broker, the object store, the receipt's TLS and the tile reads was a run
# somebody did by hand once, months ago (B-88).
#
# **It asserts `skipped=0`, not the exit code.** An `assumeTrue` skip is green, so a run of this
# script that satisfied nothing would look exactly like a run that proved everything — which is the
# defect B-87 found in one of these tests' own written-down instructions.
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

# The addresses `docker/compose.yaml` publishes. Override any of them for a stand somewhere else.
export SHASHKI_SERVER=${SHASHKI_SERVER:-http://127.0.0.1:18080}
export SHASHKI_SHILDIK=${SHASHKI_SHILDIK:-http://127.0.0.1:18081}
export SHASHKI_BOCHKA=${SHASHKI_BOCHKA:-http://127.0.0.1:19000}
export SHASHKI_BOOBLIK=${SHASHKI_BOOBLIK:-127.0.0.1:19092}
export SHASHKI_TILES=${SHASHKI_TILES:-http://127.0.0.1:19000/tiles/city.pmtiles}
export SHASHKI_OSM_FILE=${SHASHKI_OSM_FILE:-$PWD/build/city/Ljubljana.osm.pbf}

# **Mailpit is started here rather than put in the compose file.** It is not part of the product's
# stand — nothing in shashki talks to it in a demo — it is a mail server that exists to be asserted
# against, so it lives for the length of this script. Two certificates, because the second test is
# the control: the same code pointed at a CA that signed nothing must fail (B-14, B-87).
MAILPIT_TLS=${MAILPIT_TLS:-/tmp/shashki-mailpit-tls}
started_mailpit=0
if [ -z "${SHASHKI_MAILPIT:-}" ]; then
  if command -v docker >/dev/null && command -v openssl >/dev/null; then
    mkdir -p "$MAILPIT_TLS"
    [ -f "$MAILPIT_TLS/cert.pem" ] || openssl req -x509 -newkey rsa:2048 -nodes \
      -keyout "$MAILPIT_TLS/key.pem" -out "$MAILPIT_TLS/cert.pem" -days 30 \
      -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' 2>/dev/null
    [ -f "$MAILPIT_TLS/wrong-ca.pem" ] || openssl req -x509 -newkey rsa:2048 -nodes \
      -keyout "$MAILPIT_TLS/wrong-key.pem" -out "$MAILPIT_TLS/wrong-ca.pem" -days 30 \
      -subj '/CN=nobody' 2>/dev/null
    chmod 644 "$MAILPIT_TLS"/cert.pem "$MAILPIT_TLS"/key.pem "$MAILPIT_TLS"/wrong-ca.pem
    docker rm -f shashki-mailpit >/dev/null 2>&1
    docker run -d --name shashki-mailpit -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 \
      -v "$MAILPIT_TLS":/tls -e MP_SMTP_TLS_CERT=/tls/cert.pem -e MP_SMTP_TLS_KEY=/tls/key.pem \
      axllent/mailpit:latest >/dev/null 2>&1 && started_mailpit=1
    for _ in $(seq 1 20); do
      curl -sf -o /dev/null http://127.0.0.1:8025/api/v1/messages && break
      sleep 1
    done
    export SHASHKI_MAILPIT=127.0.0.1:1025
    export SHASHKI_MAILPIT_API=http://127.0.0.1:8025
    export SHASHKI_MAILPIT_CA="$MAILPIT_TLS/cert.pem"
    export SHASHKI_MAILPIT_WRONG_CA="$MAILPIT_TLS/wrong-ca.pem"
  fi
fi
cleanup() { [ "$started_mailpit" = 1 ] && docker rm -f shashki-mailpit >/dev/null 2>&1; }
trap cleanup EXIT

# **katcher is not in the stand**, so its guard is the one this script cannot satisfy on its own.
# Point `SHASHKI_KATCHER_URL` and `_KEY` at a running one and it joins the set; without them it is
# reported as uncovered below rather than passed over in silence.
if [ -n "${SHASHKI_KATCHER_URL:-}" ]; then export SHASHKI_KATCHER_URL SHASHKI_KATCHER_KEY; fi

# **Four of these guards are about tokens, so the stand has to be the authenticated one.** Run
# against a stand whose `SHASHKI_OIDC_ISSUER` is empty, they fail saying "the stand is not protecting
# anything" — which is true and reads like a defect. `GET /api/rides?mine=true` is protected and has
# no side effects, so its status is the cheapest way to ask which stand this is. Found running this
# script from a fresh clone of the published repository, where the stand happened to be open (B-88).
protected_status=$(curl -s -o /dev/null -w '%{http_code}' -m 5 "$SHASHKI_SERVER/api/rides?mine=true" 2>/dev/null)
if [ "$protected_status" != "401" ]; then
  echo "warning: $SHASHKI_SERVER answered $protected_status to a protected route, not 401." >&2
  echo "         This stand has no provider configured, so the four sign-in guards will fail." >&2
  echo "         Bring it up without the open-auth override to check them." >&2
  echo >&2
fi

echo "the stand these guards are pointed at:"
printf '  %-24s %s\n' server "$SHASHKI_SERVER" shildik "$SHASHKI_SHILDIK" bochka "$SHASHKI_BOCHKA" \
  booblik "$SHASHKI_BOOBLIK" tiles "$SHASHKI_TILES" osm "$SHASHKI_OSM_FILE" \
  mailpit "${SHASHKI_MAILPIT:-<none - the receipt TLS guard is unchecked>}" \
  katcher "${SHASHKI_KATCHER_URL:-<none - the crash ingest guard is unchecked>}"
echo

# **One invocation per task, not one invocation listing five.** Written as a single command line,
# Gradle binds each `--tests` to the task it follows, and a filter that lands on the wrong task
# leaves that task matching nothing — it does not run, its previous report stays on disk, and the
# summary below reads it as this run's result. That happened on the first version of this script:
# `:server:test`'s report was four minutes older than the run that supposedly produced it.
started_at=$(date +%s)
gradle_status=0
run() { # task, then filters
  local task=$1; shift
  local args=()
  for t in "$@"; do args+=(--tests "$t"); done
  ./gradlew "$task" "${args[@]}" --rerun-tasks "${EXTRA_ARGS[@]}" || gradle_status=1
}
EXTRA_ARGS=("$@")
run :auth-client:jvmTest    '*SignInAgainstShildikTest*'
run :crash-client:jvmTest   '*KatcherIngestTest*'
run :shared-ui:desktopTest  '*TilesOverHttpTest*'
run :rider:desktopTest      '*SignInJoinsUpTest*'
run :server:test            '*ProtectedRidesTest*' '*RideEventsOverBooblikTest*' \
                            '*DocumentsAgainstBochkaTest*' '*ReceiptOverSmtpTest*' \
                            '*CityGraphMeasurement*'

# The assertion. `assumeTrue` is green, so the exit code above says nothing about whether a single
# one of these ran — the reports do, and only the reports this run wrote.
python3 - "$gradle_status" "$started_at" "${SHASHKI_KATCHER_URL:-}" <<'PY'
import glob, os, sys, xml.etree.ElementTree as ET

# **Only files this run wrote.** A report older than the run is a report about a different one, and
# reading it is how the first version of this script reported nine guards as skipped when the tasks
# holding them had not executed at all.
STARTED_AT = int(sys.argv[2]) - 2

# **A guard this stand cannot satisfy is not a failure, and must not be silence either.** katcher is
# not in `docker/compose.yaml`; without one, its guard is reported as uncovered and the run can still
# say it did what it could. Making it fatal would paint every ordinary run red, and a check that is
# always red is a check nobody reads - which is the whole subject of B-88.
UNCOVERED = {} if sys.argv[3] else {"KatcherIngestTest": "no katcher in this stand"}

WANTED = [
    "SignInAgainstShildikTest", "KatcherIngestTest", "TilesOverHttpTest", "SignInJoinsUpTest",
    "ProtectedRidesTest", "RideEventsOverBooblikTest", "DocumentsAgainstBochkaTest",
    "ReceiptOverSmtpTest", "CityGraphMeasurement",
]
seen, skipped, failed = {}, [], []
stale = []
for path in glob.glob("*/build/test-results/**/*.xml", recursive=True):
    try:
        suite = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    if os.path.getmtime(path) < STARTED_AT:
        name = (suite.get("name") or "").split("[")[0].split(".")[-1]
        if name in WANTED:
            stale.append(name)
        continue
    if suite.tag != "testsuite":
        continue
    name = (suite.get("name") or "").split("[")[0].split(".")[-1]
    if name not in WANTED:
        continue
    seen.setdefault(name, 0)
    for case in suite.iter("testcase"):
        seen[name] += 1
        for child in case:
            if child.tag == "skipped":
                skipped.append((name, case.get("name"), (child.get("message") or "").strip()[:120]))
            if child.tag in ("failure", "error"):
                failed.append((name, case.get("name")))

print()
print("%-28s %s" % ("guard", "ran"))
for name in WANTED:
    print("%-28s %s" % (name, seen.get(name, 0) or "-- no report --"))

if failed:
    print("\nfailed:")
    for n, t in failed:
        print("  %s.%s" % (n, t))
expected = [(n, t) for n, t, _ in skipped if n in UNCOVERED]
unexpected = [(n, t, m) for n, t, m in skipped if n not in UNCOVERED]
if expected:
    print("\nnot covered by this stand, and named rather than passed over:")
    for n, t in expected:
        print("  %s - %s" % (n, UNCOVERED[n]))
if unexpected:
    print("\nskipped though this stand could have answered for it:")
    for n, t, m in unexpected:
        print("  %s.%s\n      %s" % (n, t, m))

missing = [n for n in WANTED if not seen.get(n) and n not in UNCOVERED]
if missing:
    print("\nthis run wrote no report for: %s" % ", ".join(sorted(set(missing))))
    only_stale = sorted(set(stale) & set(missing))
    if only_stale:
        print("  (a report from an earlier run is on disk for %s - not read)" % ", ".join(only_stale))

ok = not failed and not unexpected and not missing and int(sys.argv[1]) == 0
if ok and expected:
    print("\nevery guard this stand can answer for ran and passed; %d named above did not"
          % len(expected))
elif ok:
    print("\nevery guard ran and passed")
else:
    print("\nthis run did NOT check everything - read the lists above")
sys.exit(0 if ok else 1)
PY
