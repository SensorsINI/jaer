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
| **Legacy raw DAT** | `.dat` | Often AEDAT-1/2 without a `% ` header ([AEDAT overview](https://docs.inivation.com/software/software-advanced-usage/file-formats/index.html)) | SensorsINI / jAER | yes | no³ | **yes** | None | Same as AEDAT-1/2 depending on header; pre-2010 / DVS09 `.dat` assumed `DVS128` when no stronger hint |
| **[Metavision DAT](https://docs.prophesee.ai/stable/data/file_formats/dat.html)** | `.dat` | Decoded CD / Event2d: ASCII `% ` header, then type/size byte pair, then 8-byte LE events | [Prophesee](https://www.prophesee.ai/) Metavision | yes | no⁴ | no | None (decoded `t` + packed `x`/`y`/`p`; larger than RAW) | Size from `% Width` / `% Height` — chip `PropheseeIMX636HD` (1280×720) or `DVS640` (640×480); other sizes use the DSEC size fit. Disambiguated from legacy jAER `.dat` by the `% ` header |
| **[Metavision RAW EVT3](https://docs.prophesee.ai/stable/data/file_formats/raw.html)** | `.raw` | Prophesee native RAW, `% evt 3.0` / `% format EVT3…` ([EVT3](https://docs.prophesee.ai/stable/data/encoding_formats/evt3.html)) | [Prophesee](https://www.prophesee.ai/) Metavision | yes | no⁴ | no | None (native EVT3 bitstream after ASCII `%` header) | **EVK4 / IMX636 HD**; Gen4.1 HD sample recordings (e.g. `laser.raw`) — chip `PropheseeIMX636HD` |
| **[DSEC HDF5](https://dsec.ifi.uzh.ch/data-format/)** | `.h5` / `.hdf5` (`events.h5`) | cooked `/events/{p,t,x,y}`, `/ms_to_idx`, `/t_offset` | [DSEC](https://dsec.ifi.uzh.ch/) (UZH); also some EVK4 exports | yes | Save As⁷ | no | Play: Blosc + ZSTD; Save As: uncompressed | Size from HDF5 attrs or max x/y — chip `DVS640` (640×480) or `DVS1280x720SD` (1280×720); left/right are separate files |
| **[ROS bag](http://wiki.ros.org/Bags)** | `.bag` | ROS1 bag (rpg_dvs_ros / MVSEC / EV-IMO topics) | ROS / UZH RPG / dataset authors | yes | no | no⁵ | Bag-internal (ROS serialization); not jAER-selectable | DAVIS-class topics in RPG/MVSEC/EV-IMO bags |
| **Text events** | `.csv`, `.txt` | One DVS event per line (`t,x,y,p` variants) | Various exports / tools | yes | Save As⁷ | no | None (ASCII text) | Any polarity chip after address reconstruct (often DAVIS-oriented CSV) |
| **Index playlist** | `.aeidx` (also `.index`) | List of paths to AE data files | jAER | yes | yes⁶ | `.index` is legacy | N/A (text index) | N/A — points at other recordings |

¹ Prefer `.aedat2` for new AEDAT-2 writes; `.aedat` remains accepted on open.  
² Playback support in [`AEFileInputStream`](../src/net/sf/jaer/eventio/AEFileInputStream.java); not offered as a recording format.  
³ Recording no longer uses bare `.dat` as the preferred extension.  
⁴ Live EVK4 capture is recorded as AEDAT-4/2 in jAER; Metavision Studio writes `.raw` (and can export DAT).  
⁵ Still common for public datasets; not a jAER-native recording path.  
⁶ Created when using synchronized multi-viewer logging (an `.aeidx` listing the sibling data files).  
⁷ **File → Save As…** (`Ctrl+Shift+S`) while playing a recording (not live logging). Optional IN/OUT markers, EventFilters, and HVS sidecars (`XXX-frames/` PNGs, `XXX-imu.csv`).

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
| Metavision DAT | [Prophesee — DAT File Format](https://docs.prophesee.ai/stable/data/file_formats/dat.html) |
| EVT 3.0 encoding | [Prophesee — EVT 3.0](https://docs.prophesee.ai/stable/data/encoding_formats/evt3.html) |
| EVT 2.0 encoding | [Prophesee — EVT 2.0](https://docs.prophesee.ai/stable/data/encoding_formats/evt2.html) (not yet played by jAER) |
| Prophesee HDF5 | [Prophesee — HDF5 event files](https://docs.prophesee.ai/stable/data/file_formats/hdf5.html) (not yet played by jAER; DSEC-layout `.h5` is a different path) |
| DSEC HDF5 events | [DSEC — Data Format](https://dsec.ifi.uzh.ch/data-format/) |
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
| [DSEC HDF5](https://dsec.ifi.uzh.ch/data-format/) | [`DsecHdf5AEOutputStream`](../src/net/sf/jaer/eventio/dsec/DsecHdf5AEOutputStream.java) | **File → Save As** (playback). Cooked `/events/{p,t,x,y}` with DSEC/image coords (`y=0` top, `p` 0=off/1=on), `/ms_to_idx`, `/t_offset`; uncompressed (jHDF 0.12). Width/height attributes for reopen. |
| Text CSV/TXT | [`CsvEventSink`](../src/net/sf/jaer/eventio/export/CsvEventSink.java) | **File → Save As** (playback). Options match [`DavisTextEventFormatter`](../src/net/sf/jaer/util/textio/DavisTextEventFormatter.java) (`t,x,y,p` variants; RPG preset). The EventFilter [`DavisTextOutputWriter`](../src/net/sf/jaer/util/textio/DavisTextOutputWriter.java) still streams text during play. |

### File → Save As (playback export)

Enabled only while a recording is open (`PlayMode.PLAYBACK`). Unlike relogging (AEDAT at ViewLoop pace), Save As pauses playback and scans the file as fast as possible, then restores position.

- **Use IN and OUT markers** (default on): unset ends are file start / EOF.
- **Apply EventFilters** (default on): same chain as filtered relogging.
- **HVS sidecars** (DAVIS / CDAVIS): optional `<basename>-frames/` compressed PNGs + `timestamps.txt`, and `<basename>-imu.csv`.

Dialog: [`SaveAsExportDialog`](../src/net/sf/jaer/eventio/export/SaveAsExportDialog.java).

---

## Playback formats (detail)

| Format | Reader | Notes |
|--------|--------|--------|
| [AEDAT-4](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-4.0.html) | [`Aedat4FileInputStream`](../src/net/sf/jaer/eventio/aedat4/Aedat4FileInputStream.java) | Multi-camera EVTS stream selection; decompresses LZ4/ZSTD as needed. |
| [AEDAT-1](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-1.0.html)/[2](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-2.0.html)/[3](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-3.1.html), legacy `.dat` | [`AEFileInputStream`](../src/net/sf/jaer/eventio/AEFileInputStream.java) | Version from `#!AER-DAT…` header line. Used for `.dat` only when the file is **not** Metavision DAT. |
| [Metavision DAT](https://docs.prophesee.ai/stable/data/file_formats/dat.html) | [`MetavisionDatFileInputStream`](../src/prophesee/eventio/MetavisionDatFileInputStream.java) | Peek: lines starting with `% ` (vs jAER `#` / raw AEDAT-1). CD / Event2d types `0` and `12` only (8-byte LE `t` + packed `x`/`y`/`p`). External-trigger DAT (`type 14`) is not played. Random-access seek; no index cache. |
| [Metavision RAW EVT3](https://docs.prophesee.ai/stable/data/file_formats/raw.html) | [`MetavisionRawFileInputStream`](../src/prophesee/eventio/MetavisionRawFileInputStream.java) | Same `Evt3Parser` as live USB ([EVT3](https://docs.prophesee.ai/stable/data/encoding_formats/evt3.html)). Seek index cached as `*.metavisionrawidx` in `java.io.tmpdir`. **EVT2 / Prophesee HDF5 not supported yet.** |
| [DSEC HDF5](https://dsec.ifi.uzh.ch/data-format/) | [`DsecHdf5AEInputStream`](../src/net/sf/jaer/eventio/dsec/DsecHdf5AEInputStream.java) | Single-camera cooked `events.h5` (left or right): pack via chip `getAddressFromCell`. Uses [jHDF](https://jhdf.io/) + [`BloscHdf5Filter`](../src/net/sf/jaer/eventio/dsec/BloscHdf5Filter.java) for Blosc/ZSTD. Chip from peeked size: `DVS640` (640×480) or `DVS1280x720SD` (1280×720). Stereo dual-stream later. **Save As** writes the same layout uncompressed via [`DsecHdf5AEOutputStream`](../src/net/sf/jaer/eventio/dsec/DsecHdf5AEOutputStream.java). |
| [ROS bag](http://wiki.ros.org/Bags) | [`RosbagFileInputStream`](../src/net/sf/jaer/eventio/ros/RosbagFileInputStream.java) | Topics under `/dvs/`, `/davis/left/`, or `/samsung/camera/` headers. |
| Text | [`TextFileInputStream`](../src/net/sf/jaer/eventio/TextFileInputStream.java) | CSV/space-separated DVS lines; options for timestamp units and polarity. |
| Index | AEPlayer index path | Opens the listed AE files in sequence. |

Chip auto-detect for recordings: [`RecordingChipDetector`](../src/net/sf/jaer/eventio/RecordingChipDetector.java) (filename token, AEDAT-4 `infoNode`, Metavision RAW / DAT header, AEDAT-2 header). `.dat` with a `% ` header is Metavision DAT; other `.dat` still falls back to `DVS128`.

---

## Compression reference

| Format | Options | Where set |
|--------|---------|-----------|
| AEDAT-4 | `NONE` (0), `LZ4` (1, default), `LZ4_HIGH` (2), `ZSTD` (3), `ZSTD_HIGH` (4) | AEViewer recording prefs; see [`CompressionType`](../src/net/sf/jaer/eventio/aedat4/dv/CompressionType.java), [`Aedat4Compression`](../src/net/sf/jaer/eventio/aedat4/Aedat4Compression.java) |
| AEDAT-1/2/3, legacy `.dat` | None | — |
| Metavision `.dat` | None (decoded events; typically larger than RAW) | Exported by Metavision Studio / SDK (`File to DAT`) |
| Metavision `.raw` | None (sensor EVT3 encoding is the “compression”) | Recorded by Metavision Studio / SDK |
| ROS bag | ROS bag storage (not exposed in jAER UI) | — |
| Text CSV/TXT | None (optionally gzip outside jAER) | — |
| DSEC HDF5 (Save As) | Uncompressed contiguous datasets (jHDF 0.12 cannot write gzip/Blosc) | File → Save As |

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
- [Prophesee driver README](../src/prophesee/README.md) — EVK4 live + RAW EVT3 / DAT playback
- [USB live acquisition bench](usb-live-acquisition-bench.md)
