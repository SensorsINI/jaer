#!/usr/bin/env python3
"""Write repo-root-style updates.xml from four GitHub Release installer files.

install4j media ids: macos 38, macos aarch64 39, unix 37, windows 26.
baseUrl is always GitHub Latest so the in-app checker follows the Latest flag.
"""
from __future__ import annotations

import argparse
import hashlib
import sys
from pathlib import Path

BASE_URL = "https://github.com/SensorsINI/jaer/releases/latest/download/"

# (filename glob suffix after version underscores, media id, bundled JRE name)
MEDIA = (
    ("jAER_macos_{v}.dmg", 38, "macos-amd64-25.0.4.tar.gz"),
    ("jAER_macos_aarch64_{v}.dmg", 39, "macos-aarch64-25.0.4.tar.gz"),
    ("jAER_unix_{v}.sh", 37, "linux-amd64-25.0.4.tar.gz"),
    ("jAER_windows-x64_{v}.exe", 26, "windows-amd64-25.0.4.tar.gz"),
)


def digest(path: Path) -> tuple[int, str, str]:
    md5 = hashlib.md5()
    sha = hashlib.sha256()
    n = 0
    with path.open("rb") as f:
        while True:
            chunk = f.read(1024 * 1024)
            if not chunk:
                break
            n += len(chunk)
            md5.update(chunk)
            sha.update(chunk)
    return n, md5.hexdigest(), sha.hexdigest()


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--dir", required=True, type=Path, help="Directory with the four installers")
    p.add_argument("--version", required=True, help="Public version, e.g. 3.5.2")
    p.add_argument("--out", required=True, type=Path)
    args = p.parse_args()
    v = args.version.replace(".", "_")
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        f'<updateDescriptor baseUrl="{BASE_URL}">',
    ]
    missing = []
    for tmpl, media_id, jre in MEDIA:
        name = tmpl.format(v=v)
        path = args.dir / name
        if not path.is_file():
            missing.append(name)
            continue
        size, md5, sha = digest(path)
        lines.append(
            f'  <entry targetMediaFileId="{media_id}" updatableVersionMin="2.0" '
            f'updatableVersionMax="" fileName="{name}" newVersion="{args.version}" '
            f'newMediaFileId="{media_id}" fileSize="{size}" md5Sum="{md5}" '
            f'sha256Sum="{sha}" bundledJre="{jre}" jreMinVersion="25" '
            f'jreMaxVersion="25" archive="false" singleBundle="false">'
        )
        lines.append('    <comment language="en" />')
        lines.append("  </entry>")
    lines.append("</updateDescriptor>")
    lines.append("")
    if missing:
        print("missing installers: " + ", ".join(missing), file=sys.stderr)
        return 1
    args.out.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
