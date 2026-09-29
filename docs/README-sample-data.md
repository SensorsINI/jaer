# Sample recordings — packaging and installer

User-facing file list: [`jaerSampleData/README.md`](../jaerSampleData/README.md). This page is for packing, GitHub, and the installer.

## What ships where

Recordings are **not** in git and **not** in the basic installer. Git tracks `jaerSampleData/README.md` and looping WebP thumbs in `jaerSampleData/previews/`. Recordings stay in a local / Dropbox `jaerSampleData/` folder (gitignored). An older checkout may still have `sampleData/`; jAER renames that to `jaerSampleData` when the new name is free.

| Piece | Role |
|-------|------|
| GitHub Latest asset `jaer-sample-data.zip` | The zip users download |
| `jaerSampleData/README.md` | What the files are (also inside the zip and the install tree) |
| `jaerSampleData/previews/*.webp` | 5 s / 240 px loops for the GitHub README table. Not in the zip. |
| `jaerSampleData/SIZE.txt` | Zip and unpacked MiB; written by pack, not in git |
| Installer Welcome checkbox | Optional download; default **off**. Shows zip/unpacked size and a time estimate at **10 MB/s Wi-Fi**. If checked, the zip downloads in the background while files are copied. After that (rollback barrier), Setup waits for any remaining download and unpacks into `jaer/jaerSampleData` with no extra prompt. Cancel on that wait skips the recordings — jAER is already installed. |
| **Help > Sample data** | **Download** if recordings are missing. The chooser picks a parent; recordings always unpack into a folder named `jaerSampleData` (install tree when writable, otherwise `jaerSampleData` in the home directory). **Show jAER sample data folder and README** if recordings are already present |
| Uninstaller | Deletes default `jaer/jaerSampleData` and a leftover `jaer/sampleData` (and those names at the install root when that directory is the jAER tree). Warns that extra files you put there are removed too. Does **not** delete a `jaerSampleData` folder outside the install directory. If files remain in the install folder after uninstall, that folder is opened. |

Download URL:

<https://github.com/SensorsINI/jaer/releases/latest/download/jaer-sample-data.zip>

README in the browser on all platforms (local `README.md` is shown inside jAER only if GitHub is unreachable):

<https://github.com/SensorsINI/jaer/tree/master/jaerSampleData#readme>

The zip root is the files (no nested directory). Both the installer and **Help > Sample data** unpack into a directory named `jaerSampleData`. The chooser asks for the parent: `jaerSampleData/` next to `dist/` / `lib/` in a git checkout, and `jaer/jaerSampleData` in an installed copy. If that path is not writable (Windows Program Files), the default parent is the user home directory. Choosing Downloads creates `Downloads/jaerSampleData`. A folder that is already named `jaerSampleData` is used as-is. A legacy `sampleData` directory is renamed when `jaerSampleData` does not already exist beside it. jAER remembers the qualified folder on the File menu recent-folders list.

If a previous download unpacked straight into a mixed folder (the remembered path is not named `jaerSampleData` or `sampleData`, and that folder contains `README.md`), the next launch moves `README.md`, `SIZE.txt`, and recording files into `jaerSampleData` inside that folder.

## Pack

1. Drop recordings into `jaerSampleData/` (gitignored).
2. After exporting a rendered MP4 or AVI of each recording (File → Export video), name it like the `.aedat4` (same stem) and drop it in `jaerSampleData/preview-src/` (gitignored).
3. For the cloud release pipeline (`release.yml`): `ant upload-sample-data-current` (needs `gh` and recordings). That zips to `currentInstallers/<VERSION.txt>/jaer-sample-data.zip` and creates or updates GitHub Release **`sample-data-current`** (prerelease, never Latest). Assemble copies that zip onto each product/rc Release. Do this **before** tagging `N.N.N-rc.N`.
4. Mini-era attach onto an existing **product** Release (`VERSION.txt` or `-Djaer.upload.tag=`): `ant upload-sample-data`. Needs `gh` and that Release already created. Encodes WebP thumbs when ffmpeg and preview sources are present, then `gh release upload` (`--clobber`). Dry run: `ant "-Djaer.upload.whatif=true" upload-sample-data`. Skip WebP: `ant "-Dskip.sampleData.previews=true" upload-sample-data`. Force a new zip: `ant "-Djaer.sampleData.force=true" upload-sample-data`.

Do **not** `ant "-Djaer.upload.tag=sample-data-current" upload-sample-data`: that script looks for `currentInstallers/sample-data-current/jaer-sample-data.zip`, which pack never writes. Do **not** `ant create-draft-release` to make `sample-data-current` (that tags `VERSION.txt`).

Dry run for the durable Release (packs locally, skips `gh`): `ant "-Djaer.upload.whatif=true" upload-sample-data-current`. Check: `gh release view sample-data-current`. Never `gh release edit sample-data-current --latest`.

Standalone pieces (same as the upload pre-steps):

```powershell
ant make-sample-data-previews
ant pack-sample-data
```

`pack-sample-data` (also run from `ant macos-build-notarize` / `release-linux` / `install4j` when recordings are present) writes the zip (store / no deflate; AEDAT-4 is already compressed) and `SIZE.txt`. Skips the zip if a name+size stamp still matches. Force: `ant "-Djaer.sampleData.force=true" pack-sample-data`, or `scripts/pack-sample-data.ps1 -Force` / `bash scripts/pack-sample-data.sh --force`.

The pack scripts refresh the **Size** column in the `jaerSampleData/README.md` file table (matched by the backtick filename) and the **That downloads about N MB** line. Add new recordings as a row in that table; pack does not invent descriptions. Only `*.aedat4` (and `.aedat` / `.dat` / `.raw`) are zipped; WebP previews and source MP4/AVI are skipped.

`make-sample-data-previews` looks for sources in `jaerSampleData/preview-src/`, then `jaerSampleData/`. Re-encode with `--force` on the scripts. Optional start times (seconds into the source) go in `jaerSampleData/previews/offsets.txt`:

```
DVS128 DVS09 2006 mouse behavior over 3 days  60
```

Commit the `.webp` files so the GitHub README table shows them. They are not packed into `jaer-sample-data.zip`.

`upload-installers` does **not** attach the sample zip. Cloud pipeline: `ant upload-sample-data-current` before tagging. Mini-era product Release: `ant upload-sample-data`.

Installer checkbox sizes come from `SIZE.txt` at `install4jc` time (`-Djaer.sampleDataZipMiB` / `jaer.sampleDataUnpackedMiB`). `SIZE.txt` and `README.md` are install4j `fileEntry`s under `jaer/jaerSampleData`.

In-app File → Open may still offer a download if the folder has no recordings (prefs `AEViewer.sampleDataDownloadDeclined`).

See [`README-releasing-tagging.md`](README-releasing-tagging.md).
