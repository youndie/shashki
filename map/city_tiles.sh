#!/usr/bin/env bash
#
# The demo's map data, from one OSM extract: a pmtiles archive, a routing graph and the glyph
# stacks the two style documents ask for. B-06.
#
# Runs on the Linux build box, not on the mac — it wants a few GB of heap, docker for the glyph
# step and a fast link to Geofabrik's neighbours. Nothing it writes belongs in git: the output
# directory is `build/city`, and B-07 is what carries the archive to bochka.
#
#   bash map/city_tiles.sh                                  # everything
#   OUT=/tmp/city bash map/city_tiles.sh tiles              # one step: tiles | graph | glyphs
#
set -euo pipefail

OUT=${OUT:-build/city}
STEP=${1:-all}

# The extract. Geofabrik's Slovenia file is the obvious source and it is unusable from this
# network: the .md5 beside it downloads, the .pbf itself accepts the TLS handshake and then never
# answers (measured 2026-09-01, three attempts, both here and from the mac). BBBike publishes a
# ready-made Ljubljana extract instead, with its bounding box stated in Ljubljana.poly —
# 14.27–14.77 E, 45.90–46.25 N — which holds the city *and* Brnik airport at 46.23, the one pair
# of endpoints every fixture names. It is regenerated weekly, so the md5 is verified against the
# publisher's own CHECKSUM.txt rather than pinned here; a changed file is a fact to record in
# B-06, not a build failure.
EXTRACT_URL=https://download.bbbike.org/osm/bbbike/Ljubljana/Ljubljana.osm.pbf
CHECKSUM_URL=https://download.bbbike.org/osm/bbbike/Ljubljana/CHECKSUM.txt
PLANETILER_VERSION=v0.10.2
GRAPHHOPPER_VERSION=11.0
SOURCE_SANS_VERSION=3.052R

STYLES_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

mkdir -p "$OUT"

log() { printf '\n== %s\n' "$*"; }

fetch() { # url path
  [ -s "$2" ] || curl -sSL -o "$2" "$1"
}

extract() {
  fetch "$EXTRACT_URL" "$OUT/Ljubljana.osm.pbf"
  fetch "$CHECKSUM_URL" "$OUT/CHECKSUM.txt"
  local want have
  want=$(awk '$2 == "Ljubljana.osm.pbf" { print $1 }' "$OUT/CHECKSUM.txt")
  have=$(md5sum "$OUT/Ljubljana.osm.pbf" | cut -d' ' -f1)
  [ "$want" = "$have" ] || { echo "extract md5 $have != published $want" >&2; exit 1; }
  echo "extract: $(stat -c%s "$OUT/Ljubljana.osm.pbf") bytes, md5 $have"
}

# The layer list is read out of the style documents rather than typed here. Everything the OpenMapTiles
# schema carries beyond them is weight the browser pays for on every ranged read and nothing draws:
# `poi` alone was 188k of the biggest tile's 241k before this filter existed.
style_layers() {
  python3 - "$STYLES_DIR"/shashki-map-*.json <<'PY'
import json, sys
layers = set()
for path in sys.argv[1:]:
    style = json.load(open(path))
    vector = {k for k, v in style["sources"].items() if v.get("type") == "vector"}
    for layer in style["layers"]:
        if layer.get("source") in vector and layer.get("source-layer"):
            layers.add(layer["source-layer"])
print(",".join(sorted(layers)))
PY
}

tiles() {
  extract
  fetch "https://github.com/onthegomap/planetiler/releases/download/$PLANETILER_VERSION/planetiler.jar" "$OUT/planetiler.jar"
  fetch "https://github.com/onthegomap/planetiler/releases/download/$PLANETILER_VERSION/planetiler.jar.sha256" "$OUT/planetiler.jar.sha256"
  (cd "$OUT" && sha256sum -c planetiler.jar.sha256)

  local layers
  layers=$(style_layers)
  log "planetiler, layers from the styles: $layers"
  # nodemap-type: sparsearray, not array. `array` indexes by OSM node id, so on a city extract whose
  # ids reach into the billions it asks for tens of GB and dies with 8 GB of heap.
  # download_dir: under $OUT, not planetiler's own default of `./data/sources`. That default is
  # relative to wherever this script is invoked from — the repository root on the build box — and
  # nothing here ignores it from sync. A one-way-replica mutagen session mirrors alpha exactly and
  # deletes what beta has that alpha does not; `build/` is in that session's ignore list, a bare
  # `data/` at the root is not, and the two downloads landed in the gap between "finished
  # downloading" and planetiler's own existence check, ten seconds later, on the machine this was
  # first run on. `--download_dir` is planetiler's own flag for exactly this.
  java -Xmx8g -jar "$OUT/planetiler.jar" \
    --osm-path="$OUT/Ljubljana.osm.pbf" \
    --output="$OUT/city.pmtiles" \
    --download_dir="$OUT/sources" \
    --tmpdir="$OUT/tmp" \
    --only-layers="$layers" \
    --nodemap-type=sparsearray \
    --download --force
  echo "city.pmtiles: $(stat -c%s "$OUT/city.pmtiles") bytes"
}

