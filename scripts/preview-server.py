#!/usr/bin/env python3
"""Runs the TV pages on your computer, without a TV, for working on prompter.html and edit.html.

    python3 scripts/preview-server.py [--port 8080] [--notes notes.md]

Then open http://localhost:8080/ (the TV screen, use the arrow keys, Enter and 0) and
http://localhost:8080/edit (the editor). It answers the same requests as NotesServer.java,
and keeps the notes in memory only.
"""
import argparse
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets")
state = {"notes": b"", "version": 0}


class Handler(BaseHTTPRequestHandler):
    def send(self, code, ctype, body, extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path in ("/", "/edit"):
            name = "prompter.html" if path == "/" else "edit.html"
            with open(os.path.join(ASSETS, name), "rb") as f:
                self.send(200, "text/html; charset=utf-8", f.read())
        elif path == "/notes":
            self.send(200, "text/plain; charset=utf-8", state["notes"], {"X-Version": str(state["version"])})
        elif path == "/version":
            self.send(200, "text/plain", str(state["version"]).encode())
        elif path == "/address":
            self.send(200, "text/plain", ("http://%s" % self.headers.get("Host", "localhost")).encode())
        else:
            self.send(404, "text/plain", b"Not found")

    def do_POST(self):
        if self.path.split("?", 1)[0] != "/notes":
            return self.send(404, "text/plain", b"Not found")
        length = int(self.headers.get("Content-Length") or 0)
        state["notes"] = self.rfile.read(length)
        state["version"] = int(time.time() * 1000)
        self.send(200, "text/plain", b"ok")

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--port", type=int, default=8080)
    p.add_argument("--notes", help="a text or Markdown file to start with")
    a = p.parse_args()
    if a.notes:
        with open(a.notes, "rb") as f:
            state["notes"] = f.read()
        state["version"] = int(time.time() * 1000)
    print("TV screen: http://localhost:%d/   editor: http://localhost:%d/edit" % (a.port, a.port))
    ThreadingHTTPServer(("127.0.0.1", a.port), Handler).serve_forever()
