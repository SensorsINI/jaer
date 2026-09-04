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

Public dataset links also live in **Help -> Sample data**.

## Size

`SIZE.txt` is written by `ant pack-sample-data` from the local folder and zip
(not in git). It is the download size (zip) and the unpacked disk size. Do not
hand-edit it. Re-run pack after renaming or truncating recordings.

## Contents

<!-- SAMPLE-DATA-CONTENTS -->

Download **774 MB**, about **774 MB** on disk.

| File | Size |
|------|------|
| `DAVIS240C 2016  Tobi juggling.aedat4` | 6.4 MB |
| `DAVIS240C CapoCaccia 2016 Hotel dei Pini bar-export.aedat4` | 12.5 MB |
| `Davis346blue 2026 Steadicam test 6mm lens.aedat4` | 140.8 MB |
| `Davis346redColor MISTLab RoboCup soccer ball approaching from air.aedat4` | 14.6 MB |
| `DDD20 rec1501953155 San Marino drive clipped.aedat4` | 70.6 MB |
| `DVS128 DVS09 2006 crosshatch and single bar` | 10.2 MB |
| `DVS128 DVS09 2006 mouse behavior over 3 days.aedat4` | 86.3 MB |
| `DVS128 DVS09 2006 Patrick Lichtstieiner juggling.aedat4` | 13.9 MB |
| `NRV DELTA01 2026 humming birds squabbling.aedat4` | 10.4 MB |
| `PropheseeIMX636HD 2026 short heavily filtered.aedat4` | 37.5 MB |
| `PropheseeIMX636HD Metavision driving_sample street.aedat4` | 370.8 MB |

<!-- /SAMPLE-DATA-CONTENTS -->
