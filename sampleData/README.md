# jAER sample recordings

These recordings are **not** stored in git and are **not** in the basic installer
(they are too large). This folder sits next to `dist/` and `lib/` in a git
checkout and in an installed copy.

## Download

Asset (fixed name on the GitHub **Latest** release):

<https://github.com/SensorsINI/jaer/releases/latest/download/jaer-sample-data.zip>

Unpack so the recordings land **in this folder** (zip root is the files, not a
nested `sampleData/` directory).

| How | What to do |
|-----|------------|
| New install / update without recordings | Optional Welcome checkbox (default on) |
| Already installed, empty folder | **File -> Open**, or **Help -> Sample data -> Download jAER sample recordings...** |
| Manual | Browser download of the zip, or `gh release download --pattern jaer-sample-data.zip` then unzip here |

## Size

`SIZE.txt` is written by `ant pack-sample-data` from the local folder and zip.
It is the download size (zip) and the unpacked disk size. Do not hand-edit it.

## Contents

<!-- SAMPLE-DATA-CONTENTS -->

Download **96 MB**, about **120 MB** on disk.

| File | Size |
|------|------|
| `DAVIS240C-2016-04-29T07-14-04+0200-00000075-0 bar2-export.aedat4` | 35.9 MB |
| `Davis346blue-2025-05-28T12-01-20+0200-00000105-0 15x10 25mm f3p5 kowa lens calib.aedat4` | 46.8 MB |
| `PropheseeIMX636HD-2026-08-19T17-51-29+0200--0 short heavily filtered recording.aedat4` | 37.5 MB |

<!-- /SAMPLE-DATA-CONTENTS -->
