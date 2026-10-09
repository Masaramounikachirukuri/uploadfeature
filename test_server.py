#!/usr/bin/env python3
"""
Simple multipart upload test server for the local_upload_feature Android app.

Usage:
    python3 test_server.py

The server listens on port 8080 and accepts POST /upload with multipart image files.
Uploaded files are saved to ./uploads/ directory.

Response JSON:
    {"success": true, "message": "...", "uploaded_count": N, "fileUrl": null}

The Android emulator reaches your Mac's localhost via 10.0.2.2:8080.

NOTE: Uses only stdlib — no third-party packages required.
      Replaces the deprecated `cgi` module (removed in Python 3.13) with
      email.parser-based multipart parsing.
"""

import http.server
import json
import os
import datetime
import io
from email import message_from_bytes
from email.policy import HTTP

UPLOAD_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "uploads")
os.makedirs(UPLOAD_DIR, exist_ok=True)


def parse_multipart(content_type: str, body: bytes):
    """
    Parse a multipart/form-data body without using the deprecated `cgi` module.

    Returns a list of dicts:
        [{"filename": str, "data": bytes, "content_type": str}, ...]
    Only parts that have a filename (i.e. file uploads) are returned.
    """
    # Build a minimal RFC-2822 message so email.parser can handle the multipart body.
    # We fake a top-level Content-Type header so the parser sees the boundary.
    raw = f"Content-Type: {content_type}\r\n\r\n".encode() + body
    msg = message_from_bytes(raw, policy=HTTP)

    results = []
    if msg.is_multipart():
        for part in msg.walk():
            # skip the container itself
            if part.get_content_maintype() == "multipart":
                continue
            disposition = part.get("Content-Disposition", "")
            filename = part.get_filename()
            if filename:
                results.append({
                    "filename": filename,
                    "data": part.get_payload(decode=True),
                    "content_type": part.get_content_type(),
                })
    return results


class UploadHandler(http.server.BaseHTTPRequestHandler):

    def do_GET(self):
        """Health-check endpoint — useful for verifying the server is reachable."""
        body = json.dumps({"status": "ok", "message": "Upload test server is running"}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path not in ("/upload", "/upload/", "/api/upload", "/api/upload/"):
            self._json_response(404, {"success": False, "message": "Not found", "uploaded_count": 0, "fileUrl": None})
            return

        content_type = self.headers.get("Content-Type", "")
        if "multipart/form-data" not in content_type:
            self._json_response(400, {
                "success": False,
                "message": "Expected multipart/form-data",
                "uploaded_count": 0,
                "fileUrl": None
            })
            return

        try:
            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length)

            parts = parse_multipart(content_type, body)

            if not parts:
                raise ValueError("No file parts found in multipart body. "
                                 "Make sure the field name is 'files'.")

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
                "uploaded_count": 0,
                "fileUrl": None
            })

    def _json_response(self, status: int, payload: dict):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print(f"[{self.address_string()}] {fmt % args}")


if __name__ == "__main__":
    PORT = 8080
    server = http.server.HTTPServer(("0.0.0.0", PORT), UploadHandler)
    print("=" * 50)
    print(f"  Upload test server running on port {PORT}")
    print(f"  Emulator  → http://10.0.2.2:{PORT}/upload")
    print(f"  Host      → http://localhost:{PORT}/upload")
    print(f"  Files saved to: {UPLOAD_DIR}/")
    print("  Press Ctrl+C to stop.")
    print("=" * 50)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nServer stopped.")
