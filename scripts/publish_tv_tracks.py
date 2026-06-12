#!/usr/bin/env python3
"""Publish the TV-flavor bundle to Google Play's Android TV form-factor tracks.

Play manages Android TV releases on separate, TV-only tracks (``tv:internal``,
``tv:TV`` — the closed-testing track, etc.) that only accept artifacts REQUIRING
``android.software.leanback``. Gradle Play Publisher cannot target those tracks, so
this script uploads ``app/build/outputs/bundle/tvRelease/app-tv-release.aab`` and
points the TV tracks at it.

Usage:
    ./gradlew :app:bundleTvRelease
    python3 scripts/publish_tv_tracks.py [--tracks tv:internal,tv:TV]

Credentials: ``play-service-account.json`` at the repo root (same file Gradle Play
Publisher uses). The TV versionCode is read from the bundle's manifest, so bump the
``tv`` flavor's ``versionCode`` in app/build.gradle.kts before each TV release.

No review-bypass flags are ever sent: if Play wants a manual review, the commit
fails loudly and the release has to be finished in the Play Console.
"""

import argparse
import base64
import json
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

PACKAGE = "com.dx.ambient"
ROOT = Path(__file__).resolve().parent.parent
BUNDLE = ROOT / "app/build/outputs/bundle/tvRelease/app-tv-release.aab"
CREDENTIALS = ROOT / "play-service-account.json"
RELEASE_NOTES = ROOT / "app/src/main/play/release-notes/en-GB/default.txt"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD_API = (
    "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/"
    f"applications/{PACKAGE}"
)


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def access_token() -> str:
    sa = json.loads(CREDENTIALS.read_text())
    header = b64url(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    now = int(time.time())
    claims = b64url(
        json.dumps(
            {
                "iss": sa["client_email"],
                "scope": "https://www.googleapis.com/auth/androidpublisher",
                "aud": "https://oauth2.googleapis.com/token",
                "iat": now,
                "exp": now + 3600,
            }
        ).encode()
    )
    signing_input = f"{header}.{claims}".encode()
    with tempfile.NamedTemporaryFile("w", suffix=".pem") as key_file:
        key_file.write(sa["private_key"])
        key_file.flush()
        signature = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", key_file.name],
            input=signing_input,
            capture_output=True,
            check=True,
        ).stdout
    assertion = f"{header}.{claims}.{b64url(signature)}"
    response = json.loads(
        urllib.request.urlopen(
            urllib.request.Request(
                "https://oauth2.googleapis.com/token",
                data=urllib.parse.urlencode(
                    {
                        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                        "assertion": assertion,
                    }
                ).encode(),
            )
        ).read()
    )
    return response["access_token"]


def request(token: str, method: str, url: str, body=None, content_type="application/json"):
    data = None
    if body is not None:
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={"Authorization": f"Bearer {token}", "Content-Type": content_type},
    )
    try:
        return json.loads(urllib.request.urlopen(req).read() or b"{}")
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")
        raise SystemExit(f"{method} {url} failed ({error.code}):\n{detail}") from error


def bundle_version_name() -> str:
    """The release label: the versionName declared in app/build.gradle.kts."""
    gradle = (ROOT / "app/build.gradle.kts").read_text()
    match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    return match.group(1) if match else BUNDLE.stem


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--tracks",
        default="tv:internal,tv:TV",
        help="Comma-separated TV form-factor tracks to update (default: %(default)s)",
    )
    args = parser.parse_args()

    if not BUNDLE.exists():
        raise SystemExit(f"TV bundle not found: {BUNDLE}\nRun ./gradlew :app:bundleTvRelease first.")
    if not CREDENTIALS.exists():
        raise SystemExit(f"Missing {CREDENTIALS}")

    token = access_token()

    edit = request(token, "POST", f"{API}/edits", {})
    edit_id = edit["id"]
    print(f"Edit {edit_id} opened")

    uploaded = request(
        token,
        "POST",
        f"{UPLOAD_API}/edits/{edit_id}/bundles?uploadType=media",
        BUNDLE.read_bytes(),
        content_type="application/octet-stream",
    )
    version_code = uploaded["versionCode"]
    print(f"Uploaded {BUNDLE.name} as versionCode {version_code}")

    release = {
        "name": bundle_version_name(),
        "versionCodes": [str(version_code)],
        "status": "completed",
    }
    if RELEASE_NOTES.exists():
        release["releaseNotes"] = [
            {"language": "en-GB", "text": RELEASE_NOTES.read_text().strip()}
        ]

    for track in args.tracks.split(","):
        track = track.strip()
        request(
            token,
            "PUT",
            f"{API}/edits/{edit_id}/tracks/{urllib.parse.quote(track)}",
            {"track": track, "releases": [release]},
        )
        print(f"Staged versionCode {version_code} on '{track}'")

    request(token, "POST", f"{API}/edits/{edit_id}:commit")
    print("Committed — TV tracks updated.")


if __name__ == "__main__":
    sys.exit(main())
