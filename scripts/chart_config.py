#!/usr/bin/env python3
"""
The chart's environment against the one the server actually reads.

    python3 scripts/chart_config.py            # report
    python3 scripts/chart_config.py --check    # the same, non-zero on a discrepancy

WHY THIS IS A SCRIPT. B-36's criterion is "every variable the server reads has a value in the chart,
and every value in the chart is read by the server" — which is a claim somebody checks once and then
nobody checks again. A variable added to `ReceiptConfig` next month is invisible in the chart, and the
symptom is a deployment where receipts are silently not sent; a variable *removed* from the code
leaves a line in the chart that documents something that no longer exists, which is worse than a
missing line because it reads as current.

WHAT IT READS. The names out of `server/src/main` — anything quoted that looks like a variable this
project owns — and the `- name:` entries under the deployment's `env:`. Deliberately textual: the
alternative is rendering the chart with Helm and parsing YAML, which needs Helm on the machine
running the documentation gate.

THE EXEMPTIONS ARE PER VARIABLE AND EACH HAS A REASON. A blanket "ignore what is not in the chart"
would hide exactly the gap this exists for, so anything left out has to be named below and say why.
"""
import argparse
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = os.path.join(ROOT, "server", "src", "main")
DEPLOYMENT = os.path.join(ROOT, "charts", "shashki", "templates", "deployment.yaml")

# A quoted name that looks like this project's own environment variable.
IN_CODE = re.compile(r'"((?:SHASHKI|DB)_[A-Z0-9_]+)"')
# `- name: FOO` under the container's `env:`. The chart sets nothing else in that shape.
IN_CHART = re.compile(r"^\s*-\s*name:\s*((?:SHASHKI|DB)_[A-Z0-9_]+)\s*$", re.M)

# Read by the server and deliberately absent from the chart.
PROVIDED_BY_THE_IMAGE = {
    "SHASHKI_GRAPH_DIR": "the image sets it; the graph is a layer, not a value somebody chooses",
    "SHASHKI_BUNDLES": "the image sets it; the bundles are a layer",
    "SHASHKI_OSM_FILE": "a build-time input to `:server:prepareGraph`. At run time the graph is "
                        "already prepared, and pointing a deployment at an extract it does not "
                        "carry would import 41 MB it has not got",
    "SHASHKI_DRIVER_ID": "the driver bundle's own default, read from the page rather than from this "
                         "service's environment — one browser is one driver, and a deployment does "
                         "not know which",
}


def names_in_code():
    found = {}
    for base, _, files in os.walk(CODE):
        for name in files:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(base, name)
            with open(path, encoding="utf-8") as fh:
                for variable in IN_CODE.findall(fh.read()):
                    found.setdefault(variable, os.path.relpath(path, ROOT))
    return found


def names_in_chart():
    if not os.path.isfile(DEPLOYMENT):
        sys.exit(f"no deployment template at {os.path.relpath(DEPLOYMENT, ROOT)}")
    with open(DEPLOYMENT, encoding="utf-8") as fh:
        return set(IN_CHART.findall(fh.read()))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    code = names_in_code()
    chart = names_in_chart()

    # The guard on the guard: both sides must have found something. A regex that stopped matching
    # would otherwise report a perfect agreement between two empty sets.
    if len(code) < 10 or len(chart) < 5:
        sys.exit(f"read {len(code)} variables from the code and {len(chart)} from the chart — "
                 f"one of the patterns has stopped matching, and an empty set agrees with anything")

    missing = {v: where for v, where in code.items() if v not in chart and v not in PROVIDED_BY_THE_IMAGE}
    unread = chart - set(code)
    stale = {v: why for v, why in PROVIDED_BY_THE_IMAGE.items() if v not in code}

    print(f"variables: {len(code)} in the code, {len(chart)} in the chart, "
          f"{len(PROVIDED_BY_THE_IMAGE)} provided by the image")
    for variable, where in sorted(missing.items()):
        print(f"  read by {where} and not set by the chart: {variable}")
    for variable in sorted(unread):
        print(f"  set by the chart and read by nothing: {variable}")
    for variable, why in sorted(stale.items()):
        print(f"  exempted and no longer read at all: {variable} — {why}")

    problems = len(missing) + len(unread) + len(stale)
    if not problems:
        print("the chart and the server agree about the environment")
    if args.check and problems:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
