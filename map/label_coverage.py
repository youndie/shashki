#!/usr/bin/env python3
"""How many roads in city.pmtiles the style documents would draw without a label.

Reads the archive and the style, and answers one question: for every feature in the label layer,
does the style's `text-field` expression have anything to put on it?

**The keys come out of the style document, not out of this file.** Hard-coding `name:latin` and
`name` here would make this a test of what the author remembered rather than of what the map says,
and the whole reason B-24 exists is that the two had drifted apart.

    python3 map/label_coverage.py /path/to/city.pmtiles map/shashki-map-dark.json
"""

import gzip
import json
import struct
import sys
from collections import Counter

# ---------------------------------------------------------------- protobuf

def varint(buf, i):
    result = shift = 0
    while True:
        byte = buf[i]
        i += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, i
        shift += 7


def fields(buf):
    """(field number, wire type, payload) for one message."""
    i = 0
    while i < len(buf):
        key, i = varint(buf, i)
        number, wire = key >> 3, key & 7
        if wire == 0:
            value, i = varint(buf, i)
            yield number, wire, value
        elif wire == 1:
            yield number, wire, buf[i:i + 8]
            i += 8
        elif wire == 2:
            length, i = varint(buf, i)
            yield number, wire, buf[i:i + length]
            i += length
        elif wire == 5:
            yield number, wire, buf[i:i + 4]
            i += 4
        else:
            raise ValueError(f"wire type {wire}")


def packed(buf):
    out, i = [], 0
    while i < len(buf):
        value, i = varint(buf, i)
        out.append(value)
    return out

# ---------------------------------------------------------------- pmtiles v3

def directory(buf):
    """(offset, length) per tile, from a v3 directory: deltas, runs, lengths, offsets."""
    count, i = varint(buf, 0)
    for _ in range(count):          # tile id deltas
        _, i = varint(buf, i)
    for _ in range(count):          # run lengths
        _, i = varint(buf, i)
    lengths = []
    for _ in range(count):
        value, i = varint(buf, i)
        lengths.append(value)
    offsets = []
    for n in range(count):
        value, i = varint(buf, i)
        # 0 is the archive's way of saying "immediately after the previous tile".
        offsets.append(offsets[n - 1] + lengths[n - 1] if value == 0 and n > 0 else value - 1)
    return list(zip(offsets, lengths))


def tiles(path):
    with open(path, "rb") as handle:
        blob = handle.read()
    assert blob[:7] == b"PMTiles" and blob[7] == 3, "not a pmtiles v3 archive"
    root_offset, root_length = struct.unpack_from("<QQ", blob, 8)
    leaf_offset, leaf_length = struct.unpack_from("<QQ", blob, 40)
    data_offset = struct.unpack_from("<Q", blob, 56)[0]
    assert leaf_length == 0, "this archive has leaf directories and this reader does not"
    for offset, length in directory(gzip.decompress(blob[root_offset:root_offset + root_length])):
        yield gzip.decompress(blob[data_offset + offset:data_offset + offset + length])

# ---------------------------------------------------------------- mvt

def layers(tile):
    for number, _, payload in fields(tile):
        if number == 3:
            yield layer(payload)


def layer(buf):
    name, keys, values, features = None, [], [], []
    for number, _, payload in fields(buf):
        if number == 1:
            name = payload.decode()
        elif number == 2:
            features.append(payload)
        elif number == 3:
            keys.append(payload.decode())
        elif number == 4:
            values.append(payload)
    return name, keys, values, features


def tags_of(feature, keys, values):
    out = {}
    for number, _, payload in fields(feature):
        if number == 2:
            flat = packed(payload)
            for k, v in zip(flat[0::2], flat[1::2]):
                out[keys[k]] = value_of(values[v])
    return out


def value_of(buf):
    for number, wire, payload in fields(buf):
        if number == 1:
            return payload.decode()
        if wire == 0:
            return payload
    return None

# ---------------------------------------------------------------- the style

def coalesced_keys(expression):
    """Every `["get", k]` reachable from a text-field expression, in order."""
    found = []
    def walk(node):
        if isinstance(node, list):
            if len(node) == 2 and node[0] == "get" and isinstance(node[1], str):
                found.append(node[1])
            else:
                for child in node:
                    walk(child)
    walk(expression)
    return found


def label_layers(style):
    """Layer name → the keys its text-field coalesces through, for every symbol layer."""
    out = {}
    for layer_spec in style["layers"]:
        field = layer_spec.get("layout", {}).get("text-field")
        if layer_spec.get("type") == "symbol" and field is not None:
            out.setdefault(layer_spec["source-layer"], []).extend(coalesced_keys(field))
    return out


def main(archive, style_path):
    style = json.loads(open(style_path).read())
    wanted = label_layers(style)
    print(f"{style_path}: {wanted}")

    totals = Counter()
    unlabelled_classes = Counter()
    # What the unlabelled ones *do* carry. Without this the report says how many roads have no
    # label and not whether anything could be put on them, which is the question a fix needs.
    unlabelled_keys = Counter()
    for tile in tiles(archive):
        for name, keys, values, features in layers(tile):
            if name not in wanted:
                continue
            for feature in features:
                tags = tags_of(feature, keys, values)
                totals[f"{name}:total"] += 1
                if not any(tags.get(key) for key in wanted[name]):
                    totals[f"{name}:unlabelled"] += 1
                    unlabelled_classes[f"{name}:{tags.get('class')}"] += 1
                    unlabelled_keys[",".join(sorted(k for k, v in tags.items() if v)) or "(nothing)"] += 1

    for key in sorted(totals):
        print(f"  {key}: {totals[key]}")
    for key in sorted(unlabelled_classes):
        print(f"  unlabelled by class — {key}: {unlabelled_classes[key]}")
    for key, n in unlabelled_keys.most_common(12):
        print(f"  unlabelled carrying — {key}: {n}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
