# Sample recordings — packaging and installer

User-facing file list: [`sampleData/README.md`](../sampleData/README.md). This page is for packing, GitHub, and the installer.

## What ships where

Recordings are **not** in git and **not** in the basic installer. Git tracks `sampleData/README.md` and looping WebP thumbs in `sampleData/previews/`. Recordings stay in a local / Dropbox `sampleData/` folder (gitignored).

| Piece | Role |
|-------|------|
| GitHub Latest asset `jaer-sample-data.zip` | The zip users download |
| `sampleData/README.md` | What the files are (also inside the zip and the install tree) |
| `sampleData/previews/*.webp` | 5 s / 240 px loops for the GitHub README table. Not in the zip. |
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
2. After exporting a rendered MP4 or AVI of each recording (File → Export video), name it like the `.aedat4` (same stem) and drop it in `sampleData/preview-src/` (gitignored).
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

The pack scripts refresh the **Size** column in the `sampleData/README.md` file table (matched by the backtick filename) and the **That downloads about N MB** line. Add new recordings as a row in that table; pack does not invent descriptions. Only `*.aedat4` (and `.aedat` / `.dat` / `.raw`) are zipped; WebP previews and source MP4/AVI are skipped.

`make-sample-data-previews` looks for sources in `sampleData/preview-src/`, then `sampleData/`. Re-encode with `--force` on the scripts. Optional start times (seconds into the source) go in `sampleData/previews/offsets.txt`:

```
DVS128 DVS09 2006 mouse behavior over 3 days  60
```

Commit the `.webp` files so the GitHub README table shows them. They are not packed into `jaer-sample-data.zip`.

`upload-installers` does **not** attach the sample zip. Cloud pipeline: `ant upload-sample-data-current` before tagging. Mini-era product Release: `ant upload-sample-data`.

Installer checkbox sizes come from `SIZE.txt` at `install4jc` time (`-Djaer.sampleDataZipMiB` / `jaer.sampleDataUnpackedMiB`). `SIZE.txt` and `README.md` are install4j `fileEntry`s under `jaer/sampleData`.

In-app File → Open may still offer a download if the folder has no recordings (prefs `AEViewer.sampleDataDownloadDeclined`).

See [`README-releasing-tagging.md`](README-releasing-tagging.md).
