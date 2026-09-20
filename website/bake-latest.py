#!/usr/bin/env python3
"""Write website/latest.json from GitHub Latest plus a newer prerelease, if any."""

from __future__ import annotations

import json
import os
import re
import ssl
import sys
import urllib.request

LATEST_API = "https://api.github.com/repos/SensorsINI/jaer/releases/latest"
LIST_API = "https://api.github.com/repos/SensorsINI/jaer/releases?per_page=30"
OUT_NAME = "latest.json"
SKIP_TAGS = {"sample-data-current"}

WINDOWS = re.compile(r"^jAER_windows-x64_.*\.exe$")
MAC_ARM = re.compile(r"^jAER_macos_aarch64_.*\.dmg$")
MAC_INTEL = re.compile(r"^jAER_macos_[0-9].*\.dmg$")
LINUX = re.compile(r"^jAER_unix_.*\.sh$")


def github_get(url):
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "SensorsINI-jaer-pages",
    }
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, headers=headers)
    context = ssl.create_default_context()
    with urllib.request.urlopen(req, context=context, timeout=60) as resp:
        return json.load(resp)


def pick_assets(release):
    picked = {}
    for asset in release.get("assets") or []:
        name = asset.get("name") or ""
        info = {
            "name": name,
            "url": asset.get("browser_download_url"),
            "size": asset.get("size"),
        }
        if WINDOWS.fullmatch(name):
            picked["windows"] = info
        elif MAC_ARM.fullmatch(name):
            picked["macos_aarch64"] = info
        elif MAC_INTEL.fullmatch(name):
            picked["macos_intel"] = info
        elif LINUX.fullmatch(name):
            picked["linux"] = info
    return picked


def payload_from_release(release):
    payload = {
        "tag_name": release.get("tag_name"),
        "html_url": release.get("html_url"),
        "published_at": release.get("published_at"),
    }
    payload.update(pick_assets(release))
    return payload


def pick_newer_prerelease(releases, stable):
    stable_tag = (stable or {}).get("tag_name") or ""
    stable_published = (stable or {}).get("published_at") or ""
    for rel in releases or []:
        if rel.get("draft"):
            continue
        tag = rel.get("tag_name") or ""
        if tag in SKIP_TAGS or tag == stable_tag:
            continue
        if not rel.get("prerelease"):
            continue
        published = rel.get("published_at") or ""
        if stable_published and published and published <= stable_published:
            continue
        if not pick_assets(rel):
            continue
        return payload_from_release(rel)
    return None


def write_payload(out_path, payload):
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, indent=2)
        fh.write("\n")


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out_path = os.path.join(here, OUT_NAME)
    try:
        latest = github_get(LATEST_API)
        listing = github_get(LIST_API)
    except Exception as exc:
        print("warning: GitHub release fetch failed: " + str(exc), file=sys.stderr)
        write_payload(out_path, {"error": str(exc)})
        return 0

    payload = payload_from_release(latest)
    pre = pick_newer_prerelease(listing, payload)
    if pre:
        payload["prerelease"] = pre
    write_payload(out_path, payload)
    extra = " prerelease=" + str(pre.get("tag_name")) if pre else " prerelease=none"
    print("wrote " + out_path + " tag=" + str(payload.get("tag_name")) + extra)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
