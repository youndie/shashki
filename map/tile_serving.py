#!/usr/bin/env python3
"""Put city.pmtiles into an S3 store and read it back the way a browser does.

B-07's measurement. Browser tile traffic is many small ranged reads into one large object, which is
not the load an object store's published throughput numbers describe — so this makes that load and
counts it.

    python3 map/tile_serving.py upload  http://127.0.0.1:9000 tiles /path/to/city.pmtiles
    python3 map/tile_serving.py measure http://127.0.0.1:9000 tiles city.pmtiles --max-zoom 16

**SigV4 by hand, on the standard library.** The alternative is boto3 on the measuring machine, which
is a dependency for the sake of four HMACs and would put a retrying, pooling client between the
measurement and the thing being measured. The reads are `http.client` for the same reason: one
connection, one request, one timing, nothing helpful in the way.
"""

import argparse
import datetime
import gzip
import hashlib
import hmac
import http.client
import statistics
import struct
import sys
import time
import urllib.parse

ALGORITHM = "AWS4-HMAC-SHA256"
SERVICE = "s3"
REGION = "us-east-1"
KEY_ID = "shashki"
SECRET = "shashkisecret"


# ---------------------------------------------------------------- signing

def _sign(key, message):
    return hmac.new(key, message.encode(), hashlib.sha256).digest()


def authorization(method, host, path, payload, now, query=""):
    stamp = now.strftime("%Y%m%dT%H%M%SZ")
    day = now.strftime("%Y%m%d")
    payload_hash = hashlib.sha256(payload).hexdigest()
    headers = {"host": host, "x-amz-content-sha256": payload_hash, "x-amz-date": stamp}
    signed = ";".join(sorted(headers))
    canonical = "\n".join(
        [
            method,
            urllib.parse.quote(path),
            query,
            "".join(f"{k}:{headers[k]}\n" for k in sorted(headers)),
            signed,
            payload_hash,
        ]
    )
    scope = f"{day}/{REGION}/{SERVICE}/aws4_request"
    to_sign = "\n".join([ALGORITHM, stamp, scope, hashlib.sha256(canonical.encode()).hexdigest()])
    key = _sign(_sign(_sign(_sign(f"AWS4{SECRET}".encode(), day), REGION), SERVICE), "aws4_request")
    signature = hmac.new(key, to_sign.encode(), hashlib.sha256).hexdigest()
    headers["Authorization"] = (
        f"{ALGORITHM} Credential={KEY_ID}/{scope}, SignedHeaders={signed}, Signature={signature}"
    )
    return headers


def request(endpoint, method, path, payload=b"", extra=None, query=""):
    url = urllib.parse.urlsplit(endpoint)
    host = url.netloc
    headers = authorization(method, host, path, payload, datetime.datetime.now(datetime.UTC), query)
    headers.update(extra or {})
    target = urllib.parse.quote(path) + (f"?{query}" if query else "")
    connection = http.client.HTTPConnection(url.hostname, url.port or 80, timeout=60)
    connection.request(method, target, body=payload, headers=headers)
    response = connection.getresponse()
    body = response.read()
    connection.close()
    return response.status, body


# ---------------------------------------------------------------- pmtiles over ranges

def varint(buf, i):
    result = shift = 0
    while True:
        byte = buf[i]
        i += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, i
        shift += 7


def directory_entries(buf):
    """(tile id, offset, length) per entry of a v3 directory."""
    count, i = varint(buf, 0)
    ids, previous = [], 0
    for _ in range(count):
        delta, i = varint(buf, i)
        previous += delta
        ids.append(previous)
    for _ in range(count):
        _, i = varint(buf, i)
    lengths = []
    for _ in range(count):
        value, i = varint(buf, i)
        lengths.append(value)
    offsets = []
    for n in range(count):
        value, i = varint(buf, i)
        offsets.append(offsets[n - 1] + lengths[n - 1] if value == 0 and n > 0 else value - 1)
    return list(zip(ids, offsets, lengths))


