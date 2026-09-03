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

`SIZE.txt` is written by `ant pack-sample-data` from the local folder and zip.
It is the download size (zip) and the unpacked disk size. Do not hand-edit it.
Re-run pack after renaming or truncating recordings.

## Contents

<!-- SAMPLE-DATA-CONTENTS -->

(Sizes below are unpacked file sizes. Zip size is in `SIZE.txt` after `ant pack-sample-data`.)

| File | Size | Source |
|------|------|--------|
| `DAVIS240C CapoCaccia 2016 Hotel dei Pini bar.aedat4` | 35.9 MB | jAER recording, CapoCaccia 2016 |
| `DAVIS346 MVSEC UPenn indoor_flying3.aedat4` | 95.3 MB | [MVSEC](https://daniilidis-group.github.io/mvsec/download/) indoor flying 3 (UPenn) |
| `DAVIS346-DVXplorer EvDownsampling Pevensey corridor.aedat4` | 182.6 MB | [EvDownsampling](https://github.com/anindyaghosh/EvDownsampling) Pevensey corridor (DAVIS346 + DVXplorer mux) |
| `Davis346blue Steadicam 2026 LR UD CW CCW.aedat4` | 883.7 MB | jAER recording (to be truncated) |
| `Davis346redColor MISTLab RoboCup soccer ball approaching from air.aedat4` | 14.6 MB | [MISTLab RoboCup soccer](https://github.com/MISTLab/event_based_data) (`ball_approaching_air`) |
| `PropheseeIMX636HD 2026 short heavily filtered.aedat4` | 37.5 MB | jAER recording |
| `PropheseeIMX636HD Metavision driving_sample street.aedat4` | 370.8 MB | [Prophesee Metavision](https://docs.prophesee.ai/stable/datasets.html) `driving_sample` (Gen4.1 / IMX636) |
| `Tmpdiff128 DVS09 2006 mouse behavior over 3 days.aedat4` | 86.3 MB | [DVS09 / DVS128 samples](https://docs.google.com/document/d/16b4H78f4vG_QvYDK2Tq0sNBA-y7UFnRbNnsGbD1jJOg/edit?tab=t.0) |

<!-- /SAMPLE-DATA-CONTENTS -->
