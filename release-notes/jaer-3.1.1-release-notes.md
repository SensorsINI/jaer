<!--
  Paste-ready for GitHub Releases once these files are on GitHub (master or tag 3.1.1).
  Image links below use raw.githubusercontent.com so they render in the Release body.
  After you push tag 3.1.1, optionally change /master/ → /3.1.1/ in the image URLs for permanence.

  Relative paths (3.1.1/....png) also work when viewing this file in the repo on GitHub,
  but they do NOT work when pasted into a Release description — use the absolute URLs.
-->

Go to [install4j jAER installers on Dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0) to download installers.

See video [installing and updating jaer on YouTube](https://youtu.be/qQVt8_gwYVY).

**jAER 3.1.1** is a patch release focused on **faster DV / AEDAT-4 LZ4 playback**: detect slow dependent-block LZ4, optionally re-record a sibling optimized copy, and open large recordings with less waiting. Also adds a **RedBlue** DVS color mode, **iniVation AEDAT-4 sample-data** Help links, and fixes an **Intel Arc OpenGL** crash when switching AEChips live.

### Highlights

* **Slow LZ4 → optimized `-rerecord.aedat4`** — DV recordings often use dependent-block LZ4, which is much slower in jAER (~30× vs native independent-block LZ4). On open, jAER offers to create an optimized sibling copy next to the original (same folder; size estimate shown). Cancel is safe and leaves the viewer in a clean state.

![Slow LZ4 — create optimized copy?](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.1.1/rerecord-offer-aedat4.png)

![Re-recording LZ4 progress](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.1.1/rerecord-aedat-4-progress.png)

* **Faster LZ4 decode / re-record** — hybrid framed LZ4 decoder (native lz4-java blocks; BlockLZ4 only where dependent continuations require it). Re-record writes independent-block LZ4 for snappy seek and playback.

* **RedBlue DVS color mode** — ON events blue, OFF events red (black background), alongside existing RedGreen / gray modes.

* **Intel Arc OpenGL fix** — live AEChip switch no longer crashes on some Intel Arc GPUs (reuse one `GLCanvas`, EDT-safe chip change, prefer remembered live AEChip at startup).

### Features

* **AEDAT-4 / DV playback**
  * Detect dependent-block LZ4 and offer sibling `-rerecord.aedat4` (Yes / No / Cancel).
  * Progress dialog during re-record; cancel returns to WAITING without getNextPacket NPEs.
  * Prefer **Davis346red** when mapping DV `DAVIS346_*` sources (colorFilter absent); experimental Davis346B deprecated for this path.
  * Richer open logging: compression and per-stream source / size / module from infoNode.
  * Multi-stream infoNode kept intact across re-record.

* **Display**
  * New **RedBlue** color mode in AEChip / Davis / multi-camera renderers.

* **Help / samples**
  * Help menu: iniVation release-repo AEDAT-4 datasets (`https://release.inivation.com/?prefix=datasets/`) alongside existing DAVIS346 / MISTLab / Prophesee links.

### Bug fixes and minor improvements

* Fixed **Intel Arc** OpenGL crash on live AEChip switch (`ChipCanvas` / `AEViewer`).
* Hardened open / re-record **cancel** so playback does not NPE after abort.
* Davis346blue default prefs: avoid persisting “prefs already loaded” so first-use import can still apply cleanly.
* Slight deviceSettings tidy for Davis346blue.

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/3.1.0...3.1.1
