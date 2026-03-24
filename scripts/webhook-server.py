#!/usr/bin/env python3
"""
GitHub Webhook Listener for Auto-Deploy
========================================
Listens for push events on main branch and triggers deployment.

Usage on VPS:
    python3 webhook-server.py --secret YOUR_WEBHOOK_SECRET --port 9000

Setup:
    1. Run this script on VPS as a systemd service
    2. Add GitHub webhook: https://YOUR_VPS_IP:9000/webhook
    3. Set content type: application/json
    4. Set secret: same as --secret
    5. Select event: Just the push event
"""

import argparse
import hashlib
import hmac
import json
import subprocess
import sys
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler


class WebhookHandler(BaseHTTPRequestHandler):
    secret = ""
    deploy_script = "/opt/waselak/repo/scripts/vps-deploy.sh"
    branch = "main"

    def do_POST(self):
        if self.path != "/webhook":
            self.send_response(404)
            self.end_headers()
            return

        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length)

        # Verify signature
        if self.secret:
            signature = self.headers.get("X-Hub-Signature-256", "")
            expected = "sha256=" + hmac.new(
                self.secret.encode(), body, hashlib.sha256
            ).hexdigest()
            if not hmac.compare_digest(signature, expected):
                print(f"[REJECTED] Invalid signature")
                self.send_response(403)
                self.end_headers()
                self.wfile.write(b'{"error": "Invalid signature"}')
                return

        # Parse payload
        try:
            payload = json.loads(body)
        except json.JSONDecodeError:
            self.send_response(400)
            self.end_headers()
            return

        # Check if it's a push to main
        ref = payload.get("ref", "")
        if ref != f"refs/heads/{self.branch}":
            print(f"[SKIP] Push to {ref}, not {self.branch}")
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b'{"status": "skipped", "reason": "not main branch"}')
            return

        # Check if backend files changed
        commits = payload.get("commits", [])
        backend_changed = False
        changed_files = []
        for commit in commits:
            for f in commit.get("added", []) + commit.get("modified", []) + commit.get("removed", []):
                changed_files.append(f)
                if f.startswith("backend/") or f.startswith("scripts/"):
                    backend_changed = True

        if not backend_changed:
            print(f"[SKIP] No backend changes in {len(changed_files)} files")
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b'{"status": "skipped", "reason": "no backend changes"}')
            return

        # Trigger deploy in background
        pusher = payload.get("pusher", {}).get("name", "unknown")
        head_commit = payload.get("head_commit", {}).get("message", "")[:80]
        print(f"\n{'='*50}")
        print(f"[DEPLOY] Triggered by {pusher}")
        print(f"[DEPLOY] Commit: {head_commit}")
        print(f"[DEPLOY] Changed: {len(changed_files)} files")
        print(f"{'='*50}")

        # Run deploy script in background thread
        def run_deploy():
            try:
                result = subprocess.run(
                    ["bash", self.deploy_script],
                    capture_output=True,
                    text=True,
                    timeout=300,  # 5 min timeout
                )
                if result.returncode == 0:
                    print(f"[DEPLOY] ✅ Success!")
                else:
                    print(f"[DEPLOY] ❌ Failed!")
                    print(result.stderr[-500:] if result.stderr else "No error output")
            except subprocess.TimeoutExpired:
                print(f"[DEPLOY] ❌ Timeout (5 min)")
            except Exception as e:
                print(f"[DEPLOY] ❌ Error: {e}")

        thread = threading.Thread(target=run_deploy, daemon=True)
        thread.start()

        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'{"status": "deploying"}')

    def do_GET(self):
        """Health check endpoint"""
        if self.path == "/health":
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b'{"status": "ok", "service": "waselak-webhook"}')
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        """Custom log format"""
        print(f"[WEBHOOK] {args[0]}")


def main():
    parser = argparse.ArgumentParser(description="GitHub Webhook Listener")
    parser.add_argument("--port", type=int, default=9000, help="Port to listen on")
    parser.add_argument("--secret", type=str, default="", help="Webhook secret")
    parser.add_argument("--branch", type=str, default="main", help="Branch to deploy")
    parser.add_argument("--deploy-script", type=str,
                        default="/opt/waselak/repo/scripts/vps-deploy.sh",
                        help="Path to deploy script")
    args = parser.parse_args()

    WebhookHandler.secret = args.secret
    WebhookHandler.branch = args.branch
    WebhookHandler.deploy_script = args.deploy_script

    server = HTTPServer(("0.0.0.0", args.port), WebhookHandler)
    print(f"🚀 Webhook server listening on port {args.port}")
    print(f"   Branch: {args.branch}")
    print(f"   Deploy script: {args.deploy_script}")
    print(f"   Secret: {'configured' if args.secret else 'NONE (not recommended)'}")
    print(f"   URL: http://YOUR_VPS_IP:{args.port}/webhook")
    print()

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.server_close()


if __name__ == "__main__":
    main()
