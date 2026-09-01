"""The static server the B-06 preview needs and python's own does not provide: byte ranges, because
a pmtiles archive is read by range and nothing else, plus a POST that lets the page save its own
canvas next to the artefacts. Serves the directory it lives in.

    python3 map/preview/serve.py 8731
"""

import os, re, sys
from http.server import HTTPServer, SimpleHTTPRequestHandler

class RangeHandler(SimpleHTTPRequestHandler):
    """SimpleHTTPRequestHandler with byte-range support — pmtiles is nothing but ranged reads."""

    def send_head(self):
        rng = self.headers.get("Range")
        if not rng:
            return super().send_head()
        m = re.match(r"bytes=(\d+)-(\d*)", rng)
        if not m:
            return super().send_head()
        path = self.translate_path(self.path)
        if not os.path.isfile(path):
            return super().send_head()
        size = os.path.getsize(path)
        start = int(m.group(1))
        end = int(m.group(2)) if m.group(2) else size - 1
        end = min(end, size - 1)
        f = open(path, "rb")
        f.seek(start)
        self._range = (start, end)
        self.send_response(206)
        self.send_header("Content-Type", self.guess_type(path))
        self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.send_header("Content-Length", str(end - start + 1))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        return f

    def copyfile(self, source, outputfile):
        rng = getattr(self, "_range", None)
        if rng is None:
            return super().copyfile(source, outputfile)
        start, end = rng
        remaining = end - start + 1
        while remaining > 0:
            chunk = source.read(min(65536, remaining))
            if not chunk:
                break
            outputfile.write(chunk)
            remaining -= len(chunk)

    def end_headers(self):
        if self.command == "HEAD" or not hasattr(self, "_range"):
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Access-Control-Allow-Origin", "*")
        super().end_headers()

    def do_POST(self):
        """The page posts its own canvas back: a WebGL surface the pane will not capture."""
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        name = self.path.split("name=")[-1] or "shot.png"
        name = os.path.basename(name)
        with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), name), "wb") as f:
            f.write(body)
        self.send_response(200)
        self.send_header("Content-Length", "2")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(b"ok")

    def log_message(self, *a):
        pass

os.chdir(os.path.dirname(os.path.abspath(__file__)))
HTTPServer(("127.0.0.1", int(sys.argv[1])), RangeHandler).serve_forever()
