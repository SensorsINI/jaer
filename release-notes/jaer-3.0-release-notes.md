<!--
  Paste-ready for GitHub Releases once these files are on GitHub (master or tag 3.0.0).
  Image links below use raw.githubusercontent.com so they render in the Release body.
  After you push tag 3.0.0, optionally change /master/ → /3.0.0/ in the image URLs for permanence.

  Relative paths (3.0/....png) also work when viewing this file in the repo on GitHub,
  but they do NOT work when pasted into a Release description — use the absolute URLs.
-->

Go to [install4j jAER installers on Dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0) to download installers.

See video [installing and updating jaer on YouTube](https://youtu.be/qQVt8_gwYVY).

![jAER 3.0 splash](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.0/SplashScreen.png)

**jAER 3.0** is a major architectural release: the processing pipeline now follows the inivation DV-style model where each packet holds a **single typed stream** (polarity events, frames, IMU, …) carried in a time-ordered **`PacketBundle`**, with first-class **iniVation AEDAT-4** (FlatBuffers) logging and playback (optional LZ4/ZSTD compression). Live Davis capture, NRV/Prophesee camera support, and many UX features from the 2.8 line are carried forward and improved on this foundation.

### Highlights

* **Typed `PacketBundle` pipeline** — extract → filter → render → log on homogeneous packets (events / frames / IMU) instead of a single mixed APS+DVS packet. This reduces memory copy and event packet sizes dramatically because DVS events require only simpler PolarityEvent objects, not the the elaborated ApsDvsEvent from before. And the events/frames/IMU samples are extracted at the camera USB interface, not later on, again reducing internal memory and copying.
* **AEDAT-4 record & playback** — FlatBuffers `.aedat4` I/O with LZ4/ZSTD compression; Davis346 and NRV playback; compression ratio shown when recording stops; **Playback** from the save dialog.
* **File → Export video…** — capture the rendered AEViewer view to AVI, optional **ffmpeg** conversion to MP4, and cleanup of the intermediate AVI.
* **AEViewer Preferences** — recording format (AEDAT-4), compression, rendering, and playback options in one dialog.
* **Release automation** — root `VERSION.txt` + `ant release` (splash overlay, clean jar, `install4jc`).

![Davis346 live (events + APS + IMU)](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.0/davis346.png)

### Features

* **jAER 3.0 typed packet model**
  * `PacketBundle` / `TypedDataPacket` / `PacketType` APIs; Davis `extractBundle` demux into polarity, frame, and IMU packets.
  * `ViewLoop` wired to extract → filter → render on bundles (default for Davis live).
  * Filter refactors for typed bundles (e.g. Steadicam simplified to IMU).
  * Space-time rolling 3D view continues to show APS frames and DVS events together along time:

![Davis space-time 3D (APS frames + DVS events)](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.0/davis-3d.png)

* **AEDAT-4 (iniVation / FlatBuffers)**
  * `Aedat4FileOutputStream` / `Aedat4FileInputStream` with FlatBuffers schemas under `src/net/sf/jaer/eventio/aedat4/`.
  * Logging prefers `.aedat4`; compression selectable in Preferences (default **LZ4**, also **ZSTD**); payload compression ratio reported when logging stops.
  * Working record/playback for **Davis346**; NRV AEDAT-4 playback (address packing / sparse packet index fixes).
  * Prompt to **switch AEChip** when opening a recording whose chip does not match the current viewer chip.
  * Save confirmation includes **Show folder** and **Playback**:

![AEDAT-4 save dialog with ZSTD compression stats](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.0/AEDAT4-compression.png)

* **File → Export video…**
  * Dialog to record the rendered view (same path as JaerAviWriter): frame format, FPS, timecode, rewind / one-pass options.
  * **Convert to MP4 with ffmpeg after close**, auto-detect ffmpeg, delete intermediate AVI on success.
  * Menu: **Export video…** / **Stop video export**.

![Export video dialog](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.0/export-video.png)

* **AEViewer → Preferences…** — centralized prefs UI including **Recording format: AEDAT-4** and **AEDAT-4 compression**; Help links for iniVation / Prophesee / NRV camera docs.

![AEViewer Preferences (AEDAT-4 / ZSTD)](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.0/Preferences-dialog.png)

* **RpmMeter** (`EventFilter2DMouseROI`) — drag an ROI over a repeating feature; autocorrelation of ROI event rate → RPM / RPS (Hz); experimental, tuned to ~1 kHz.
* **NRV** — experimental pixel-bias panel (0x0160–0x016B); panel layout shrinks without clipping sliders; Davis346Blue default bias refresh.
* **Developer / release**
  * Root **`VERSION.txt`** as version SSOT; **`ant release`** (confirm → splash PNGs → sync `jaer.install4j` → clean + jar → `install4jc --release=…`); `ant generate-splash` for splash-only.
  * IDE / Ant Java 21 alignment; fast run scripts; Cursor/VS Code Java setup notes.

### Bug fixes and minor improvements

* Fixed AEViewer hang on exit during NRV AEDAT-4 playback.
* Fixed AEDAT-4 NRV playback address packing; sparse packet index for reliable NRV playback.
* Fixed Davis346 AEDAT-4 playback alongside LZ4/ZSTD compression support.
* Fixed File menu separators around recent files / Preferences.
* Fixed spike-sound visual indication rendering and scaling.
* Improved Info filter rate-trace scaling and per-type rate measurement.
* Davis346Blue live display restored on the jaer3 / PacketBundle path.
* Splash / install4j versioning no longer requires hand-editing Photoshop text for each release.

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/2.8.0...3.0.0

---

## Publishing these notes on GitHub

**Copy/paste (your plan)** — works well:

1. Commit and **push** `release-notes/3.0/*.png` and this markdown to `master` (images must exist at the raw URLs above before the Release page can show them).
2. Open the GitHub Release draft for `3.0.0`.
3. Paste everything **above** the `---` / “Publishing” section (or the whole body without this footer).
4. Optional: after the `3.0.0` tag exists, find/replace  
   `raw.githubusercontent.com/SensorsINI/jaer/master/` → `raw.githubusercontent.com/SensorsINI/jaer/3.0.0/`  
   so images stay pinned to the release tag.

**Why absolute URLs?** GitHub Release descriptions do **not** resolve relative paths like `3.0/foo.png` from the repo. They only render images with full `https://…` URLs (raw GitHub, release assets, or images you drag into the Release editor).

**Automate with GitHub CLI** (after installing [gh](https://cli.github.com/) and `gh auth login`):

```bash
# from repo root, after push + tag
gh release create 3.0.0 \
  --title "jaer-3.0.0" \
  --notes-file release-notes/jaer-3.0-release-notes.md
```

Strip this footer first (or keep a paste-only file without it) so the “Publishing” section is not part of the public notes. Attach installers separately if desired:

```bash
gh release upload 3.0.0 path/to/jAER_*.exe path/to/jAER_*.dmg path/to/jAER_*.sh
```

Dragging PNGs into the Release web editor also works (GitHub hosts them on `user-images.githubusercontent.com`); then you would not need raw repo URLs, but links would not live in this markdown file.
