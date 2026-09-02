#!/usr/bin/env python3
"""The style documents and the renderer agree, checked where checks always run.

Two facts, both of which drifted once already:

* the route's phases. `RouteLine` has one field per `phase` the documents filter on, and a document
  growing a third would leave the renderer drawing two — silently, because Kotlin cannot see a JSON
  file. B-24 was the same shape of drift in the other direction.
* the `cars` source is declared in both documents and no layer draws it. That is not a defect: the
  kit draws cars as markers over the map rather than as a style layer. It is pinned so that a layer
  appearing later is noticed by the module that would then have to draw it.

**Why here and not in a Kotlin test.** The obvious home is `shared-ui`'s test suite, and it was
written there first. A Gradle test that opens a file outside its declared inputs is a test the build
considers up to date when that file changes: the guard was correct, a deliberately broken document
passed, and only `--rerun-tasks` showed it failing. Declaring `map/` an input is the right fix and it
did not take. `make check` has no such notion, so the guard runs every time — which for a guard is
worth more than living beside the code it guards.
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCUMENTS = ["map/shashki-map-dark.json", "map/shashki-map-light.json"]
SCENE = "shared-ui/src/commonMain/kotlin/io/github/youndie/shashki/ui/map/MapScene.kt"


def phases_in(style):
    """The `phase` value each route layer filters on, in document order."""
    out = []
    for layer in style["layers"]:
        if layer.get("source") != "route":
            continue
        f = layer.get("filter")
        if not (isinstance(f, list) and len(f) == 3 and f[0] == "=="):
            raise SystemExit(f"route layer {layer['id']}: unexpected filter {f!r}")
        key = f[1]
        if not (isinstance(key, list) and key[:1] == ["get"]):
            raise SystemExit(f"route layer {layer['id']}: filtered on {key!r}, not on a property")
        if key[1] != "phase":
            raise SystemExit(f"route layer {layer['id']}: filtered on {key[1]!r}, not on 'phase'")
        out.append(f[2])
    return out


def declared_phases():
    """`RouteLine.PHASES`, read out of the Kotlin rather than repeated here."""
    text = (ROOT / SCENE).read_text()
    match = re.search(r"PHASES:\s*List<String>\s*=\s*listOf\(([^)]*)\)", text)
    if not match:
        raise SystemExit(f"{SCENE}: RouteLine.PHASES is gone or reshaped; this check reads it by pattern")
    return re.findall(r'"([^"]*)"', match.group(1))


def main():
    failures = []
    declared = declared_phases()
    for name in DOCUMENTS:
        style = json.loads((ROOT / name).read_text())
        found = phases_in(style)
        if found != declared:
            failures.append(f"{name}: filters the route on {found}; RouteLine.PHASES is {declared}")
        if "cars" not in style["sources"]:
            failures.append(f"{name}: lost its cars source")
        drawn = [l["id"] for l in style["layers"] if l.get("source") == "cars"]
        if drawn:
            failures.append(
                f"{name}: {drawn} now draw cars as style layers, but CanvasMapSurface draws them as markers"
            )

    for line in failures:
        print(f"  {line}", file=sys.stderr)
    if failures:
        raise SystemExit(f"style contract: {len(failures)} problem(s)")
    print(f"style contract: route phases {declared} in {len(DOCUMENTS)} documents, cars drawn as markers")


if __name__ == "__main__":
    main()
