#!/usr/bin/env python3
"""
Upload + Gallery test server for the local_upload_feature Android app.

Endpoints:
    GET  /               — health check
    GET  /api/v2/list    — returns picsum-style photo list (gallery)
    POST /upload         — multipart image upload
    POST /api/upload     — same as above (with /api prefix)

The Android emulator reaches your Mac's localhost via 10.0.2.2:8080.
ADB reverse tunnel: adb reverse tcp:8080 tcp:8080
  → emulator localhost:8080  →  host localhost:8080

NOTE: Uses only stdlib — no third-party packages required.
      Replaces the deprecated `cgi` module (removed in Python 3.13) with
      email.parser-based multipart parsing.
"""

import http.server
import json
import os
import datetime
import urllib.parse
from email import message_from_bytes
from email.policy import HTTP

UPLOAD_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "uploads")
os.makedirs(UPLOAD_DIR, exist_ok=True)

# ── Gallery photo data ────────────────────────────────────────────────────────
# 30 photos that match the picsum.photos /v2/list JSON schema.
# download_url uses picsum.photos CDN — if that is also unreachable through
# Zscaler the Coil image loader will show a broken-image icon (acceptable).
# The API LIST CALL itself goes through localhost:8080 (ADB tunnel) so the
# GalleryState will be Success even if individual images fail to load.

GALLERY_PHOTOS = [
    {"id": str(i), "author": f"Photographer {i}", "width": 5760, "height": 3840,
     "url": f"https://unsplash.com/photos/{i}",
     "download_url": f"https://picsum.photos/id/{i}/1920/1080"}
    for i in range(10, 40)   # IDs 10-39 — all known-good picsum photo IDs
]


def parse_multipart(content_type: str, body: bytes):
    """
    Parse a multipart/form-data body without using the deprecated `cgi` module.
    Returns a list of dicts: [{"filename": str, "data": bytes, "content_type": str}, ...]
    Only parts that have a filename (file uploads) are returned.
    """
    raw = f"Content-Type: {content_type}\r\n\r\n".encode() + body
    msg = message_from_bytes(raw, policy=HTTP)
    results = []
    if msg.is_multipart():
        for part in msg.walk():
            if part.get_content_maintype() == "multipart":
                continue
            filename = part.get_filename()
            if filename:
                results.append({
                    "filename": filename,
                    "data": part.get_payload(decode=True),
                    "content_type": part.get_content_type(),
                })
    return results


class UploadHandler(http.server.BaseHTTPRequestHandler):

    # ── GET ───────────────────────────────────────────────────────────────────

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path   = parsed.path.rstrip("/")
        params = urllib.parse.parse_qs(parsed.query)

        # ── Gallery list: GET /api/v2/list?page=1&limit=30 ────────────────────
        if path in ("/api/v2/list", "/v2/list"):
            try:
                page  = int(params.get("page",  ["1"])[0])
                limit = int(params.get("limit", ["30"])[0])
            except ValueError:
                page, limit = 1, 30

            start   = (page - 1) * limit
            end     = start + limit
            photos  = GALLERY_PHOTOS[start:end]

            body = json.dumps(photos).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            print(f"[GALLERY] Served {len(photos)} photos (page={page}, limit={limit})")
            return

        # ── Health check: GET / ───────────────────────────────────────────────
        body = json.dumps({
            "status":  "ok",
            "message": "Upload test server is running",
            "endpoints": [
                "GET  /api/v2/list?page=1&limit=30  — gallery photo list",
                "POST /api/upload                   — multipart image upload"
            ]
        }).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    # ── POST ──────────────────────────────────────────────────────────────────

    def do_POST(self):
        path = self.path.rstrip("/")
        if path not in ("/upload", "/api/upload"):
            self._json_response(404, {
                "success": False, "message": "Not found",
                "uploaded_count": 0, "fileUrl": None
            })
            return

        content_type = self.headers.get("Content-Type", "")
        if "multipart/form-data" not in content_type:
            self._json_response(400, {
                "success": False,
                "message": "Expected multipart/form-data",
                "uploaded_count": 0, "fileUrl": None
            })
            return

        try:
            length = int(self.headers.get("Content-Length", 0))
            body   = self.rfile.read(length)
            parts  = parse_multipart(content_type, body)

            if not parts:
                raise ValueError("No file parts found in multipart body.")

            saved = []
            for part in parts:
                timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S_%f")
                safe_name = f"{timestamp}_{os.path.basename(part['filename'])}"
                save_path = os.path.join(UPLOAD_DIR, safe_name)
                with open(save_path, "wb") as f:
                    f.write(part["data"])
                saved.append(safe_name)
                print(f"  Saved: {save_path}  ({len(part['data'])} bytes)")

            msg = f"Uploaded {len(saved)} file(s): {', '.join(saved)}"
            self._json_response(200, {
                "success": True,
                "message": msg,
                "uploaded_count": len(saved),
                "fileUrl": None
            })
            print(f"[OK] {msg}")

        except Exception as e:
            error_msg = str(e)
            print(f"[ERROR] {error_msg}")
            self._json_response(500, {
                "success": False,
                "message": error_msg,
                "uploaded_count": 0, "fileUrl": None
            })

    # ── Helper ────────────────────────────────────────────────────────────────

    def _json_response(self, status: int, payload: dict):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print(f"[{self.address_string()}] \"{fmt % args}\"")


if __name__ == "__main__":
    PORT   = 8080
    server = http.server.HTTPServer(("0.0.0.0", PORT), UploadHandler)
    print("=" * 60)
    print(f"  Local upload + gallery server  →  port {PORT}")
    print(f"  Emulator gallery  →  http://10.0.2.2:{PORT}/api/v2/list")
    print(f"  Emulator upload   →  http://10.0.2.2:{PORT}/api/upload")
    print(f"  ADB tunnel:  adb reverse tcp:{PORT} tcp:{PORT}")
    print(f"  Files saved to:  {UPLOAD_DIR}/")
    print("  Press Ctrl+C to stop.")
    print("=" * 60)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nServer stopped.")
