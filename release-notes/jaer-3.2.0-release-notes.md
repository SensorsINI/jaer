<!--
  Paste-ready for GitHub Releases once these files are on GitHub (master or tag 3.2.0).
  Image links below use raw.githubusercontent.com so they render in the Release body.
  After you push tag 3.2.0, optionally change /master/ → /3.2.0/ in the image URLs for permanence.

  Relative paths (3.2.0/....png) also work when viewing this file in the repo on GitHub,
  but they do NOT work when pasted into a Release description — use the absolute URLs.

  Keep the Which installer / <a id="assets"></a> block last so it sits just above GitHub’s
  Assets list (per-OS install steps belong there, next to the download table).
  Copy it into later jaer-*-release-notes.md and bump 3.2.0 / 3_2_0.

  Images: GFM has no size syntax. Use HTML <img src="..." alt="..." width="50%" />
  (GitHub allows width/height on img; it strips style=). Copy that for later notes.
-->

Download jAER installers from [Assets](#assets) at the end of this page (~300 MB each; Java is bundled). **Windows, macOS, and Linux install steps** are in [Which installer to download](#assets), immediately above the file list. On a Mac, pick **Apple Silicon** vs **Intel** using the table there.

**Note: starting with this 3.2.0 release, releases are shared via GitHub Assets, and jAER can self-update via Download and Install.** Older archival releases may remain on [Dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0).

Installed jAER: Help → Check for release updates… → **Download and install**.

See video [installing and updating jaer on YouTube](https://youtu.be/qQVt8_gwYVY), which also shows how you can *git clone* and rebuild jAER with latest master-branch fixes from within jAER.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/SplashScreen.png" alt="jAER 3.2.0 splash" width="50%" />

**jAER 3.2.0** is a feature release for **live USB cameras**, **filter help**, and **file playback**. Live USB is **verified on Windows, macOS (including Apple Silicon), and Linux**. New: **EventFilter Help** (`?` on the filter panel), a **hello-world path to drive a Python DNN** (`SharedMemoryDVSFrameSender` + [dextra-roshambo-python](https://github.com/SensorsINI/dextra-roshambo-python)), **USB tuning** with per-camera FIFO/buffer defaults, **libusb hotplug** on Linux, **Homebrew libusb** how-to on Apple Silicon, **WinUSB install help** on Windows, a **Davis-style EVK4 bias panel**, **DVXplorer contrast-threshold / ReadoutFPS** control, and an EVK4 SuperSpeed stall fix. **File → Preferences → Export/Reset** exports, imports, or reverts the `/jaer` Preferences tree (quit and restart afterwards). Playback: **DSEC-layout HDF5** (play and **File → Save As**), **Metavision DAT** (`.dat` with `% ` header), **faster DV / AEDAT-4 LZ4**, more **DVS color modes**, **File → Export video** (Show folder / Play video), and an **Intel Arc OpenGL** fix when switching AEChips live.

### Highlights

* **EventFilter Help** — filters with an `@Help` annotation open a nonmodal HTML dialog the first time you expand their controls, and again from the **`?`** button. Links open in the system browser. Default-chain guides: **SpatioTemporalCorrelationFilter** (BA denoiser), **CellStatsProber**, **Info**, **HotPixelFilter**, **RotateFilter**, **XYTypeFilter**, **RefractoryFilter**, and **SharedMemoryDVSFrameSender**. **Steadicam** has a guide when you add it.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/help-example-STCF.png" alt="EventFilter Help — SpatioTemporalCorrelationFilter" width="50%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/help-example-cell-stats-prober.png" alt="EventFilter Help — CellStatsProber" width="50%" />

* **Hello world: jAER driving a Python DNN** — enable **SharedMemoryDVSFrameSender** (on the default filter list) to publish 64×64 DVS event-count frames over a memory-mapped file plus localhost TCP. Run [dextra-roshambo-python](https://github.com/SensorsINI/dextra-roshambo-python) `consumer.py --jaer-mmap … --serial_port None --windowed` to classify rock / scissors / paper. Sample throws: [Davis346 Roshambo](https://drive.google.com/file/d/1hEI4HMODwAu6Pm9P4oDecePbfv--Lwbg/view?usp=drive_link) (chip **Davis346blue**). CNN weights stay in Python; jAER does not load TensorFlow.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/roshambo-example.gif" alt="jAER SharedMemoryDVSFrameSender + Roshambo CNN" width="50%" />

* **Live USB on Windows, macOS, and Linux** — live USB cameras (DAVIS346 / DAVIS240, DVXplorer, DVS128, EVK4, NRV DELTA01, CDAVIS, cochleas) were verified on all three desktop OSes (macOS includes Apple Silicon). Linux: libusb **hotplug** wakes WAITING discovery immediately. Apple Silicon: Homebrew **libusb** is required (`brew install libusb`; `ant run` installs it when Homebrew is present). If the dylib is missing, jAER shows a how-to and quits so the next launch can load it. Windows: WinUSB via Zadig when libusb reports `LIBUSB_ERROR_NOT_SUPPORTED`. Camera list: [README Device hardware support](https://github.com/SensorsINI/jaer#device-hardware-support).

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/images/supported-cameras-annotated.jpg" alt="jAER supported cameras" width="50%" />

* **USB tuning** — USB → **USB tuning...** opens a modeless window (spinners, typed values, mouse wheel) for FIFO bytes, buffer count, AE render-packet size, and Prophesee **Live keep**. Values auto-apply after a short pause; **Requested** vs **Active** status shows when the reader has restarted. Per-camera defaults from live tuning: DVS128 / Davis240 **32 KiB × 4**, Davis346 **128 KiB × 4** (1.2M events), NRV **512 KiB × 16** (2M events), EVK4 **2 MiB × 4** (2M render/keep). **Reset USB interface** remains on the USB menu for recovery.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/usb-tuning.png" alt="USB tuning (EVK4 live)" width="50%" />

* **WinUSB required (Windows)** — if libusb reports `LIBUSB_ERROR_NOT_SUPPORTED`, jAER shows how to bind **WinUSB** with [Zadig](https://zadig.akeo.ie/) (not libusb-win32). Prophesee EVK4 can use Prophesee **wdi-simple** instead.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/missing_winusb_dialog.png" alt="Windows WinUSB driver required" width="50%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/zadig-winusb-install.png" alt="Zadig: install WinUSB for EVK4 (04B4:00F5)" width="50%" />

* **EVK4 user-friendly biases** — Hardware Configuration **User-Friendly Controls** for brightness-change threshold, ON/OFF balance, and pixel low/high-pass as additive tweaks around saved preferences (no claimed digital-to-physical mapping). SuperSpeed live path no longer stalls after ISSD start (do not `CLEAR_HALT` on 0x81 while async URBs are queued).

* **DVXplorer biases** — Hardware Configuration ON/OFF **contrast thresholds** 0–17 (default 9; smaller = more sensitive) and **ReadoutFPS** named modes, mapped like [dv-processing](https://gitlab.com/inivation/dv/dv-processing). Global hold (default on) and global reset (default off).

* **Preferences Export/Reset** — File → Preferences → **Export/Reset** exports or imports the entire `/jaer` Java Preferences tree (layout, last files, USB tuning, chip/filter settings). **Revert all preferences to defaults** warns and offers an XML backup first. Quit and restart afterwards; in-memory settings are not reloaded live.

* **Welcome overlay** — idle WAITING view shows version, File/Open, AEChip, and Help → Sample data. AEChip menu items show `@Description` tooltips. Help → **Release notes** opens GitHub Releases.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/welcome-overlay.png" alt="Welcome overlay (WAITING)" width="50%" />

* **DSEC-layout HDF5 playback** — open cooked `events.h5` / `.hdf5` with `/events/{p,t,x,y}`, `/ms_to_idx`, and `/t_offset` (Blosc + ZSTD via jHDF). Sensor size is peeked from HDF5 attributes or max `x`/`y` (not assumed VGA): **640×480 → `DVS640`**, **1280×720 → `DVS1280x720SD`**. Works with [DSEC](https://dsec.ifi.uzh.ch/) driving sequences and HD stereo kitchen recordings from [EventKitchen](https://chengmingf.github.io/EventKitchen.github.io/index.html). Drag-and-drop or File → Open; Esc cancels a queued jog and shows a wait cursor while slow seeks drain. DSEC `y=0` is top (image / UL origin); `p` is 0=off / 1=on. **File → Save As…** (`Ctrl+Shift+S`) writes the same layout from any playback file.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/dsec-h5-playback.png" alt="DSEC VGA events.h5 playback (DVS640)" width="50%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/eventkitchen-hdf5-playback.png" alt="EventKitchen HD LeftEvent.hdf5 playback (DVS1280x720SD)" width="50%" />

* **File → Save As…** (`Ctrl+Shift+S`, playback only) — offline export to CSV/text or DSEC-layout `.h5` (pauses playback; not AEDAT relogging). Optional **Use IN and OUT markers** and **Apply EventFilters** (both on by default). The default output name is `{open-recording}-export.h5` (or `.csv`) next to the source file. When finished: **Show folder** and **Play exported file** (opens the export in this AEViewer). DAVIS/CDAVIS can write sidecar `<basename>-frames/` PNGs and `<basename>-imu.csv`.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/save-as-h5.png" alt="File → Save As (DSEC HDF5)" width="50%" />

* **Slow LZ4 → optimized `-rerecord.aedat4`** — DV recordings often use dependent-block LZ4, which is much slower in jAER (~30× vs native independent-block LZ4). On open, jAER offers to create an optimized sibling copy next to the original (same folder; size estimate shown). Cancel is safe and leaves the viewer in a clean state.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/rerecord-offer-aedat4.png" alt="Slow LZ4 — create optimized copy?" width="50%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/rerecord-aedat-4-progress.png" alt="Re-recording LZ4 progress" width="50%" />

* **More DVS color modes** — **RedBlue** (ON blue / OFF red), **GrayTime** (time within slice on white), **HotCode** (event-count heatmap), and **WhiteBackground** (RedGreen on white), alongside existing ColorTime / RedGreen / gray modes.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.2.0/colormodes-mosaic.png" alt="DVS color modes mosaic" width="50%" />

### Features

* **Live USB**
  * USB tuning window; debounced stop/start of the reader so FIFO/buffer edits do not resize in-flight URBs.
  * Per-camera FIFO / buffer / render-packet (and EVK4 live-keep) defaults from live tuning.
  * Live cameras verified on **Windows, macOS (Apple Silicon and Intel), and Linux**.
  * Linux libusb hotplug pump while idle; Interface menu still lists FX2/FX3 / NRV / EVK4 by VID/PID. Windows WinUSB (bundled libusb 1.0.22) still falls back to periodic scans.
  * Apple Silicon: Homebrew libusb at `/opt/homebrew/opt/libusb/lib/libusb-1.0.0.dylib` (`brew install libusb`; `ant install-macos-libusb` / `ant run` when Homebrew is present). Missing dylib shows a how-to and quits.
  * WinUSB how-to dialog on Windows `LIBUSB_ERROR_NOT_SUPPORTED` (Zadig WinUSB; EVK4 may use wdi-simple).
  * EVK4 user-friendly bias tab (threshold, ON/OFF balance, `bias_fo` / `bias_hpf` offsets around last save).
  * Davis346 **red** / **redColor** use the same user-friendly Hardware Configuration panel as Blue.
  * DVXplorer Hardware Configuration: ON/OFF contrast thresholds 0–17 (default 9, smaller = more sensitive) and ReadoutFPS named modes, mapped like [dv-processing](https://gitlab.com/inivation/dv/dv-processing).
  * Assume **DVS128** when opening legacy `.dat` recordings (not Metavision DAT).
  * Treat **Davis346mini** recordings as **Davis346blue** in the chip detector.

* **EventFilter Help**
  * `@Help` HTML on `EventFilter` subclasses; nonmodal dialog on first expand and from the FilterPanel **`?`** button.
  * Clickable `href` links; shown-once remembered in Preferences.
  * Guides on default filters (plus Steadicam when added): SpatioTemporalCorrelationFilter, CellStatsProber, Info, HotPixelFilter, RotateFilter, XYTypeFilter, RefractoryFilter, SharedMemoryDVSFrameSender.

* **Python DNN hello world**
  * `SharedMemoryDVSFrameSender` publishes 64×64 uint8 event-count frames (mmap + localhost TCP JSON `HELLO` / `FRAME_READY`).
  * Consumer: [dextra-roshambo-python](https://github.com/SensorsINI/dextra-roshambo-python) `python consumer.py --jaer-mmap <mmapPath> --serial_port None --windowed`.
  * Default mmap: Linux/macOS `/tmp/jaer_dvs_frames.mmap`; Windows `%TEMP%\jaer_dvs_frames.mmap`.

* **Preferences**
  * File → Preferences → **Export/Reset**: export / import the `/jaer` Preferences tree for another computer or a later restore.
  * **Revert all preferences to defaults** (warning; offers export first). Quit and restart; in-memory settings are not updated live.

* **Playback / file formats**
  * **Metavision DAT** (`.dat` with `% ` header): decoded CD / Event2d; disambiguated from legacy jAER `.dat`. Chip from `% Width` / `% Height` (`PropheseeIMX636HD` or `DVS640`). See [`docs/README-file-formats.md`](../docs/README-file-formats.md).

* **DSEC / EventKitchen HDF5**
  * Playback of DSEC-format cooked event HDF5 (`events.h5`, `.hdf5`) with Blosc/ZSTD (incl. bitshuffle).
  * Auto AEChip hint from peeked geometry: `DVS640` (VGA) or `DVS1280x720SD` (HD).
  * Forward/reverse seek and jog; **Esc** cancels queued jog; wait cursor while jog is pending.
  * Datasets: [DSEC](https://dsec.ifi.uzh.ch/) (stereo driving, VGA event cameras); [EventKitchen](https://chengmingf.github.io/EventKitchen.github.io/index.html) (egocentric cooking, stereo Prophesee Gen4 HD).
  * **File → Save As…** (`Ctrl+Shift+S`, playback only): see dedicated item below.

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

* **File → Save As…** (`Ctrl+Shift+S`)
  * Playback-only offline export to CSV/text or DSEC-layout HDF5 (pauses the view loop; not AEDAT relogging).
  * **Use IN and OUT markers** and **Apply EventFilters** default on; CSV options include float-seconds timestamps.
  * Default filename is `{current-recording}-export.{ext}` in the recording’s folder (not the last export).
  * Finished dialog: **Show folder** and **Play exported file** (`AEPlayer.startPlayback` in this AEViewer).
  * DSEC write: uncompressed (jHDF 0.12); `y=0` top; `p` 0=off / 1=on; width/height attributes for reopen.
  * DAVIS / CDAVIS: optional `<basename>-frames/` PNGs and `<basename>-imu.csv`.

* **Help / samples**
  * Filter-panel **`?`** / first-activation `@Help` dialogs (see Highlights).
  * Help → **Release notes** (GitHub Releases).
  * Help → Sample data: **DVS09 / DVS128** sample files first ([Google Doc](https://docs.google.com/document/d/16b4H78f4vG_QvYDK2Tq0sNBA-y7UFnRbNnsGbD1jJOg/edit?tab=t.0)), then iniVation AEDAT-4 datasets (`https://release.inivation.com/?prefix=datasets/`) alongside existing DAVIS346 / MISTLab / Prophesee links.

* **Developer / JDK**
  * JDK 25: `-XX:+UseCompactObjectHeaders` and `--add-opens java.base/jdk.internal.loader=ALL-UNNAMED` so MLPNoiseFilter can hot-add the OS TensorFlow native jar.
  * Apple Silicon `ant run` / `ant compile` installs Homebrew libusb when missing (`-Dskip.macos.libusb=true` to skip).
  * SignPath Windows test-signing CI (`ant release-windows-ci` / tag `3.*`).
  * GitHub Releases as installer host (`updates.xml` baseUrl); install4j standalone **updater**; `scripts/upload-github-release-installers.ps1` and `scripts/prune-old-release-assets.ps1`. Winget/Homebrew templates under `packaging/`.

### Bug fixes and minor improvements

* Fixed **EVK4 SuperSpeed stall** after ISSD start (`CLEAR_HALT` on 0x81 while URBs were queued; Linux “EP not empty, refuse reset”).
* Serialized USB reader lifecycle: debounce buffer reconfig; bound EVK4 endpoint drains; stop sensor streaming before joining the Prophesee reader.
* Fixed leftover-prefs **startup / AEChip switch** so upgrades and hot-plug cameras work (merge new default chips into leftover menus, unique USB match without clearing Preferences, reset reused GL / mouse listeners).
* Fixed **Intel Arc** OpenGL crash on live AEChip switch (`ChipCanvas` / `AEViewer`).
* Fixed **NPE when panning after file close**; fixed **right-drag pan after mouse-wheel zoom**.
* Fixed Davis **IMU dt overlay of 0 ms** with USB PacketBundle demux.
* Kept **File** menu structure when the recent-files list is empty.
* Stopped **localhost UDP drops** for large AE packets; **TCP stream sockets** removed from the Remote menu (archival classes remain).
* Hardened open / re-record **cancel** so playback does not NPE after abort.
* Wait for AEViewer chip init before CLI / “Open with” file playback (avoids startup race).
* Warn when Hardware Config **Load** targets a different AEChip; Davis **autoExposureEnabled** now persists on Save.
* Exclude first-use UX keys from Hardware Config export; migrate `deviceSettings`.
* Improved HotCode and RedBlue color rendering.
* Removed unused **neuromorphic-drivers** crate (EVK4 path is native Java).
* Fixed DSEC HDF5 **Save As** storing jAER lower-left `y` (playback was upside down) and Davis polarity packing (ON events decoded as OFF).
* Fixed empty HDF5 close crash (jHDF 0.12 cannot write zero-length arrays) and zero-event export after seek-back (`NonMonotonicTimeException` on the first packet).

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/3.1.0...3.2.0

<a id="assets"></a>

## Which installer to download

GitHub lists the files under **Assets** immediately below this section ([Release Assets](https://github.com/SensorsINI/jaer/releases/tag/3.2.0#assets)). Each installer is ~300 MB and includes Eclipse Temurin 21 — you do not install Java yourself.

Video: [installing and updating jAER on YouTube](https://youtu.be/qQVt8_gwYVY).

| You have | CPU | Download |
|---|---|---|
| Windows 10 / 11 | x64 | [jAER_windows-x64_3_2_0.exe](https://github.com/SensorsINI/jaer/releases/download/3.2.0/jAER_windows-x64_3_2_0.exe) |
| macOS | Apple Silicon (M1, M2, M3, M4) | [jAER_macos_aarch64_3_2_0.dmg](https://github.com/SensorsINI/jaer/releases/download/3.2.0/jAER_macos_aarch64_3_2_0.dmg) |
| macOS | Intel | [jAER_macos_3_2_0.dmg](https://github.com/SensorsINI/jaer/releases/download/3.2.0/jAER_macos_3_2_0.dmg) |
| Linux | x64 | [jAER_unix_3_2_0.sh](https://github.com/SensorsINI/jaer/releases/download/3.2.0/jAER_unix_3_2_0.sh) |

### Windows

1. Download [jAER_windows-x64_3_2_0.exe](https://github.com/SensorsINI/jaer/releases/download/3.2.0/jAER_windows-x64_3_2_0.exe).
2. Run the installer. This 3.2.0 Windows build is not Authenticode-signed yet, so SmartScreen may say *Windows protected your PC*: click **More info**, then **Run anyway**. If the installer also warns, click **Install anyway**.
3. Finish the install4j wizard (Start Menu / desktop shortcut is created).
4. USB cameras: if jAER reports `LIBUSB_ERROR_NOT_SUPPORTED`, bind **WinUSB** with [Zadig](https://zadig.akeo.ie/) (not libusb-win32). Prophesee EVK4 can use Prophesee **wdi-simple** instead. Screenshots are in Highlights above.
5. Later updates: Help → Check for release updates… → **Download and install**.

### macOS

Apple menu → About This Mac. If **Chip** is Apple M1/M2/M3/M4, use the `aarch64` DMG. If **Processor** is Intel, use `jAER_macos_3_2_0.dmg` (no `aarch64` in the name). Terminal: `uname -m` is `arm64` (Apple Silicon) or `x86_64` (Intel).

1. Download the matching DMG from the table.
2. Open the DMG and run the jAER installer. The build is unsigned; if macOS blocks it, see [Open a Mac app from an unidentified developer](https://support.apple.com/guide/mac-help/open-a-mac-app-from-an-unidentified-developer-mh40616/mac): **right-click** the installer → **Open**, or System Settings → Privacy & Security → **Open Anyway**. You can also right-click the DMG → Open With → Archive Utility, then run the installer inside.
3. Prefer a **user folder** (for example `~/Applications` or `~/jaer`) rather than `/Applications` if you do not want an admin install.
4. **Apple Silicon only:** USB cameras (and jAER startup) need Homebrew [libusb](https://formulae.brew.sh/formula/libusb):

   ```bash
   brew install libusb
   ```

   If the dylib is missing, jAER shows a how-to and quits so the next launch can load it.
5. Later updates: Help → Check for release updates… → **Download and install**.

### Linux

1. Download [jAER_unix_3_2_0.sh](https://github.com/SensorsINI/jaer/releases/download/3.2.0/jAER_unix_3_2_0.sh).
2. Make it executable and run it:

   ```bash
   chmod +x jAER_unix_3_2_0.sh
   sh jAER_unix_3_2_0.sh
   ```

3. Start jAER from the installation directory or the desktop / GNOME menu entry the installer created.
4. Later updates: Help → Check for release updates… → **Download and install**.

No official apt / `.deb` is provided (USB cameras need an unsandboxed install).
