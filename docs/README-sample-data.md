# Sample recordings — packaging and installer

User-facing file list: [`sampleData/README.md`](../sampleData/README.md). This page is for packing, GitHub, and the installer.

## What ships where

Recordings are **not** in git and **not** in the basic installer. Git tracks `sampleData/README.md` only. Recordings stay in a local / Dropbox `sampleData/` folder (gitignored).

| Piece | Role |
|-------|------|
| GitHub Latest asset `jaer-sample-data.zip` | The zip users download |
| `sampleData/README.md` | What the files are (also inside the zip and the install tree) |
| `sampleData/SIZE.txt` | Zip and unpacked MiB; written by pack, not in git |
| Installer Welcome checkbox | Optional download; default **off** |
| **Help → Sample data → Download jAER sample data** | Download if empty; otherwise open the folder and README |

Download URL:

<https://github.com/SensorsINI/jaer/releases/latest/download/jaer-sample-data.zip>

README in the browser (Help menu opens this while a download runs):

<https://github.com/SensorsINI/jaer/blob/master/sampleData/README.md>

Unpack so files land **in** `sampleData/` (zip root is the files, not a nested `sampleData/` directory). That folder is next to `dist/` / `lib/` in a git checkout and under `jaer/sampleData` in an installed copy.

## Pack

1. Drop recordings into `sampleData/` (gitignored).
2. `ant pack-sample-data` (or `ant release` when recordings are present).

Writes `currentInstallers/<VERSION>/jaer-sample-data.zip` (store / no deflate; AEDAT-4 is already compressed) and `sampleData/SIZE.txt`. Skips the zip if a name+size stamp still matches. Force: `scripts/pack-sample-data.ps1 -Force` or `bash scripts/pack-sample-data.sh --force`.

The pack scripts also refresh the generated size table between `<!-- SAMPLE-DATA-CONTENTS -->` markers in `sampleData/README.md`. Keep file descriptions **outside** that block.

Upload the zip with the release (`scripts/upload-github-release-installers.ps1` / `.sh`) so `/latest/download/jaer-sample-data.zip` is valid.

Installer checkbox sizes come from `SIZE.txt` at `install4jc` time (`-Djaer.sampleDataZipMiB` / `jaer.sampleDataUnpackedMiB`). `SIZE.txt` and `README.md` are install4j `fileEntry`s under `jaer/sampleData`.

In-app File → Open may still offer a download if the folder has no recordings (prefs `AEViewer.sampleDataDownloadDeclined`).

See [`README-releasing-tagging.md`](README-releasing-tagging.md).
