#!/usr/bin/env python3
"""Cut a small pmtiles v3 archive out of a large one, so a test can have a real archive.

    python3 map/pmtiles_subset.py city.pmtiles out.pmtiles --tile 14/8850/5815 --tile 14/8852/5825

**Why a subset and not the city.** The Kotlin reader has to be tested against an archive somebody
else wrote — a fixture this repository generated from its own understanding of the format would pass
for exactly as long as that understanding is wrong. The city archive is 17 MB and does not belong in
git; a 2x2 block of it is the same bytes, written by the same `pmtiles` build, at a size a test
resource can be.

The header layout is v3: magic and version in the first 8 bytes, then five (offset, length) pairs,
then the counts and the four compression/type bytes. Everything this writes is copied from the
source header except the offsets, the counts and the zoom range.
"""

import argparse
import struct
import sys

HEADER = 127


def varint(buf, i):
    result = shift = 0
    while True:
        byte = buf[i]
        i += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, i
        shift += 7


def put_varint(out, value):
    while True:
        byte = value & 0x7F
        value >>= 7
        out.append(byte | (0x80 if value else 0))
        if not value:
            return


def directory_entries(buf):
    """(tile id, run length, offset, length) per entry of a v3 directory."""
    count, i = varint(buf, 0)
    ids, previous = [], 0
    for _ in range(count):
        delta, i = varint(buf, i)
        previous += delta
        ids.append(previous)
    runs = []
    for _ in range(count):
        value, i = varint(buf, i)
        runs.append(value)
    lengths = []
    for _ in range(count):
        value, i = varint(buf, i)
        lengths.append(value)
    offsets = []
    for n in range(count):
        value, i = varint(buf, i)
        offsets.append(offsets[n - 1] + lengths[n - 1] if value == 0 and n > 0 else value - 1)
    return list(zip(ids, runs, offsets, lengths))


def serialise_directory(entries):
    """The inverse. `entries` must be sorted by tile id."""
    out = bytearray()
    put_varint(out, len(entries))
    previous = 0
    for tile_id, _, _, _ in entries:
        put_varint(out, tile_id - previous)
        previous = tile_id
    for _, run, _, _ in entries:
        put_varint(out, run)
    for _, _, _, length in entries:
        put_varint(out, length)
    for n, (_, _, offset, length) in enumerate(entries):
        if n > 0 and offset == entries[n - 1][2] + entries[n - 1][3]:
            put_varint(out, 0)
        else:
            put_varint(out, offset + 1)
    return bytes(out)


def hilbert(zoom, x, y):
    """The pmtiles tile id: the zoom's base plus the Hilbert index within it."""
    base = sum(4**z for z in range(zoom))
    n = 1 << zoom
    d = 0
    s = n // 2
    while s > 0:
        rx = 1 if x & s else 0
        ry = 1 if y & s else 0
        d += s * s * ((3 * rx) ^ ry)
        if ry == 0:
            if rx == 1:
                x = s - 1 - x
                y = s - 1 - y
            x, y = y, x
        s //= 2
    return base + d


def subset(source, target, wanted):
    blob = open(source, "rb").read()
    if blob[:7] != b"PMTiles" or blob[7] != 3:
        raise SystemExit("not a pmtiles v3 archive")
    root_offset, root_length = struct.unpack_from("<QQ", blob, 8)
    leaf_length = struct.unpack_from("<Q", blob, 48)[0]
    data_offset = struct.unpack_from("<Q", blob, 56)[0]
    if leaf_length:
        raise SystemExit("this archive has leaf directories and this cutter does not")

    import gzip

    entries = directory_entries(gzip.decompress(blob[root_offset : root_offset + root_length]))
    by_id = {tile_id: (run, offset, length) for tile_id, run, offset, length in entries}

    kept, data = [], bytearray()
    for tile_id in sorted(wanted):
        if tile_id not in by_id:
            raise SystemExit(f"tile id {tile_id} is not in {source}")
        _, offset, length = by_id[tile_id]
        kept.append((tile_id, 1, len(data), length))
        data += blob[data_offset + offset : data_offset + offset + length]

    directory = gzip.compress(serialise_directory(kept), mtime=0)
    metadata = gzip.compress(b"{}", mtime=0)

    header = bytearray(blob[:HEADER])
    root_at = HEADER
    metadata_at = root_at + len(directory)
    data_at = metadata_at + len(metadata)
    struct.pack_into("<QQ", header, 8, root_at, len(directory))
    struct.pack_into("<QQ", header, 24, metadata_at, len(metadata))
    struct.pack_into("<QQ", header, 40, data_at + len(data), 0)
    struct.pack_into("<QQ", header, 56, data_at, len(data))
    struct.pack_into("<QQQ", header, 72, len(kept), len(kept), len(kept))

    with open(target, "wb") as out:
        out.write(header)
        out.write(directory)
        out.write(metadata)
        out.write(data)
    print(f"{target}: {len(kept)} tiles, {HEADER + len(directory) + len(metadata) + len(data)} bytes")
    for tile_id, _, offset, length in kept:
        print(f"  id {tile_id}  offset {offset}  length {length}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("target")
    parser.add_argument(
        "--tile",
        action="append",
        required=True,
        metavar="Z/X/Y",
        help="a tile to keep; repeat it. The set need not be contiguous — a fixture usually wants "
        "one known tile for an oracle and a block of neighbours for a seam.",
    )
    args = parser.parse_args()
    wanted = []
    for spec in args.tile:
        z, x, y = (int(part) for part in spec.split("/"))
        wanted.append(hilbert(z, x, y))
    subset(args.source, args.target, wanted)
    return 0


if __name__ == "__main__":
    sys.exit(main())
