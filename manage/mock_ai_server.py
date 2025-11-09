#!/usr/bin/env python3
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse


class MockAiHandler(BaseHTTPRequestHandler):
    server_version = "MockAI/0.1"

    def _set_headers(self, status=200, body=b""):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        return body

    def do_POST(self):
        parsed = urlparse(self.path)
        if parsed.path not in ("/", "/v1/chat/completions"):
            body = self._set_headers(404, b"{}")
            self.wfile.write(body)
            return

        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        raw = self.rfile.read(length).decode("utf-8") if length > 0 else "{}"
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            payload = {}

        messages = payload.get("messages") or []
        last = messages[-1]["content"] if messages else ""
        reply = {
            "choices": [
                {
                    "message": {
                        "content": f"（AI自动发言）我听到了：{last[:200]}。我会谨慎发言，隐藏身份。"
                    }
                }
            ]
        }
        data = json.dumps(reply).encode("utf-8")
        body = self._set_headers(200, data)
        self.wfile.write(body)

    def log_message(self, format, *args):
        # Suppress default stdout logging to keep terminal clean
        return


def run(host="127.0.0.1", port=8787):
    server = HTTPServer((host, port), MockAiHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    run()
