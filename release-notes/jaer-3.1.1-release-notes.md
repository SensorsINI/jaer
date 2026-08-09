<!--
  Paste-ready for GitHub Releases once these files are on GitHub (master or tag 3.1.1).
  Image links below use raw.githubusercontent.com so they render in the Release body.
  After you push tag 3.1.1, optionally change /master/ → /3.1.1/ in the image URLs for permanence.

  Relative paths (3.1.1/....png) also work when viewing this file in the repo on GitHub,
  but they do NOT work when pasted into a Release description — use the absolute URLs.
-->


Go to [install4j jAER installers on Dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0) to download installers. Choose the release folder corresponding to this release.

See video [installing and updating jaer on YouTube](https://youtu.be/qQVt8_gwYVY), which also shows how you can *git clone* and rebuild jAER with latest master-branch fixes from within jAER.

**jAER 3.1.1** adds **DSEC-layout HDF5 event playback** (classic [DSEC](https://dsec.ifi.uzh.ch/) VGA and HD exports such as [EventKitchen](https://chengmingf.github.io/EventKitchen.github.io/index.html)), plus a patch focused on **faster DV / AEDAT-4 LZ4 playback**: detect slow dependent-block LZ4, optionally re-record a sibling optimized copy, and open large recordings with less waiting. Also expands **DVS color modes** (RedBlue, GrayTime, HotCode, WhiteBackground), **iniVation AEDAT-4 sample-data** Help links, and fixes an **Intel Arc OpenGL** crash when switching AEChips live.

### Highlights

* **DSEC-layout HDF5 playback** — open cooked `events.h5` / `.hdf5` with `/events/{p,t,x,y}`, `/ms_to_idx`, and `/t_offset` (Blosc + ZSTD via jHDF). Sensor size is peeked from HDF5 attributes or max `x`/`y` (not assumed VGA): **640×480 → `DVS640`**, **1280×720 → `DVS1280x720SD`**. Works with [DSEC](https://dsec.ifi.uzh.ch/) driving sequences and HD stereo kitchen recordings from [EventKitchen](https://chengmingf.github.io/EventKitchen.github.io/index.html) (Prophesee Gen4 / EVK4-class exports in the same layout). Drag-and-drop or File → Open; Esc cancels a queued jog and shows a wait cursor while slow seeks drain.

![DSEC VGA events.h5 playback (DVS640)](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.1.1/dsec-h5-playback.png)

![EventKitchen HD LeftEvent.hdf5 playback (DVS1280x720SD)](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.1.1/eventkitchen-hdf5-playback.png)

* **Slow LZ4 → optimized `-rerecord.aedat4`** — DV recordings often use dependent-block LZ4, which is much slower in jAER (~30× vs native independent-block LZ4). On open, jAER offers to create an optimized sibling copy next to the original (same folder; size estimate shown). Cancel is safe and leaves the viewer in a clean state.

![Slow LZ4 — create optimized copy?](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.1.1/rerecord-offer-aedat4.png)

![Re-recording LZ4 progress](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.1.1/rerecord-aedat-4-progress.png)

* **Faster LZ4 decode / re-record** — hybrid framed LZ4 decoder (native lz4-java blocks; BlockLZ4 only where dependent continuations require it). Re-record writes independent-block LZ4 for snappy seek and playback.

* **More DVS color modes** — **RedBlue** (ON blue / OFF red), **GrayTime** (time within slice on white), **HotCode** (event-count heatmap), and **WhiteBackground** (RedGreen on white), alongside existing ColorTime / RedGreen / gray modes.

![DVS color modes mosaic](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.1.1/colormodes-mosaic.png)

* **Intel Arc OpenGL fix** — live AEChip switch no longer crashes on some Intel Arc GPUs (reuse one `GLCanvas`, EDT-safe chip change, prefer remembered live AEChip at startup).

### Features

* **DSEC / EventKitchen HDF5**
  * Playback of DSEC-format cooked event HDF5 (`events.h5`, `.hdf5`) with Blosc/ZSTD (incl. bitshuffle).
  * Auto AEChip hint from peeked geometry: `DVS640` (VGA) or `DVS1280x720SD` (HD).
  * Forward/reverse seek and jog; **Esc** cancels queued jog; wait cursor while jog is pending.
  * Datasets: [DSEC](https://dsec.ifi.uzh.ch/) (stereo driving, VGA event cameras); [EventKitchen](https://chengmingf.github.io/EventKitchen.github.io/index.html) (egocentric cooking, stereo Prophesee Gen4 HD).

* **AEDAT-4 / DV playback**
  * Detect dependent-block LZ4 and offer sibling `-rerecord.aedat4` (Yes / No / Cancel).
  * Progress dialog during re-record; cancel returns to WAITING without getNextPacket NPEs.
  * Prefer **Davis346red** when mapping DV `DAVIS346_*` sources (colorFilter absent); experimental Davis346B deprecated for this path.
  * Richer open logging: compression and per-stream source / size / module from infoNode.
  * Multi-stream infoNode kept intact across re-record.

* **Display**
  * New / completed DVS color modes in `AEChipRenderer` (and Davis / multi-camera paths): **RedBlue**, **GrayTime**, **HotCode**, **WhiteBackground**.
  * Fixed right-drag pan after mouse-wheel zoom.

* **Help / samples**
  * Help menu: iniVation release-repo AEDAT-4 datasets (`https://release.inivation.com/?prefix=datasets/`) alongside existing DAVIS346 / MISTLab / Prophesee links.

### Bug fixes and minor improvements

* Fixed **Intel Arc** OpenGL crash on live AEChip switch (`ChipCanvas` / `AEViewer`).
* Hardened open / re-record **cancel** so playback does not NPE after abort.
* Wait for AEViewer chip init before CLI / “Open with” file playback (avoids startup race).
* Davis346blue default prefs: avoid persisting “prefs already loaded” so first-use import can still apply cleanly.
* Slight deviceSettings tidy for Davis346blue.

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/3.1.0...3.1.1
