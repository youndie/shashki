---
id: B-06
title: "Choose the demo city and produce the OSM extract and the pmtiles archive"
status: open
priority: P1
size: M
stage: stage-0-unknowns
---

# B-06 — Choose the demo city and produce the OSM extract and the pmtiles archive

Open question 3 in the research. The city sits upstream of more than it looks: it fixes the size of
the pmtiles archive the browser has to range-read, the GraphHopper import time, and the street names
in every fixture — which means it is upstream of the goldens too.

- **Compact graph wins.** The demo's value is that matching and routing answer quickly on a laptop,
  not that the map is famous.
- The rejected alternative is deferring it until the clients exist. B-01's spike needs a real
  `city.pmtiles` to have rendered anything at all, and a spike against a blank style proves nothing.
- Also produces the glyph PBFs: both style documents point at a `glyphs` endpoint that does not
  exist and say in their own metadata that the PBFs must be generated from Source Sans 3, with
  `["Noto Sans Regular"]` as the interim.

- AC: one `city.pmtiles`, its size recorded, and both style documents rendering against it.
- AC: a GraphHopper import of the same extract, with the import time recorded.
- AC: the fixture data in the handoff re-checked against real street names from the extract.
