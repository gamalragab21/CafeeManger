#!/usr/bin/env python3
"""
Upload build artifacts to Google Drive using OAuth 2.0.

Usage:
    python3 scripts/gdrive-upload.py \
        --credentials keystore/gdrive-oauth-client.json \
        --parent-folder GOOGLE_DRIVE_FOLDER_ID \
        --version 1.1.0 \
        --build-type debug \
        --artifacts-dir build/deploy/debug

First run opens a browser for Google login and saves a refresh token.
Subsequent runs use the saved token automatically.
"""

import argparse
import glob
import http.server
import json
import os
import socket
import sys
import threading
import urllib.parse
import urllib.request
import webbrowser
from urllib.request import Request

SCOPES = "https://www.googleapis.com/auth/drive.file"
TOKEN_FILE = os.path.join(os.path.dirname(__file__), "..", "keystore", "gdrive-oauth-token.json")


def _find_free_port():
    """Find a free port on localhost."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("localhost", 0))
        return s.getsockname()[1]


def _exchange_code(client_id, client_secret, code, redirect_uri):
    """Exchange authorization code for access + refresh tokens."""
    data = urllib.parse.urlencode({
        "code": code,
        "client_id": client_id,
        "client_secret": client_secret,
        "redirect_uri": redirect_uri,
        "grant_type": "authorization_code",
    }).encode()
    req = Request("https://oauth2.googleapis.com/token", data=data)
    resp = urllib.request.urlopen(req)
    return json.loads(resp.read())


def _refresh_access_token(client_id, client_secret, refresh_token):
    """Use refresh token to get a new access token."""
    data = urllib.parse.urlencode({
        "client_id": client_id,
        "client_secret": client_secret,
        "refresh_token": refresh_token,
        "grant_type": "refresh_token",
    }).encode()
    req = Request("https://oauth2.googleapis.com/token", data=data)
    resp = urllib.request.urlopen(req)
    return json.loads(resp.read())["access_token"]


def _authorize_interactive(client_id, client_secret):
    """Open browser for user to authorize, capture the code via local redirect."""
    port = _find_free_port()
    redirect_uri = f"http://localhost:{port}"

    auth_url = (
        "https://accounts.google.com/o/oauth2/v2/auth?"
        + urllib.parse.urlencode({
            "client_id": client_id,
            "redirect_uri": redirect_uri,
            "response_type": "code",
            "scope": SCOPES,
            "access_type": "offline",
            "prompt": "consent",
        })
    )

    auth_code = None

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            nonlocal auth_code
            qs = urllib.parse.urlparse(self.path).query
            params = urllib.parse.parse_qs(qs)
            auth_code = params.get("code", [None])[0]
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.end_headers()
            self.wfile.write(b"<html><body><h2>Authorization successful!</h2>"
                             b"<p>You can close this tab and return to the terminal.</p>"
                             b"</body></html>")

        def log_message(self, format, *args):
            pass  # Suppress request logs

    server = http.server.HTTPServer(("localhost", port), Handler)
    server.timeout = 120

    print(f"\n  Opening browser for Google authorization...")
    print(f"  (If it doesn't open, copy this URL into your browser)")
    print(f"  {auth_url}\n")
    webbrowser.open(auth_url)

    # Wait for the redirect callback
    while auth_code is None:
        server.handle_request()

    server.server_close()

    if not auth_code:
        print("ERROR: Authorization failed — no code received.")
        sys.exit(1)

    # Exchange code for tokens
    token_data = _exchange_code(client_id, client_secret, auth_code, redirect_uri)
    # Save refresh token
    os.makedirs(os.path.dirname(TOKEN_FILE), exist_ok=True)
    with open(TOKEN_FILE, "w") as f:
        json.dump({"refresh_token": token_data["refresh_token"]}, f)
    print("  Authorization successful! Refresh token saved.\n")
    return token_data["access_token"]


def get_access_token(credentials_path):
    """Get an access token using OAuth 2.0 Desktop flow."""
    with open(credentials_path) as f:
        creds = json.load(f)

    # Handle both formats: {"installed": {...}} and {"web": {...}}
    client_info = creds.get("installed") or creds.get("web") or creds
    client_id = client_info["client_id"]
    client_secret = client_info["client_secret"]

    token_file = os.path.normpath(TOKEN_FILE)

    # Try to use saved refresh token
    if os.path.isfile(token_file):
        with open(token_file) as f:
            saved = json.load(f)
        refresh_token = saved.get("refresh_token")
        if refresh_token:
            try:
                access_token = _refresh_access_token(client_id, client_secret, refresh_token)
                return access_token
            except Exception as e:
                print(f"  Refresh token expired or invalid ({e}), re-authorizing...")

    # No saved token or refresh failed — do interactive auth
    return _authorize_interactive(client_id, client_secret)


def find_folder(token, name, parent_id):
    """Search for an existing folder by name under a parent. Returns folder ID or None."""
    query = (
        f"name='{name}' and '{parent_id}' in parents "
        f"and mimeType='application/vnd.google-apps.folder' and trashed=false"
    )
    url = (
        "https://www.googleapis.com/drive/v3/files?"
        + urllib.parse.urlencode({
            "q": query,
            "fields": "files(id,name)",
            "pageSize": "1",
        })
    )
    req = Request(url, headers={"Authorization": f"Bearer {token}"})
    resp = urllib.request.urlopen(req)
    result = json.loads(resp.read())
    files = result.get("files", [])
    return files[0]["id"] if files else None


def create_folder(token, name, parent_id):
    """Create a folder on Google Drive. Returns the folder ID."""
    metadata = json.dumps({
        "name": name,
        "mimeType": "application/vnd.google-apps.folder",
        "parents": [parent_id],
    }).encode()
    req = Request(
        "https://www.googleapis.com/drive/v3/files",
        data=metadata,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
    )
    resp = urllib.request.urlopen(req)
    folder_id = json.loads(resp.read())["id"]
    print(f"  Created folder: {name} (ID: {folder_id})")
    return folder_id


def find_or_create_folder(token, name, parent_id):
    """Find an existing folder or create a new one. Returns the folder ID."""
    folder_id = find_folder(token, name, parent_id)
    if folder_id:
        print(f"  Found existing folder: {name} (ID: {folder_id})")
        return folder_id
    return create_folder(token, name, parent_id)


def upload_file(token, filepath, folder_id):
    """Upload a file to Google Drive using resumable upload (supports large files)."""
    filename = os.path.basename(filepath)
    file_size = os.path.getsize(filepath)
    size_mb = file_size / (1024 * 1024)
    print(f"  Uploading: {filename} ({size_mb:.1f} MB)...", end="", flush=True)

    metadata = json.dumps({"name": filename, "parents": [folder_id]}).encode()

    # Step 1: Initiate resumable upload session
    req = Request(
        "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable",
        data=metadata,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json; charset=UTF-8",
            "X-Upload-Content-Length": str(file_size),
            "X-Upload-Content-Type": "application/octet-stream",
        },
    )
    try:
        resp = urllib.request.urlopen(req)
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8", errors="replace")
        print(f"\n  ERROR {e.code}: {error_body}")
        raise
    upload_url = resp.headers["Location"]

    # Step 2: Upload the file content in chunks
    CHUNK_SIZE = 10 * 1024 * 1024  # 10 MB chunks
    with open(filepath, "rb") as f:
        offset = 0
        while offset < file_size:
            chunk = f.read(CHUNK_SIZE)
            chunk_end = offset + len(chunk) - 1
            headers = {
                "Content-Length": str(len(chunk)),
                "Content-Range": f"bytes {offset}-{chunk_end}/{file_size}",
            }
            upload_req = Request(upload_url, data=chunk, headers=headers, method="PUT")
            try:
                upload_resp = urllib.request.urlopen(upload_req)
                result = json.loads(upload_resp.read())
                print(f" done -> ID: {result['id']}")
            except urllib.error.HTTPError as e:
                if e.code == 308:
                    # Resume incomplete — chunk accepted, continue
                    pass
                else:
                    raise
            offset += len(chunk)


def main():
    parser = argparse.ArgumentParser(description="Upload build artifacts to Google Drive")
    parser.add_argument("--credentials", required=True, help="Path to OAuth client JSON")
    parser.add_argument("--parent-folder", required=True, help="Google Drive parent folder ID")
    parser.add_argument("--version", required=True, help="App version (e.g. 1.1.0)")
    parser.add_argument("--build-type", required=True, choices=["debug", "release"], help="Build type")
    parser.add_argument("--artifacts-dir", required=True, help="Directory containing artifacts to upload")
    args = parser.parse_args()

    if not os.path.isfile(args.credentials):
        print(f"ERROR: Credentials file not found: {args.credentials}")
        sys.exit(1)

    artifacts = glob.glob(os.path.join(args.artifacts_dir, "*"))
    if not artifacts:
        print(f"WARNING: No artifacts found in {args.artifacts_dir}")
        sys.exit(0)

    print(f"\n{'=' * 60}")
    print(f"Google Drive Upload: v{args.version}/{args.build_type}")
    print(f"{'=' * 60}")
    print(f"Artifacts: {len(artifacts)} files")

    # Authenticate
    print("\nAuthenticating with Google Drive...")
    token = get_access_token(args.credentials)
    print("  Authenticated successfully")

    # Create folder structure: v{VERSION}/{build_type}/
    print(f"\nCreating folder structure...")
    version_folder = find_or_create_folder(token, f"v{args.version}", args.parent_folder)
    type_folder = find_or_create_folder(token, args.build_type, version_folder)

    # Upload all artifacts
    print(f"\nUploading {len(artifacts)} artifacts...")
    for filepath in sorted(artifacts):
        upload_file(token, filepath, type_folder)

    print(f"\n{'=' * 60}")
    print(f"Done! {len(artifacts)} files uploaded to v{args.version}/{args.build_type}/")
    print(f"{'=' * 60}\n")


if __name__ == "__main__":
    main()