def zoom_of(tile_id):
    """The zoom a Hilbert tile id belongs to: the level whose 4^z block contains it."""
    accumulated, zoom = 0, 0
    while True:
        size = 4**zoom
        if tile_id < accumulated + size:
            return zoom
        accumulated += size
        zoom += 1


class Reader:
    """One connection, kept open, and every read timed."""

    def __init__(self, endpoint, path):
        url = urllib.parse.urlsplit(endpoint)
        self.endpoint, self.path, self.host = endpoint, path, url.netloc
        self.connection = http.client.HTTPConnection(url.hostname, url.port or 80, timeout=60)
        self.timings, self.bytes = [], 0

    def ranged(self, offset, length):
        headers = authorization("GET", self.host, self.path, b"", datetime.datetime.now(datetime.UTC))
        headers["Range"] = f"bytes={offset}-{offset + length - 1}"
        started = time.perf_counter()
        self.connection.request("GET", urllib.parse.quote(self.path), headers=headers)
        response = self.connection.getresponse()
        body = response.read()
        elapsed = (time.perf_counter() - started) * 1000
        if response.status != 206:
            raise SystemExit(f"expected 206 for bytes={offset}-{offset + length - 1}, got {response.status}")
        self.timings.append(elapsed)
        self.bytes += len(body)
        return body

    def close(self):
        self.connection.close()


def measure(endpoint, bucket, key, max_zoom):
    reader = Reader(endpoint, f"/{bucket}/{key}")

    header = reader.ranged(0, 127)
    if header[:7] != b"PMTiles" or header[7] != 3:
        raise SystemExit("not a pmtiles v3 archive")
    root_offset, root_length = struct.unpack_from("<QQ", header, 8)
    leaf_offset, leaf_length = struct.unpack_from("<QQ", header, 40)
    data_offset = struct.unpack_from("<Q", header, 56)[0]
    if leaf_length:
        raise SystemExit("this archive has leaf directories and this reader does not")

    entries = directory_entries(gzip.decompress(reader.ranged(root_offset, root_length)))
    wanted = [e for e in entries if zoom_of(e[0]) <= max_zoom]

    for _, offset, length in wanted:
        reader.ranged(data_offset + offset, length)
    reader.close()

    timings = sorted(reader.timings)
    def percentile(p):
        return timings[min(len(timings) - 1, int(len(timings) * p))]

    print(f"archive          {bucket}/{key}")
    print(f"tiles in archive {len(entries)}, fetched at zoom <= {max_zoom}: {len(wanted)}")
    print(f"requests         {len(timings)} (1 header, 1 directory, {len(wanted)} tiles)")
    print(f"bytes read       {reader.bytes}")
    print(f"latency ms       min {timings[0]:.2f}  p50 {percentile(0.50):.2f}  "
          f"p90 {percentile(0.90):.2f}  p99 {percentile(0.99):.2f}  max {timings[-1]:.2f}")
    print(f"total wall       {sum(timings):.0f} ms of request time")


def upload(endpoint, bucket, path):
    status, body = request(endpoint, "PUT", f"/{bucket}")
    if status not in (200, 409):
        raise SystemExit(f"creating {bucket}: {status} {body[:200]!r}")
    payload = open(path, "rb").read()
    key = path.rsplit("/", 1)[-1]
    status, body = request(endpoint, "PUT", f"/{bucket}/{key}", payload)
    if status != 200:
        raise SystemExit(f"uploading {key}: {status} {body[:200]!r}")
    print(f"uploaded {key}: {len(payload)} bytes")


def publish(endpoint, bucket):
    """A public-read bucket policy, which is what a browser fetching tiles needs.

    The browser cannot sign: it has no secret and must not be given one. So either the object is
    readable without a signature or the tiles are served from somewhere else. This is the call that
    answers which, and B-07 exists because the answer was not known.
    """
    policy = (
        '{"Version":"2012-10-17","Statement":[{"Sid":"tiles","Effect":"Allow","Principal":"*",'
        '"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::' + bucket + '/*"]}]}'
    ).encode()
    status, body = request(endpoint, "PUT", f"/{bucket}", policy, query="policy=")
    print(f"put bucket policy: {status} {body[:200]!r}")