graph() {
  extract
  fetch "https://github.com/graphhopper/graphhopper/releases/download/$GRAPHHOPPER_VERSION/graphhopper-web-$GRAPHHOPPER_VERSION.jar" "$OUT/graphhopper-web.jar"
  fetch "https://raw.githubusercontent.com/graphhopper/graphhopper/$GRAPHHOPPER_VERSION/config-example.yml" "$OUT/graphhopper.yml"
  # **`graph-cache-import`, and the suffix is the whole point.** The server resolves its own graph
  # directory as the *sibling* of the extract called `graph-cache` (`RoutingConfig.kt`), and the
  # graph this step builds is imported by GraphHopper's own jar with its own `config-example.yml` —
  # a different profile hash. Written under the name the server looks for, it makes anything later
  # pointed at this extract die with `Profiles do not match`, which is the landmine B-35 recorded
  # for images and is the same one on disk. `CityGraphMeasurement` already sidesteps it with a
  # suffix of its own; this makes the sidestep unnecessary.
  rm -rf "$OUT/graph-cache-import"
  log "graphhopper import (this is the number B-23 inherits as its startup cost)"
  /usr/bin/time -f 'import: %e s wall, %M kb max rss' java -Xmx4g \
    -Ddw.graphhopper.datareader.file="$OUT/Ljubljana.osm.pbf" \
    -Ddw.graphhopper.graph.location="$OUT/graph-cache-import" \
    -jar "$OUT/graphhopper-web.jar" import "$OUT/graphhopper.yml"
  echo "graph-cache-import: $(du -sb "$OUT/graph-cache-import" | cut -f1) bytes"
}

# The styles ask for "Source Sans 3 Light" and "Source Sans 3 SemiLight". Source Sans 3 has no
# SemiLight — the name comes from the Metro ramp the kit is drawn in, where Selawik does have one,
# and kvadrant numbers those two steps W200 and W300 (KvadrantWeights.Light / .SemiLight). So the
# stacks keep the names the styles use and are cut from the faces at those weights: ExtraLight for
# Light, Light for SemiLight. The map's labels then sit at the same weights as the UI's text.
glyphs() {
  fetch "https://github.com/adobe-fonts/source-sans/releases/download/$SOURCE_SANS_VERSION/TTF-source-sans-$SOURCE_SANS_VERSION.zip" "$OUT/source-sans.zip"
  (cd "$OUT" && rm -rf fonts && mkdir fonts && cd fonts && unzip -q ../source-sans.zip)

  cat > "$OUT/gen-glyphs.js" <<'JS'
const fs = require("fs"), path = require("path");
const fontnik = require("fontnik");
const [, , fontPath, outDir] = process.argv;
const buf = fs.readFileSync(fontPath);
fs.mkdirSync(outDir, { recursive: true });
let start = 0;
(function next() {
  if (start > 65535) { console.log("done", outDir); return; }
  const s = start, e = start + 255;
  start += 256;
  fontnik.range({ font: buf, start: s, end: e }, (err, res) => {
    if (err) throw err;
    fs.writeFileSync(path.join(outDir, `${s}-${e}.pbf`), res);
    next();
  });
})();
JS

  log "glyph pbfs (fontnik in docker — the box has no node)"
  docker run --rm -v "$(cd "$OUT" && pwd)":/w -w /w node:20 bash -c \
    'npm i --silent fontnik@0.7.1 >/dev/null 2>&1 \
     && node gen-glyphs.js fonts/TTF/SourceSans3-ExtraLight.ttf "glyphs/Source Sans 3 Light" \
     && node gen-glyphs.js fonts/TTF/SourceSans3-Light.ttf "glyphs/Source Sans 3 SemiLight"'
  echo "glyphs: $(find "$OUT/glyphs" -name '*.pbf' | wc -l) ranges, $(du -sb "$OUT/glyphs" | cut -f1) bytes"
}

case "$STEP" in
  tiles) tiles ;;
  graph) graph ;;
  glyphs) glyphs ;;
  all) tiles; graph; glyphs ;;
  *) echo "usage: $0 [all|tiles|graph|glyphs]" >&2; exit 2 ;;
esac
