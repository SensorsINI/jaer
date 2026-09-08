# Sample recordings — packaging and installer

User-facing file list: [`sampleData/README.md`](../sampleData/README.md). This page is for packing, GitHub, and the installer.

## What ships where

Recordings are **not** in git and **not** in the basic installer. Git tracks `sampleData/README.md` and looping WebP thumbs in `sampleData/previews/`. Recordings stay in a local / Dropbox `sampleData/` folder (gitignored).

| Piece | Role |
|-------|------|
| GitHub Latest asset `jaer-sample-data.zip` | The zip users download |
| `sampleData/README.md` | What the files are (also inside the zip and the install tree) |
| `sampleData/previews/*.webp` | 5 s / 120 px loops for the GitHub README table. Not in the zip. |
| `sampleData/SIZE.txt` | Zip and unpacked MiB; written by pack, not in git |
| Installer Welcome checkbox | Optional download; default **off**. Shows zip/unpacked size and a time estimate at **10 MB/s Wi-Fi**. After files are copied, a **Sample recordings** screen can **Skip** (finish Setup) or **Download**. Cancel during the download skips only that step — jAER is already installed (Installation screen is a rollback barrier). |
| **Help > Sample data** | **Download** if recordings are missing: you choose the unpack folder (install `sampleData/` when writable, otherwise `jaerSampleData` in the home directory). **Show jAER sample data folder and README** if recordings are already present |
| Uninstaller | Deletes default `jaer/sampleData` (and `sampleData` when the install dir is the jAER tree). Warns that extra files you put there are removed too. Does **not** delete `~/jaerSampleData`. If files remain in the install folder after uninstall, that folder is opened. |

Download URL:

<https://github.com/SensorsINI/jaer/releases/latest/download/jaer-sample-data.zip>

README in the browser on all platforms (local `README.md` is shown inside jAER only if GitHub is unreachable):

<https://github.com/SensorsINI/jaer/tree/master/sampleData#readme>

Unpack so files land **in** the folder you choose (zip root is the files, not a nested `sampleData/` directory). The chooser defaults to `sampleData/` next to `dist/` / `lib/` in a git checkout and `jaer/sampleData` in an installed copy. If that path is not writable (Windows Program Files), the default is `jaerSampleData` under the user home directory. jAER remembers the folder on the File menu recent-folders list.

## Pack

1. Drop recordings into `sampleData/` (gitignored).
2. `ant pack-sample-data` (or `ant release` when recordings are present).

Writes `currentInstallers/<VERSION>/jaer-sample-data.zip` (store / no deflate; AEDAT-4 is already compressed) and `sampleData/SIZE.txt`. Skips the zip if a name+size stamp still matches. Force: `scripts/pack-sample-data.ps1 -Force` or `bash scripts/pack-sample-data.sh --force`.

The pack scripts also refresh the generated size table between `<!-- SAMPLE-DATA-CONTENTS -->` markers in `sampleData/README.md`. Keep file descriptions **outside** that block. Only `*.aedat4` (and `.aedat` / `.dat` / `.raw`) are zipped; WebP previews and source MP4/AVI are skipped.

## README preview clips

After exporting a rendered MP4 or AVI of each recording (File → Export video), name the file like the `.aedat4` (same stem) and drop it in `sampleData/preview-src/` (gitignored). Then:

```bash
bash scripts/make-sample-data-previews.sh
```

Windows: `powershell -File scripts/make-sample-data-previews.ps1`

That writes looping 5 s, 120 px-wide animated WebP to `sampleData/previews/` (`-loop 0`, 12 fps). Re-encode with `--force`. Optional start times (seconds into the source) go in `sampleData/previews/offsets.txt`:

```
DVS128 DVS09 2006 mouse behavior over 3 days  60
```

Commit the `.webp` files so the GitHub README table shows them. They are not packed into `jaer-sample-data.zip`.

Upload the zip with the release (`scripts/upload-github-release-installers.ps1` / `.sh`) so `/latest/download/jaer-sample-data.zip` is valid.

Installer checkbox sizes come from `SIZE.txt` at `install4jc` time (`-Djaer.sampleDataZipMiB` / `jaer.sampleDataUnpackedMiB`). `SIZE.txt` and `README.md` are install4j `fileEntry`s under `jaer/sampleData`.

In-app File → Open may still offer a download if the folder has no recordings (prefs `AEViewer.sampleDataDownloadDeclined`).

See [`README-releasing-tagging.md`](README-releasing-tagging.md).
