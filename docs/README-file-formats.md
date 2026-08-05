# jAER file formats — play and record

Supported event-data file types for **playback** (File → Open / drag-drop) and **recording** (logging from a live device or filtered stream). Detection and open paths live in [`AEChip.constuctFileInputStream`](../src/net/sf/jaer/chip/AEChip.java); the open dialog filter is [`DATFileFilter`](../src/net/sf/jaer/util/DATFileFilter.java).

Extensions and AEDAT version constants: [`AEDataFile`](../src/net/sf/jaer/eventio/AEDataFile.java).

Official format specs (where available) are linked from the **Format** column and listed again under [Format specifications](#format-specifications).

---

## Summary

| Format | Ext. | Generation / magic | Manufacturer / origin | Play | Record | Legacy | Compression | Matching cameras / chips |
|--------|------|--------------------|------------------------|:----:|:------:|:------:|-------------|---------------------------|
| **[AEDAT-4](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-4.0.html)** | `.aedat4` | AEDAT **4.0** (`#!AER-DAT4.0`) | [iniVation](https://inivation.com/) DV / jAER 3 | yes | **yes (default)** | no | Per-packet: **NONE**, **LZ4** (default), **LZ4_HIGH**, **ZSTD**, **ZSTD_HIGH** | DAVIS346 / DAVIS240 / DVXplorer family; any jAER chip that logs AEDAT-4 (incl. Prophesee EVK4, NRV when recording in jAER) |
| **[AEDAT-2](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-2.0.html)** | `.aedat2` (preferred write), also `.aedat` | AEDAT **2.0** (`#!AER-DAT2.0`) | SensorsINI / jAER | yes | yes | partial¹ | None (raw `int32` address + `int32` timestamp) | Classic DVS/DAVIS jAER chips (DVS128, DAVIS240/346, Cochlea, etc.); widely used historical logs |
| **[AEDAT-1](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-1.0.html)** | `.aedat`, `.dat` | AEDAT **1.0** (`#!AER-DAT1.0`) | SensorsINI / jAER | yes | no | **yes** | None (`int16` address + `int32` timestamp) | Early AER boards / old DVS128-era recordings |
| **[AEDAT-3](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-3.1.html)** | typically `.aedat` | AEDAT **3.0 / 3.1** (`#!AER-DAT3.x`) | cAER / community | yes² | no | **yes** | None (packed AER-3 address words) | Rare in modern jAER workflows; open if header declares 3.x |
| **Legacy raw DAT** | `.dat` | Often AEDAT-1/2 without clear modern naming ([AEDAT overview](https://docs.inivation.com/software/software-advanced-usage/file-formats/index.html)) | SensorsINI / jAER | yes | no³ | **yes** | None | Same as AEDAT-1/2 depending on header |
| **[Metavision RAW EVT3](https://docs.prophesee.ai/stable/data/file_formats/raw.html)** | `.raw` | Prophesee native RAW, `% evt 3.0` / `% format EVT3…` ([EVT3](https://docs.prophesee.ai/stable/data/encoding_formats/evt3.html)) | [Prophesee](https://www.prophesee.ai/) Metavision | yes | no⁴ | no | None (native EVT3 bitstream after ASCII `%` header) | **EVK4 / IMX636 HD**; Gen4.1 HD sample recordings (e.g. `laser.raw`) — chip `PropheseeIMX636HD` |
| **[ROS bag](http://wiki.ros.org/Bags)** | `.bag` | ROS1 bag (rpg_dvs_ros / MVSEC / EV-IMO topics) | ROS / UZH RPG / dataset authors | yes | no | no⁵ | Bag-internal (ROS serialization); not jAER-selectable | DAVIS-class topics in RPG/MVSEC/EV-IMO bags |
| **Text events** | `.csv`, `.txt` | One DVS event per line (`t,x,y,p` variants) | Various exports / tools | yes | no | no | None (ASCII text) | Any polarity chip after address reconstruct (often DAVIS-oriented CSV) |
| **Index playlist** | `.aeidx` (also `.index`) | List of paths to AE data files | jAER | yes | yes⁶ | `.index` is legacy | N/A (text index) | N/A — points at other recordings |

¹ Prefer `.aedat2` for new AEDAT-2 writes; `.aedat` remains accepted on open.  
² Playback support in [`AEFileInputStream`](../src/net/sf/jaer/eventio/AEFileInputStream.java); not offered as a recording format.  
³ Recording no longer uses bare `.dat` as the preferred extension.  
⁴ Live EVK4 capture is recorded as AEDAT-4/2 in jAER; Metavision Studio writes `.raw`.  
⁵ Still common for public datasets; not a jAER-native recording path.  
⁶ Created when using synchronized multi-viewer logging (an `.aeidx` listing the sibling data files).

---

## Format specifications

| Format | Spec / documentation |
|--------|----------------------|
| AEDAT (all versions overview) | [iniVation — AEDAT File Formats](https://docs.inivation.com/software/software-advanced-usage/file-formats/index.html) |
| AEDAT 4.0 | [iniVation — AEDAT 4.0](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-4.0.html) |
| AEDAT 3.1 | [iniVation — AEDAT 3.1](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-3.1.html) |
| AEDAT 2.0 | [iniVation — AEDAT 2.0](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-2.0.html) |
| AEDAT 1.0 | [iniVation — AEDAT 1.0](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-1.0.html) |
| Metavision RAW container | [Prophesee — RAW File Format](https://docs.prophesee.ai/stable/data/file_formats/raw.html) |
| EVT 3.0 encoding | [Prophesee — EVT 3.0](https://docs.prophesee.ai/stable/data/encoding_formats/evt3.html) |
| EVT 2.0 encoding | [Prophesee — EVT 2.0](https://docs.prophesee.ai/stable/data/encoding_formats/evt2.html) (not yet played by jAER) |
| Prophesee DAT / HDF5 | [Prophesee — File Formats](https://docs.prophesee.ai/stable/data/file_formats/index.html) (not yet played by jAER) |
| ROS bag | [ROS wiki — Bags](http://wiki.ros.org/Bags) |
| Text CSV/TXT | No formal standard; see [`TextFileInputStream`](../src/net/sf/jaer/eventio/TextFileInputStream.java) options |
| `.aeidx` index | jAER-specific playlist (paths to AE files); no external spec |

---

## Recording formats (detail)

Logging format is chosen in AEViewer prefs / Control menu (`loggingDataFileVersion`). Default is **AEDAT-4**.

| Format | Writer | Notes |
|--------|--------|--------|
| [AEDAT-4](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-4.0.html) | [`Aedat4FileOutputStream`](../src/net/sf/jaer/eventio/aedat4/Aedat4FileOutputStream.java) | DV-compatible FlatBuffers packets (events, frames, IMU). Compression via `AEViewer.aedat4Compression`. Sparse index cache under `java.io.tmpdir` (`*.aedat4idx`) speeds reopen. |
| [AEDAT-2](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-2.0.html) | [`AEFileOutputStream`](../src/net/sf/jaer/eventio/AEFileOutputStream.java) | Classic `#` ASCII header + binary address/timestamp pairs. Extension `.aedat2`. |

---

## Playback formats (detail)

| Format | Reader | Notes |
|--------|--------|--------|
| [AEDAT-4](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-4.0.html) | [`Aedat4FileInputStream`](../src/net/sf/jaer/eventio/aedat4/Aedat4FileInputStream.java) | Multi-camera EVTS stream selection; decompresses LZ4/ZSTD as needed. |
| [AEDAT-1](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-1.0.html)/[2](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-2.0.html)/[3](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-3.1.html), `.dat` | [`AEFileInputStream`](../src/net/sf/jaer/eventio/AEFileInputStream.java) | Version from `#!AER-DAT…` header line. |
| [Metavision RAW EVT3](https://docs.prophesee.ai/stable/data/file_formats/raw.html) | [`MetavisionRawFileInputStream`](../src/prophesee/eventio/MetavisionRawFileInputStream.java) | Same `Evt3Parser` as live USB ([EVT3](https://docs.prophesee.ai/stable/data/encoding_formats/evt3.html)). Seek index cached as `*.metavisionrawidx` in `java.io.tmpdir`. **EVT2 / HDF5 / Prophesee DAT not supported yet.** |
| [ROS bag](http://wiki.ros.org/Bags) | [`RosbagFileInputStream`](../src/net/sf/jaer/eventio/ros/RosbagFileInputStream.java) | Topics under `/dvs/`, `/davis/left/`, or `/samsung/camera/` headers. |
| Text | [`TextFileInputStream`](../src/net/sf/jaer/eventio/TextFileInputStream.java) | CSV/space-separated DVS lines; options for timestamp units and polarity. |
| Index | AEPlayer index path | Opens the listed AE files in sequence. |

Chip auto-detect for recordings: [`RecordingChipDetector`](../src/net/sf/jaer/eventio/RecordingChipDetector.java) (filename token, AEDAT-4 `infoNode`, Metavision RAW header, AEDAT-2 header).

---

## Compression reference

| Format | Options | Where set |
|--------|---------|-----------|
| AEDAT-4 | `NONE` (0), `LZ4` (1, default), `LZ4_HIGH` (2), `ZSTD` (3), `ZSTD_HIGH` (4) | AEViewer recording prefs; see [`CompressionType`](../src/net/sf/jaer/eventio/aedat4/dv/CompressionType.java), [`Aedat4Compression`](../src/net/sf/jaer/eventio/aedat4/Aedat4Compression.java) |
| AEDAT-1/2/3, `.dat` | None | — |
| Metavision `.raw` | None (sensor EVT3 encoding is the “compression”) | Recorded by Metavision Studio / SDK |
| ROS bag | ROS bag storage (not exposed in jAER UI) | — |
| Text CSV/TXT | None (optionally gzip outside jAER) | — |

---

## Sample data links

Help → **Sample data** in AEViewer:

| Item | URL |
|------|-----|
| DAVIS346 AEDAT-2 samples | [DAVIS24 site](https://sites.google.com/view/davis24-davis-sample-data/home) |
| AEDAT-4 / DV samples | [MISTLab/event_based_data](https://github.com/MISTLab/event_based_data) |
| Prophesee / Metavision samples | [Prophesee datasets](https://docs.prophesee.ai/stable/datasets.html#chapter-datasets) |

Constants: [`JaerConstants`](../src/net/sf/jaer/JaerConstants.java).

---

## Related docs

- [jAER 3 pipeline](README-jaer3.md) — PacketBundle path and AEDAT-4 logging
- [Prophesee driver README](../src/prophesee/README.md) — EVK4 live + RAW EVT3 playback
- [USB live acquisition bench](usb-live-acquisition-bench.md)
