#!/usr/bin/env bash
# The bucket a driver's documents go into (B-47).
#
#   docker compose -f docker/compose.yaml up -d bochka
#   bash docker/bootstrap-documents.sh
#
# **It creates the bucket and deliberately does not publish it.** `upload-tiles.sh` next to this one
# ends with a public-read policy and a CORS rule, because a browser cannot sign a request for a tile;
# a licence is the opposite requirement, and the difference between the two is exactly this file's
# last line — which is not there. What reads a document is the server, authenticated, and
# `DocumentsAgainstBochkaTest` asserts that an anonymous `GET` is refused.
set -euo pipefail

ENDPOINT=${BOCHKA:-http://127.0.0.1:19000}
BUCKET=${DOCUMENTS_BUCKET:-documents}

python3 - "$ENDPOINT" "$BUCKET" <<'PY'
import sys
sys.path.insert(0, "map")
from tile_serving import request

endpoint, bucket = sys.argv[1], sys.argv[2]
status, body = request(endpoint, "PUT", f"/{bucket}")
# 409 is "it is already there", which is the ordinary second run.
if status not in (200, 409):
    raise SystemExit(f"creating {bucket}: {status} {body[:200]!r}")
print(f"bucket {bucket}: {status}")
print("no policy published: a document is read through the server, never by an anonymous GET")
PY
