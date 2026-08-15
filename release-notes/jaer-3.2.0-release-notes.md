<!--
  Paste-ready for GitHub Releases once these files are on GitHub (master or tag 3.2.0).
  Image links below use raw.githubusercontent.com so they render in the Release body.
  After you push tag 3.2.0, optionally change /master/ → /3.2.0/ in the image URLs for permanence.

  Relative paths (3.2.0/....png) also work when viewing this file in the repo on GitHub,
  but they do NOT work when pasted into a Release description — use the absolute URLs.
-->

Go to [install4j jAER installers on Dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0) to download installers. Choose the release folder corresponding to this release.

See video [installing and updating jaer on YouTube](https://youtu.be/qQVt8_gwYVY), which also shows how you can *git clone* and rebuild jAER with latest master-branch fixes from within jAER.

![jAER 3.2.0 splash](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/SplashScreen.png)

**jAER 3.2.0** is a feature release for **live USB cameras** and **file playback**. Live: **USB tuning** with per-camera FIFO/buffer defaults, **libusb hotplug** on Linux (DVS128 / DAVIS / NRV / EVK4 without a 3 s bus poll), **WinUSB install help** when Windows cannot open the camera, a **Davis-style EVK4 bias panel**, **DVXplorer contrast-threshold / ReadoutFPS** control (same mapping as DV), and an EVK4 SuperSpeed stall fix. **File → Preferences → Store** exports, imports, or reverts the `/jaer` Preferences tree (quit and restart afterwards). Playback: **DSEC-layout HDF5**, **faster DV / AEDAT-4 LZ4** (detect slow dependent-block LZ4 and optionally re-record), more **DVS color modes**, **File → Export video** (Show folder / Play video), and an **Intel Arc OpenGL** fix when switching AEChips live.

### Highlights

* **USB tuning** — USB → **USB tuning...** opens a modeless window (spinners, typed values, mouse wheel) for FIFO bytes, buffer count, AE render-packet size, and Prophesee **Live keep**. Values auto-apply after a short pause; **Requested** vs **Active** status shows when the reader has restarted. Per-camera defaults from live tuning: DVS128 / Davis240 **32 KiB × 4**, Davis346 **128 KiB × 4** (1.2M events), NRV **512 KiB × 16** (2M events), EVK4 **2 MiB × 4** (2M render/keep). **Reset USB interface** remains on the USB menu for recovery.

![USB tuning (EVK4 live)](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/usb-tuning.png)

* **Linux libusb hotplug** — plug/unplug wakes WAITING discovery immediately (FX2/FX3 cameras listed by VID/PID). Windows WinUSB (bundled libusb 1.0.22) still falls back to periodic scans.

* **WinUSB required (Windows)** — if libusb reports `LIBUSB_ERROR_NOT_SUPPORTED`, jAER shows how to bind **WinUSB** with [Zadig](https://zadig.akeo.ie/) (not libusb-win32). Prophesee EVK4 can use Prophesee **wdi-simple** instead.

![Windows WinUSB driver required](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/missing_winusb_dialog.png)

![Zadig: install WinUSB for EVK4 (04B4:00F5)](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/zadig-winusb-install.png)

* **EVK4 user-friendly biases** — Hardware Configuration **User-Friendly Controls** for brightness-change threshold, ON/OFF balance, and pixel low/high-pass as additive tweaks around saved preferences (no claimed digital-to-physical mapping). SuperSpeed live path no longer stalls after ISSD start (do not `CLEAR_HALT` on 0x81 while async URBs are queued).

* **DVXplorer biases** — Hardware Configuration ON/OFF **contrast thresholds** 0–17 (default 9; smaller = more sensitive) and **ReadoutFPS** named modes, mapped like [dv-processing](https://gitlab.com/inivation/dv/dv-processing). Global hold (default on) and global reset (default off).

* **Preferences Store** — File → Preferences → **Store** exports or imports the entire `/jaer` Java Preferences tree (layout, last files, USB tuning, chip/filter settings). **Revert all preferences to defaults** warns and offers an XML backup first. Quit and restart afterwards; in-memory settings are not reloaded live.

* **Welcome overlay** — idle WAITING view shows version, File/Open, AEChip, and Help → Sample data. AEChip menu items show `@Description` tooltips. Help → **Release notes** opens GitHub Releases.

![Welcome overlay (WAITING)](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/welcome-overlay.png)

* **DSEC-layout HDF5 playback** — open cooked `events.h5` / `.hdf5` with `/events/{p,t,x,y}`, `/ms_to_idx`, and `/t_offset` (Blosc + ZSTD via jHDF). Sensor size is peeked from HDF5 attributes or max `x`/`y` (not assumed VGA): **640×480 → `DVS640`**, **1280×720 → `DVS1280x720SD`**. Works with [DSEC](https://dsec.ifi.uzh.ch/) driving sequences and HD stereo kitchen recordings from [EventKitchen](https://chengmingf.github.io/EventKitchen.github.io/index.html). Drag-and-drop or File → Open; Esc cancels a queued jog and shows a wait cursor while slow seeks drain.

![DSEC VGA events.h5 playback (DVS640)](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/dsec-h5-playback.png)

![EventKitchen HD LeftEvent.hdf5 playback (DVS1280x720SD)](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/eventkitchen-hdf5-playback.png)

* **Slow LZ4 → optimized `-rerecord.aedat4`** — DV recordings often use dependent-block LZ4, which is much slower in jAER (~30× vs native independent-block LZ4). On open, jAER offers to create an optimized sibling copy next to the original (same folder; size estimate shown). Cancel is safe and leaves the viewer in a clean state.

![Slow LZ4 — create optimized copy?](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/rerecord-offer-aedat4.png)

![Re-recording LZ4 progress](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/rerecord-aedat-4-progress.png)

* **More DVS color modes** — **RedBlue** (ON blue / OFF red), **GrayTime** (time within slice on white), **HotCode** (event-count heatmap), and **WhiteBackground** (RedGreen on white), alongside existing ColorTime / RedGreen / gray modes.

![DVS color modes mosaic](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/colormodes-mosaic.png)

### Features

* **Live USB**
  * USB tuning window; debounced stop/start of the reader so FIFO/buffer edits do not resize in-flight URBs.
  * Per-camera FIFO / buffer / render-packet (and EVK4 live-keep) defaults from live tuning.
  * Linux libusb hotplug pump while idle; Interface menu still lists FX2/FX3 / NRV / EVK4 by VID/PID.
  * WinUSB how-to dialog on Windows `LIBUSB_ERROR_NOT_SUPPORTED` (Zadig WinUSB; EVK4 may use wdi-simple).
  * EVK4 user-friendly bias tab (threshold, ON/OFF balance, `bias_fo` / `bias_hpf` offsets around last save).
  * DVXplorer Hardware Configuration: ON/OFF contrast thresholds 0–17 (default 9, smaller = more sensitive) and ReadoutFPS named modes, mapped like [dv-processing](https://gitlab.com/inivation/dv/dv-processing).
  * Assume **DVS128** when opening legacy `.dat` recordings.

* **Preferences**
  * File → Preferences → **Store**: export / import the `/jaer` Preferences tree for another computer or a later restore.
  * **Revert all preferences to defaults** (warning; offers export first). Quit and restart; in-memory settings are not updated live.

* **DSEC / EventKitchen HDF5**
  * Playback of DSEC-format cooked event HDF5 (`events.h5`, `.hdf5`) with Blosc/ZSTD (incl. bitshuffle).
  * Auto AEChip hint from peeked geometry: `DVS640` (VGA) or `DVS1280x720SD` (HD).
  * Forward/reverse seek and jog; **Esc** cancels queued jog; wait cursor while jog is pending.
  * Datasets: [DSEC](https://dsec.ifi.uzh.ch/) (stereo driving, VGA event cameras); [EventKitchen](https://chengmingf.github.io/EventKitchen.github.io/index.html) (egocentric cooking, stereo Prophesee Gen4 HD).

* **AEDAT-4 / DV playback**
  * Hybrid framed LZ4 decoder (native lz4-java blocks; BlockLZ4 only where dependent continuations require it).
  * Detect dependent-block LZ4 and offer sibling `-rerecord.aedat4` (Yes / No / Cancel); re-record writes independent-block LZ4.
  * Progress dialog during re-record; cancel returns to WAITING without getNextPacket NPEs.
  * Prefer **Davis346red** when mapping DV `DAVIS346_*` sources (colorFilter absent).
  * Richer open logging: compression and per-stream source / size / module from infoNode.
  * Multi-stream infoNode kept intact across re-record.

* **Display**
  * New / completed DVS color modes in `AEChipRenderer` (and Davis / multi-camera paths): **RedBlue**, **GrayTime**, **HotCode**, **WhiteBackground**.
  * Welcome overlay when WAITING with no open hardware.
  * AEChip `@Description` tooltips in the AEChip menu.

* **Video export**
  * After **File → Export video** finishes (AVI and optional ffmpeg MP4), a confirmation offers **Show folder** and **Play video**.
  * Export still matches AEViewer target frame rate for smooth playback.

* **Help / samples**
  * Help → **Release notes** (GitHub Releases).
  * Help → Sample data: **DVS09 / DVS128** sample files first ([Google Doc](https://docs.google.com/document/d/16b4H78f4vG_QvYDK2Tq0sNBA-y7UFnRbNnsGbD1jJOg/edit?tab=t.0)), then iniVation AEDAT-4 datasets (`https://release.inivation.com/?prefix=datasets/`) alongside existing DAVIS346 / MISTLab / Prophesee links.

* **Developer / JDK**
  * JDK 25: `-XX:+UseCompactObjectHeaders` and `--add-opens java.base/jdk.internal.loader=ALL-UNNAMED` so MLPNoiseFilter can hot-add the OS TensorFlow native jar.
  * SignPath Windows test-signing CI (`ant release-windows-ci` / tag `3.*`).

### Bug fixes and minor improvements

* Fixed **EVK4 SuperSpeed stall** after ISSD start (`CLEAR_HALT` on 0x81 while URBs were queued; Linux “EP not empty, refuse reset”).
* Serialized USB reader lifecycle: debounce buffer reconfig; bound EVK4 endpoint drains; stop sensor streaming before joining the Prophesee reader.
* Fixed leftover-prefs **startup / AEChip switch** so upgrades and hot-plug cameras work (merge new default chips into leftover menus, unique USB match without clearing Preferences, reset reused GL / mouse listeners).
* Fixed **Intel Arc** OpenGL crash on live AEChip switch (`ChipCanvas` / `AEViewer`).
* Fixed **NPE when panning after file close**; fixed **right-drag pan after mouse-wheel zoom**.
* Stopped **localhost UDP drops** for large AE packets; **TCP stream sockets** removed from the Remote menu (archival classes remain).
* Hardened open / re-record **cancel** so playback does not NPE after abort.
* Wait for AEViewer chip init before CLI / “Open with” file playback (avoids startup race).
* Warn when Hardware Config **Load** targets a different AEChip; Davis **autoExposureEnabled** now persists on Save.
* Exclude first-use UX keys from Hardware Config export; migrate `deviceSettings`.
* Improved HotCode and RedBlue color rendering.
* Removed unused **neuromorphic-drivers** crate (EVK4 path is native Java).

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/3.1.0...3.2.0
