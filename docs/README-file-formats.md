# jAER file formats — play and record

Supported event-data file types for **playback** (File → Open / drag-drop) and **recording** (from a live device or filtered stream). Detection and open paths live in [`AEChip.constuctFileInputStream`](../src/net/sf/jaer/chip/AEChip.java); the open dialog filter is [`DATFileFilter`](../src/net/sf/jaer/util/DATFileFilter.java).

Extensions and AEDAT version constants: [`AEDataFile`](../src/net/sf/jaer/eventio/AEDataFile.java).

Official format specs (where available) are linked from the **Format** column and listed again under [Format specifications](#format-specifications).

---

## Summary

| Format | Ext. | Generation / magic | Manufacturer / origin | Play | Record | Legacy | Compression | Matching cameras / chips |
|--------|------|--------------------|------------------------|:----:|:------:|:------:|-------------|---------------------------|
| **[AEDAT-4](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-4.0.html)** | `.aedat4` | AEDAT **4.0** (`#!AER-DAT4.0`) | [iniVation](https://inivation.com/) DV / jAER 3 | yes | **yes (default)** + Save As⁷ | no | Per-packet: **NONE**, **LZ4** (default), **LZ4_HIGH**, **ZSTD**, **ZSTD_HIGH**. Optional [lossy timestamp resolution](#lossy-timestamp-resolution) under the same codec (jAER-only EVTS) | DAVIS346 / DAVIS240 / DVXplorer family; any jAER chip that records AEDAT-4 (incl. Prophesee EVK4, NRV when recording in jAER) |
| **AEDZ** | `.aedz` | Binary magic `AEDZ`, then an embedded AEDAT-2 header | SensorsINI / jAER | yes | yes + Save As⁷ | no | 8 byte-planes, zstd level 1. Optional [lossy timestamp shift](#lossy-timestamp-resolution) (every event kept) | Polarity-only chips. Live Davis / DVXplorer are steered to AEDAT-4 (frames and IMU are not stored) |
| **[AEDAT-2](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-2.0.html)** | `.aedat2` (preferred write), also `.aedat` | AEDAT **2.0** (`#!AER-DAT2.0`) | SensorsINI / jAER | yes | yes | partial¹ | None (raw `int32` address + `int32` timestamp) | Classic DVS/DAVIS jAER chips (DVS128, DAVIS240/346, Cochlea, etc.); widely used historical logs |
| **[AEDAT-1](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-1.0.html)** | `.aedat`, `.dat` | AEDAT **1.0** (`#!AER-DAT1.0`) | SensorsINI / jAER | yes | no | **yes** | None (`int16` address + `int32` timestamp) | Early AER boards / old DVS128-era recordings |
| **[AEDAT-3](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-3.1.html)** | typically `.aedat` | AEDAT **3.0 / 3.1** (`#!AER-DAT3.x`) | cAER / community | yes² | no | **yes** | None (packed AER-3 address words) | Rare in modern jAER workflows; open if header declares 3.x |
| **Legacy raw DAT** | `.dat` | Often AEDAT-1/2 without a `% ` header ([AEDAT overview](https://docs.inivation.com/software/software-advanced-usage/file-formats/index.html)) | SensorsINI / jAER | yes | no³ | **yes** | None | Same as AEDAT-1/2 depending on header; pre-2010 / DVS09 `.dat` assumed `DVS128` when no stronger hint |
| **[Metavision DAT](https://docs.prophesee.ai/stable/data/file_formats/dat.html)** | `.dat` | Decoded CD / Event2d: ASCII `% ` header, then type/size byte pair, then 8-byte LE events | [Prophesee](https://www.prophesee.ai/) Metavision | yes | no⁴ | no | None (decoded `t` + packed `x`/`y`/`p`; larger than RAW) | Size from `% Width` / `% Height` — chip `PropheseeIMX636HD` (1280×720) or `DVS640` (640×480); other sizes use the DSEC size fit. Disambiguated from legacy jAER `.dat` by the `% ` header |
| **[Metavision RAW EVT3](https://docs.prophesee.ai/stable/data/file_formats/raw.html)** | `.raw` | Prophesee native RAW, `% evt 3.0` / `% format EVT3…` ([EVT3](https://docs.prophesee.ai/stable/data/encoding_formats/evt3.html)) | [Prophesee](https://www.prophesee.ai/) Metavision | yes | no⁴ | no | None (native EVT3 bitstream after ASCII `%` header) | **EVK4 / IMX636 HD**; Gen4.1 HD sample recordings (e.g. `laser.raw`) — chip `PropheseeIMX636HD` |
| **[DSEC HDF5](https://dsec.ifi.uzh.ch/data-format/)** | `.h5` / `.hdf5` (`events.h5`) | cooked `/events/{p,t,x,y}`, `/ms_to_idx`, `/t_offset` | [DSEC](https://dsec.ifi.uzh.ch/) (UZH); also some EVK4 exports | yes | Save As⁷ | no | Play: Blosc + ZSTD; Save As: uncompressed | Size from HDF5 attrs or max x/y — chip `DVS640` (640×480) or `DVS1280x720SD` (1280×720); left/right are separate files |
| **Event Planar HDF5** | `.h5` | cooked `/events/{xs,ys,ts,ps}`, attrs `t0` / `sensor_resolution` | [TU Delft event_planar](https://github.com/tudelft/event_planar) (DAVIS240C) | yes | no | no | Play: HDF5 ZSTD (id 32015) and/or Blosc | 240×180 → chip `DAVIS240C`; same cooked packer as DSEC |
| **[ROS bag](http://wiki.ros.org/Bags)** | `.bag` | ROS1 bag (rpg_dvs_ros / MVSEC / EV-IMO topics) | ROS / UZH RPG / dataset authors | yes | no | no⁵ | Bag-internal (ROS serialization); not jAER-selectable | DAVIS-class topics in RPG/MVSEC/EV-IMO bags |
| **Text events** | `.csv`, `.txt` | One DVS event per line (`t,x,y,p` variants) | Various exports / tools | yes | Save As⁷ | no | None (ASCII text) | Any polarity chip after address reconstruct (often DAVIS-oriented CSV) |
| **Index playlist** | `.aeidx` (also `.index`) | List of paths to AE data files | jAER | yes | AEDAT-2/AEDZ sync only⁶ | `.index` is legacy | N/A (text index) | N/A — points at other recordings |

¹ Prefer `.aedat2` for new AEDAT-2 writes; `.aedat` remains accepted on open.  
² Playback support in [`AEFileInputStream`](../src/net/sf/jaer/eventio/AEFileInputStream.java); not offered as a recording format.  
³ Recording no longer uses bare `.dat` as the preferred extension.  
⁴ Live EVK4 capture is recorded as AEDAT-4/2 in jAER; Metavision Studio writes `.raw` (and can export DAT).  
⁵ Still common for public datasets; not a jAER-native recording path.  
⁶ **AEDAT-4** synchronized recording writes **one** `.aedat4` with 3 streams per camera (EVTS/FRME/IMUS). `.aeidx` is still written for AEDAT-2/AEDZ sync, and old `.aeidx` files still open.  
⁷ **File → Save As…** (`Ctrl+Shift+S`) while playing a recording (not live recording). Formats: native **AEDAT-4** (default; preferred over re-recording to clip IN/OUT or apply EventFilters), **AEDAT-Z** polarity (optional lossy timestamp resolution), CSV/text, DSEC HDF5. Optional IN/OUT markers and EventFilters. CSV/HDF5/AEDAT-Z can add HVS sidecars (`XXX-frames/` PNGs, `XXX-imu.csv`); AEDAT-4 keeps frames/IMU in the file.

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
| Event Planar HDF5 | [tudelft/event_planar `H5Loader.get_events`](https://github.com/tudelft/event_planar/blob/main/dataloader/h5.py) — not DSEC and not DDD17/DDD20 |
| ROS bag | [ROS wiki — Bags](http://wiki.ros.org/Bags) |
| Text CSV/TXT | No formal standard; see [`TextFileInputStream`](../src/net/sf/jaer/eventio/TextFileInputStream.java) options |
| AEDZ | jAER-specific compressed AEDAT-2 polarity; no external spec. See [`AEDZOutputStream`](../src/net/sf/jaer/eventio/AEDZOutputStream.java) |
| `.aeidx` index | jAER-specific playlist (paths to AE files); no external spec |

---

## Recording formats (detail)

Recording format is chosen in AEViewer prefs / Control menu (`recordingDataFileVersion`). Default is **AEDAT-4**.

| Format | Writer | Notes |
|--------|--------|--------|
| [AEDAT-4](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-4.0.html) | [`Aedat4FileOutputStream`](../src/net/sf/jaer/eventio/aedat4/Aedat4FileOutputStream.java) | DV-compatible FlatBuffers packets (events, frames, IMU). Live recording compression via `AEViewer.aedat4Compression`. Optional lossy time bins (`AEViewer.aedat4LossyTimeBins`, shift default 10) replace EVTS FlatBuffers with LBEV counts; playback expands them. **Synchronized** multi-viewer recording muxes each camera as EVTS/FRME/IMUS (IDs `3i`/`3i+1`/`3i+2`) in **one** file. **File → Save As** (playback) writes the same format (IN/OUT clip, optional EventFilters; compression chosen in the dialog). Sparse index cache under `${java.io.tmpdir}/jaer/aeidx/` (`*.aedat4idx`) speeds reopen. |
| [AEDAT-2](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-2.0.html) | [`AEFileOutputStream`](../src/net/sf/jaer/eventio/AEFileOutputStream.java) | Classic `#` ASCII header + binary address/timestamp pairs. Extension `.aedat2`. |
| AEDZ | [`AEDZOutputStream`](../src/net/sf/jaer/eventio/AEDZOutputStream.java) | Compressed AEDAT-2 polarity (`.aedz`). Live typed demux does not put IMU/APS into raw AEs, so Davis / DVXplorer cannot start AEDZ: a warning offers AEDAT-4 at the current compression. **File → Save As** can write `.aedz`. Optional lossy timestamp resolution right-shifts each timestamp (default 10 bits) and still stores every event, so current AEDZ playback shows the coarser times. Frames and IMU are not in the `.aedz` file. |
| [DSEC HDF5](https://dsec.ifi.uzh.ch/data-format/) | [`DsecHdf5AEOutputStream`](../src/net/sf/jaer/eventio/dsec/DsecHdf5AEOutputStream.java) | **File → Save As** (playback). Cooked `/events/{p,t,x,y}` with DSEC/image coords (`y=0` top, `p` 0=off/1=on), `/ms_to_idx`, `/t_offset`; uncompressed (jHDF 0.12). Width/height attributes for reopen. |
| Text CSV/TXT | [`CsvEventSink`](../src/net/sf/jaer/eventio/export/CsvEventSink.java) | **File → Save As** (playback). Options match [`DavisTextEventFormatter`](../src/net/sf/jaer/util/textio/DavisTextEventFormatter.java) (`t,x,y,p` variants; RPG preset). The EventFilter [`DavisTextOutputWriter`](../src/net/sf/jaer/util/textio/DavisTextOutputWriter.java) still streams text during play. |

### File → Save As (playback export)

Enabled only while a recording is open (`PlayMode.PLAYBACK`). Save As opens its own reader on a background low-priority thread so playback can continue or another file can be opened. Re-recording remains on the recording button.

- **AEDAT-4** (default): native DV-compatible `.aedat4` (events, frames, IMU). Preferred way to clip with IN/OUT or apply EventFilters. File → Save As stays enabled while an export runs, so another recording can be exported in parallel. An AEDAT-4 source is copied in **record order**: each EVTS, FRME, and IMUS packet is written as stored (same packet sizes and interleave). Save As does not reslice events into 8192-event windows or reassemble Davis APS from mixed samples. IN/OUT trims only the first/last EVTS packets that straddle the marks; Apply EventFilters rewrites EVTS packets only. AEDAT-2 and other playback formats still scan in 8192-event slices (and may assemble APS). Optional **lossy time bins** replace EVTS with jAER-only counts.
- **AEDAT-Z**: polarity only (`.aedz`). The same shift control coarsens timestamps and still writes every event. Frames and IMU stay out of the file (DAVIS can write HVS sidecars).
- **CSV / text** and **DSEC HDF5**: same scan; DAVIS/CDAVIS can add HVS sidecars.
- **Use IN and OUT markers** (default on): unset ends are file start / EOF.
- **Apply EventFilters** (default on): same chain as filtered re-recording.
- **HVS sidecars** (DAVIS / CDAVIS, CSV/HDF5 only): optional `<basename>-frames/` compressed PNGs + `timestamps.txt`, and `<basename>-imu.csv`.

Dialog: [`SaveAsExportDialog`](../src/net/sf/jaer/eventio/export/SaveAsExportDialog.java).

Playback IN, OUT, and other markers are stored as CSV under `${java.io.tmpdir}/jaer/markers/` and restored when the recording is reopened.

---

## Playback formats (detail)

| Format | Reader | Notes |
|--------|--------|--------|
| [AEDAT-4](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-4.0.html) | [`Aedat4FileInputStream`](../src/net/sf/jaer/eventio/aedat4/Aedat4FileInputStream.java) | Multi-camera EVTS stream selection; decompresses LZ4/ZSTD as needed. Lossy `LBEV` event packets expand back into normal polarity events. |
| AEDZ | [`AEDZInputStream`](../src/net/sf/jaer/eventio/AEDZInputStream.java) | Polarity only. A lossy timestamp shift is already applied in the stored times; the reader does not expand counts. |
| [AEDAT-1](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-1.0.html)/[2](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-2.0.html)/[3](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-3.1.html), legacy `.dat` | [`AEFileInputStream`](../src/net/sf/jaer/eventio/AEFileInputStream.java) | Version from `#!AER-DAT…` header line. Used for `.dat` only when the file is **not** Metavision DAT. |
| [Metavision DAT](https://docs.prophesee.ai/stable/data/file_formats/dat.html) | [`MetavisionDatFileInputStream`](../src/prophesee/eventio/MetavisionDatFileInputStream.java) | Peek: lines starting with `% ` (vs jAER `#` / raw AEDAT-1). CD / Event2d types `0` and `12` only (8-byte LE `t` + packed `x`/`y`/`p`). External-trigger DAT (`type 14`) is not played. Random-access seek; no index cache. |
| [Metavision RAW EVT3](https://docs.prophesee.ai/stable/data/file_formats/raw.html) | [`MetavisionRawFileInputStream`](../src/prophesee/eventio/MetavisionRawFileInputStream.java) | Same `Evt3Parser` as live USB ([EVT3](https://docs.prophesee.ai/stable/data/encoding_formats/evt3.html)). Seek index cached as `*.metavisionrawidx` in `${java.io.tmpdir}/jaer/aeidx/`. **EVT2 / Prophesee HDF5 not supported yet.** |
| [DSEC HDF5](https://dsec.ifi.uzh.ch/data-format/) | [`DsecHdf5AEInputStream`](../src/net/sf/jaer/eventio/dsec/DsecHdf5AEInputStream.java) | Single-camera cooked `events.h5` (left or right): pack via chip `getAddressFromCell`. Uses [jHDF](https://jhdf.io/) + [`BloscHdf5Filter`](../src/net/sf/jaer/eventio/dsec/BloscHdf5Filter.java) for Blosc/ZSTD. Chip from peeked size: `DVS640` (640×480) or `DVS1280x720SD` (1280×720). Stereo dual-stream later. **Save As** writes the same layout uncompressed via [`DsecHdf5AEOutputStream`](../src/net/sf/jaer/eventio/dsec/DsecHdf5AEOutputStream.java). |
| Event Planar HDF5 | [`DsecHdf5AEInputStream`](../src/net/sf/jaer/eventio/dsec/DsecHdf5AEInputStream.java) (`CookedLayout.EVENT_PLANAR`) | Same stream as DSEC, different columns: `/events/{xs,ys,ts,ps}` as in [`get_events`](https://github.com/tudelft/event_planar/blob/main/dataloader/h5.py). `ts` is Unix seconds (float64); player remaps to relative µs. `sensor_resolution=[240,180]` → `DAVIS240C`. Optional IMU/OptiTrack groups are not played. Needs [`ZstdHdf5Filter`](../src/net/sf/jaer/eventio/dsec/ZstdHdf5Filter.java) (HDF5 filter 32015). |
| [ROS bag](http://wiki.ros.org/Bags) | [`RosbagFileInputStream`](../src/net/sf/jaer/eventio/ros/RosbagFileInputStream.java) | Topics under `/dvs/`, `/davis/left/`, or `/samsung/camera/` headers. |
| Text | [`TextFileInputStream`](../src/net/sf/jaer/eventio/TextFileInputStream.java) | CSV/space-separated DVS lines; options for timestamp units and polarity. |
| Index | AEPlayer / SyncPlayer | `.aeidx` playlists still open. Multi-stream AEDAT-4: EVTS chooser can open one viewer or several (same file, different stream IDs). |

Chip auto-detect for recordings: [`RecordingChipDetector`](../src/net/sf/jaer/eventio/RecordingChipDetector.java) (filename token, AEDAT-4 `infoNode`, Metavision RAW / DAT header, cooked HDF5 layout, AEDAT-2 header). `.dat` with a `% ` header is Metavision DAT; other `.dat` still falls back to `DVS128`.

---

## Compression reference

| Format | Options | Where set |
|--------|---------|-----------|
| AEDAT-4 | `NONE` (0), `LZ4` (1, default), `LZ4_HIGH` (2), `ZSTD` (3), `ZSTD_HIGH` (4). Optional [lossy timestamp resolution](#lossy-timestamp-resolution) under that codec (not a sixth codec id). | Live recording: AEViewer recording prefs. Save As: File → Save As dialog. See [`CompressionType`](../src/net/sf/jaer/eventio/aedat4/dv/CompressionType.java), [`Aedat4Compression`](../src/net/sf/jaer/eventio/aedat4/Aedat4Compression.java), [`Aedat4LossyTimeBins`](../src/net/sf/jaer/eventio/aedat4/Aedat4LossyTimeBins.java) |
| AEDZ | Byte-plane zstd level 1. Optional [lossy timestamp shift](#lossy-timestamp-resolution) (every event kept). | Live recording format **AEDZ**, or File → Save As. See [`AEDZOutputStream`](../src/net/sf/jaer/eventio/AEDZOutputStream.java) |
| AEDAT-1/2/3, legacy `.dat` | None | — |
| Metavision `.dat` | None (decoded events; typically larger than RAW) | Exported by Metavision Studio / SDK (`File to DAT`) |
| Metavision `.raw` | None (sensor EVT3 encoding is the “compression”) | Recorded by Metavision Studio / SDK |
| ROS bag | ROS bag storage (not exposed in jAER UI) | — |
| Text CSV/TXT | None (optionally gzip outside jAER) | — |
| DSEC HDF5 (Save As) | Uncompressed contiguous datasets (jHDF 0.12 cannot write gzip/Blosc) | File → Save As |

---

## Lossy timestamp resolution

Off by default. Use this compression when you know that the source sensor timestamp resolution is excessive for your appliction, or that you know the timestamp jitter exceeeds the raw timestamp resoulution. By using this mode, many events can share the same lower-resolution timestamp, reducing file size significantly. This mode also includes the option to preserve or discard the source event **order**. Not preserving the order allows lumping all the events with the same timestamp, address, and polarity to  one slot with a byte count value.

The controls are in AEViewer **Preferences → File**, the Start recording dialog, and **File → Save As**. They do not add a DV compression id. The selected NONE / LZ4 / ZSTD codec still wraps AEDAT-4 payloads. AEDZ always uses its own byte-plane zstd.

Prefs on `AEViewer`: `aedat4LossyTimeBins` (false), `aedat4LossyTimeShift` (10), `aedat4LossyCollapsePolarities` (false). The shift spinner is **0..24** and is enabled only when lossy time bins are on. **10** drops the low 10 bits (1024 µs, about 1 ms). Restored time is `(t >>> shift) << shift`.

### AEDAT-4 time bins

Checkbox **Lossy time bins**. Polarity events that share the quantized time, pixel, and polarity become one count. Playback expands each count into that many normal jAER events, in first-seen order. On and Off stay separate. Frames and IMU stay exact FlatBuffers.

These event packets are **jAER-only** (magic `LBEV`, version 1). iniVation DV and older jAER will not parse them. The shift is stored in the infoNode node `jAERLossyTimeBins`, attribute `timeShiftBits`. `FileDataTable.numElements` is the expanded event count.

Uncompressed EVTS payload, then the chosen codec:

- Magic `LBEV`, version u16 = 1, bin count u32
- Each bin: restored Unix µs u64, record count u32, then records `x` u16, `y` u16, polarity u8, count u16
- A count above 65535 splits into back-to-back records for that pixel

The writer keeps one open bin and flushes it when the next event’s bin differs, or on close. Packet cadence stays the view-loop packet.

### Collapse On and Off

Second checkbox, AEDAT-4 only, enabled only when lossy time bins are on. Order inside the bin is not kept. One record holds both polarities of a pixel: `x` u16, `y` u16, On count u8, Off count u8 (`LBEV` version 2). Playback emits that pixel’s On events, then its Off events. Pixels stay in first-seen order. Each count is 0–255; a hotter pixel continues in the next record. The infoNode attribute is `collapsePolarities`.

AEDZ does not use this packing.

### AEDZ timestamp shift

The same shift control on an AEDZ recording or Save As. Every polarity event is still stored. Only the timestamp is quantized, so existing [`AEDZInputStream`](../src/net/sf/jaer/eventio/AEDZInputStream.java) playback shows the coarser times. The embedded AEDAT-2 header gains a comment `jAERLossyTimeShift=<bits>`. Frames and IMU are not in the `.aedz` file.

### One measured recording

`PropheseeIMX636HD-00050491_2026-10-07T14-06-35-0400-VCR_c0010.aedat4`: **182,787,226** polarity events, **1 h 0.12 s**, no frames, no IMU. The file on disk is already AEDAT-4 ZSTD (**1,034,468,740** bytes, 156,446 packets). The table rewrites those events in 65,536-event slices. AEDAT-4 rows use ZSTD. Shift is 10. All four outputs play back as the same event count. Collapse On and Off was not part of this run.

| Output | What is stored | File bytes | vs rewritten AEDAT-4 ZSTD |
|--------|----------------|----------:|--------------------------:|
| AEDAT-4 ZSTD | Every event, microsecond timestamps | 1,012,249,445 | 1.00 |
| AEDAT-4 ZSTD + 10-bit bins | Per-pixel polarity counts (`LBEV` v1) | 515,216,708 | 50.9% (1.96:1) |
| AEDZ | Every event, microsecond timestamps | 525,295,464 | 51.9% (1.93:1) |
| AEDZ + 10-bit timestamps | Every event, timestamps quantized to 1024 µs | 406,606,855 | 40.2% (2.49:1) |

The rewritten AEDAT-4 ZSTD file is slightly smaller than the original cassette because each packet holds 65,536 events. On this recording, coarsening AEDZ timestamps beat collapsing counts into AEDAT-4 bins: the timestamp byte planes become mostly zeros.

---

## Sample data links

Help → **Sample data** in AEViewer:

| Item | URL |
|------|-----|
| DAVIS346 AEDAT-2 samples | [DAVIS24 site](https://sites.google.com/view/davis24-davis-sample-data/home) |
| AEDAT-4 / DV samples | [MISTLab/event_based_data](https://github.com/MISTLab/event_based_data) |
| EvDownsampling multi-camera AEDAT-4 | [anindyaghosh/EvDownsampling](https://github.com/anindyaghosh/EvDownsampling#readme) (DAVIS346 + DVXplorer in one file; Figshare data) |
| Prophesee / Metavision samples | [Prophesee datasets](https://docs.prophesee.ai/stable/datasets.html#chapter-datasets) |
| Event Planar (DAVIS240C HDF5) | [tudelft/event_planar](https://github.com/tudelft/event_planar); dataset [10.34894/QTFHQX](https://doi.org/10.34894/QTFHQX); [project](https://mavlab.tudelft.nl/fully_neuromorphic_drone/); [Science Robotics paper](https://www.science.org/doi/full/10.1126/scirobotics.adi0591) |

Constants: [`JaerConstants`](../src/net/sf/jaer/JaerConstants.java).

---

## Related docs

- [Live camera server + Python dataloaders](README-DNN-OpenCV-ROS.md) — File → Remote (OpenCV, DNN mmap, ROS2) and reading AEDAT-4 / CSV / HDF5 in Python
- [jAER 3 pipeline](README-jaer3.md) — PacketBundle path and AEDAT-4 recording
- [Prophesee driver README](../src/prophesee/README.md) — EVK4 live + RAW EVT3 / DAT playback
- [USB live acquisition bench](usb-live-acquisition-bench.md)

## Muxed AEDAT-4 bench (2–8 live cameras)

Manual checks after File sync on, recording format AEDAT-4:

- Start/stop sync record: **one** `.aedat4`, `infoNode` has 3×N streams where N is
  **LIVE cameras** (idle WAITING windows are not tracks); no new `.aeidx`.
  Opening that file must **not** disable File sync; the EVTS chooser can spawn
  extra viewers.
- Disk: muxed size vs sum of old per-camera files (payload similar; one header/table).
- CPU: ViewLoop `recordPacket` lock on one `synchronized writeBundle` (FINE log / VisualVM).
- Open: EVTS dialog → one stream vs all viewers; seek/rewind with sync on.
- Reopen: index cache per stream (`*.s{id}.aedat4idx`); second open of the same stream should hit cache.
- Regression: single-camera record/play; open an old `.aeidx`.
- AEDAT-2 + sync still writes `.aeidx`.

Smoke: `java -cp build/classes;lib/*;jars/* net.sf.jaer.eventio.aedat4.Aedat4MultiStreamRoundtripDemo` (Unix classpath uses `:`).