def many(endpoint, bucket, directory):
    """The second load shape: many small objects, each a whole GET.

    The glyph PBFs are 512-codepoint ranges of two faces — 516 files of a couple of kilobytes. A
    store tuned for large sequential objects and one tuned for a directory of small ones are not the
    same store, and B-07 asks about both because the map needs both.
    """
    import os

    files = sorted(
        os.path.join(root, name)
        for root, _, names in os.walk(directory)
        for name in names
        if name.endswith(".pbf")
    )
    if not files:
        raise SystemExit(f"no .pbf under {directory}")
    for path in files:
        key = "glyphs/" + os.path.relpath(path, directory)
        status, body = request(endpoint, "PUT", f"/{bucket}/{key}", open(path, "rb").read())
        if status != 200:
            raise SystemExit(f"uploading {key}: {status} {body[:200]!r}")

    url = urllib.parse.urlsplit(endpoint)
    connection = http.client.HTTPConnection(url.hostname, url.port or 80, timeout=60)
    timings, total = [], 0
    for path in files:
        key = "glyphs/" + os.path.relpath(path, directory)
        target = urllib.parse.quote(f"/{bucket}/{key}")
        started = time.perf_counter()
        connection.request("GET", target, headers={"Host": url.netloc})
        response = connection.getresponse()
        body = response.read()
        timings.append((time.perf_counter() - started) * 1000)
        if response.status != 200:
            raise SystemExit(f"reading {key}: {response.status}")
        total += len(body)
    connection.close()

    timings.sort()
    def percentile(p):
        return timings[min(len(timings) - 1, int(len(timings) * p))]

    print(f"objects          {len(files)} glyph ranges, {total} bytes")
    print(f"latency ms       min {timings[0]:.2f}  p50 {percentile(0.50):.2f}  "
          f"p90 {percentile(0.90):.2f}  p99 {percentile(0.99):.2f}  max {timings[-1]:.2f}")
    print(f"total wall       {sum(timings):.0f} ms of request time")


def cors(endpoint, bucket):
    """The CORS rule a browser needs to range-read the archive.

    `Range` is not on the CORS safelist, so the browser sends a preflight before every tile read and
    refuses the response unless `Content-Range` and `Accept-Ranges` are exposed. Without this the
    fetch fails in the browser while `curl` succeeds, which is the most confusing shape a failure can
    take.
    """
    document = (
        '<?xml version="1.0" encoding="UTF-8"?>'
        '<CORSConfiguration><CORSRule>'
        "<AllowedOrigin>*</AllowedOrigin>"
        "<AllowedMethod>GET</AllowedMethod>"
        "<AllowedMethod>HEAD</AllowedMethod>"
        "<AllowedHeader>*</AllowedHeader>"
        "<ExposeHeader>Content-Range</ExposeHeader>"
        "<ExposeHeader>Accept-Ranges</ExposeHeader>"
        "<ExposeHeader>Content-Length</ExposeHeader>"
        "<MaxAgeSeconds>86400</MaxAgeSeconds>"
        "</CORSRule></CORSConfiguration>"
    ).encode()
    status, body = request(endpoint, "PUT", f"/{bucket}", document, query="cors=")
    print(f"put bucket cors: {status} {body[:200]!r}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["upload", "measure", "publish", "cors", "many"])
    parser.add_argument("endpoint")
    parser.add_argument("bucket")
    parser.add_argument("target", nargs="?", default="")
    parser.add_argument("--max-zoom", type=int, default=16)
    args = parser.parse_args()
    if args.command == "upload":
        upload(args.endpoint, args.bucket, args.target)
    elif args.command == "publish":
        publish(args.endpoint, args.bucket)
    elif args.command == "cors":
        cors(args.endpoint, args.bucket)
    elif args.command == "many":
        many(args.endpoint, args.bucket, args.target)
    else:
        measure(args.endpoint, args.bucket, args.target, args.max_zoom)


if __name__ == "__main__":
    main()
