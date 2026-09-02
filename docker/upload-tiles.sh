#!/usr/bin/env bash
# Put the city archive into the local bochka and make a browser able to read it.
#
#   docker compose -f docker/compose.yaml up -d bochka
#   bash docker/upload-tiles.sh ~/shashki-city/city.pmtiles
#
# **Three steps and none of them is optional.** Uploading is the obvious one; the other two are what
# B-07 found the hard way. A public-read bucket policy, because a browser cannot sign a request and
# an unsigned GET against an anonymous-mode store is still 403. And a CORS configuration, because
# `Range` is not on the safelist — every tile read is preflighted, and without the rule the preflight
# is refused while `curl` keeps working perfectly.
set -euo pipefail

ARCHIVE=${1:-}
ENDPOINT=${BOCHKA:-http://127.0.0.1:19000}
BUCKET=${BUCKET:-tiles}

if [ -z "$ARCHIVE" ] || [ ! -f "$ARCHIVE" ]; then
  echo "usage: $0 <path to city.pmtiles>" >&2
  echo "build one with map/city_tiles.sh" >&2
  exit 2
fi

python3 map/tile_serving.py upload "$ENDPOINT" "$BUCKET" "$ARCHIVE"
python3 map/tile_serving.py publish "$ENDPOINT" "$BUCKET"
python3 map/tile_serving.py cors "$ENDPOINT" "$BUCKET"

echo
echo "the archive is at $ENDPOINT/$BUCKET/$(basename "$ARCHIVE")"
echo "point a client at it:"
echo "  SHASHKI_TILES=$ENDPOINT/$BUCKET/$(basename "$ARCHIVE") ./gradlew :rider:run"
