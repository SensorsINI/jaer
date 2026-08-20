<!--
  Paste-ready for GitHub Releases. Image links use raw.githubusercontent.com so they
  render in the Release body. Relative paths work in the repo, not in a Release description.

  Put the download table and short OS notes at the top. GitHub always appends its own
  Assets list at the bottom of the Release page — do not duplicate a long installer
  section there. Copy this layout into later jaer-*-release-notes.md and bump 3.3.0 / 3_3_0.

  Images: GFM has no size syntax. Use HTML <img src="..." alt="..." width="50%" />
    (GitHub allows width/height on img; it strips style=).

  Screenshots still to capture into release-notes/3.3.0/:
    SplashScreen.png, save-as-aedat4.png, file-info.png, recording-overlay.png,
    aec-target-grey.png, already-running.png, logging-save-folders.png
-->

## Download

| You have | CPU | Download |
|---|---|---|
| Windows 10 / 11 | x64 | [jAER_windows-x64_3_3_0.exe](https://github.com/SensorsINI/jaer/releases/download/3.3.0/jAER_windows-x64_3_3_0.exe) |
| macOS | Apple Silicon (M1–M4) | [jAER_macos_aarch64_3_3_0.dmg](https://github.com/SensorsINI/jaer/releases/download/3.3.0/jAER_macos_aarch64_3_3_0.dmg) |
| macOS | Intel | [jAER_macos_3_3_0.dmg](https://github.com/SensorsINI/jaer/releases/download/3.3.0/jAER_macos_3_3_0.dmg) |
| Linux | x64 | [jAER_unix_3_3_0.sh](https://github.com/SensorsINI/jaer/releases/download/3.3.0/jAER_unix_3_3_0.sh) |

Each installer is ~200 MB and includes a bundled [Eclipse Temurin](https://adoptium.net/) JDK from Adoptium (this 3.3.0 media is **25.0.4**) — you do not install Java yourself. Startup is noticeably faster than 3.2.0’s Temurin 21 bundle. OpenCV and JOGL natives are per-OS in the installer (the fat jar stays in git clones for `ant run`). GitHub lists the same files again under **Assets** at the bottom of this page. To clone and `ant run`, install [Adoptium JDK 25+](https://adoptium.net/) (`javac` still targets 21).

Video: [installing and updating jAER on YouTube](https://youtu.be/qQVt8_gwYVY) (also covers *git clone* and rebuild from master).

Installers are GitHub Release assets (since 3.2.0) and jAER can self-update (Help → Check for release updates… → **Download and install**). Older archival releases may remain on [Dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0).

### Windows

Download the `.exe` and run it. This build is not Authenticode-signed yet: SmartScreen may say *Windows protected your PC* — **More info** → **Run anyway** (and **Install anyway** if the installer also warns). USB cameras: if jAER reports `LIBUSB_ERROR_NOT_SUPPORTED`, bind **WinUSB** with [Zadig](https://zadig.akeo.ie/) (not libusb-win32). Prophesee EVK4 can use Prophesee **wdi-simple**.

### macOS

Apple menu → About This Mac: **Chip** Apple M1–M4 → `aarch64` DMG; **Processor** Intel → `jAER_macos_3_3_0.dmg` (no `aarch64` in the name). Terminal: `uname -m` is `arm64` or `x86_64`.

Open the DMG and run the installer. The build is unsigned; if macOS blocks it, [right-click → Open](https://support.apple.com/guide/mac-help/open-a-mac-app-from-an-unidentified-developer-mh40616/mac) (or Privacy & Security → **Open Anyway**). Prefer a user folder (`~/Applications` or `~/jaer`) if you do not want an admin install.

**Apple Silicon USB cameras** need Homebrew [libusb](https://formulae.brew.sh/formula/libusb): `brew install libusb`. If the dylib is missing, jAER shows a how-to and quits so the next launch can load it.

### Linux

```bash
chmod +x jAER_unix_3_3_0.sh
sh jAER_unix_3_3_0.sh
```

Start jAER from the install directory or the desktop / GNOME entry the installer created. No official apt / `.deb` (USB cameras need an unsandboxed install). If USB udev rules are missing for a jAER device that is plugged in, jAER shows the exact shell command to add the rule(s).

---

<!-- Capture 3.3.0 splash after ant generate-splash / a running splash screenshot:
<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.3.0/SplashScreen.png" alt="jAER 3.3.0 splash" width="50%" />
-->

**jAER 3.3.0** is a major update after **3.2.0**: bundled Adoptium Temurin **25** and new splash screen speeds up startup by 4X, **AEDAT-4 export**, **Davis autoexposure**, and **OpenCV calibration**. **File → Save As** can write a DV-compatible `.aedat4` clip (IN/OUT, optional EventFilters) instead of re-recording. **File → Show file info** summarizes an open AEDAT-4 recording. Live **AEDAT-4 recording** applies EventFilters when that checkbox is on, and long files no longer jump backward every ~36 min. Davis **AEC** is a camera-style mean-to-target with a highlight clamp. **SingleCameraCalibration** uses typed Frame/Polarity packets, a DAVIS pixmap layout, and on-screen capture/calibrate feedback. **EventFilter Help** (`?`) now covers remaining **Stable** filters, including **PigTracker** and **PencilBalancer** on any DVS size. Recording to another folder uses the chooser directory, a recent-folders pulldown, and a **Moving recording** dialog that closes when the copy finishes. The filter panel **hides disabled filters** by default. Live view and AEDAT-4 recording reuse hot-path buffers so RSS no longer grows unbounded. Packaged installs load chips/filters from a **compile-time allowlist**. Installers ship per-OS OpenCV natives (shrunk installers to 200MB from 300MB), and use the **install4j native splash**. A **Recording** overlay (elapsed / remaining) can stay on the chip view while recording.

### Highlights

* **File → Save As… AEDAT-4** — playback-only export can write native DV-compatible `.aedat4` (events, frames, IMU) with **Use IN and OUT markers** and **Apply EventFilters** (both on by default). Compression is chosen in the dialog (LZ4 recommended). Corrupt or out-of-bounds events are skipped and counted. This is the preferred way to clip or filter a recording; re-recording is unchanged. CSV/text and DSEC HDF5 remain available.

<!-- save-as-aedat4.png -->

* **File → Show file info…** — during AEDAT-4 playback, a nonmodal dialog shows counts, duration (hours/minutes when over 1 h), and file size.

<!-- file-info.png -->

* **Recording overlay** — while recording, the chip view can show a transparent red **Recording** (or **Recording paused**) plus elapsed `XXhYYmZZs`, and total / remaining when a time limit is set. Toggle: File → Preferences → **Show recording overlay** (default on). File → Preferences itself is nonmodal and reused while the viewer is open.

<!-- recording-overlay.png -->

* **Davis autoexposure (AEC)** — Hardware Configuration scores exposure from the occupied analog DN range (not ADC full scale). Control is camera-style **mean-to-target** (midpoint of the low/high grey band) with a one-sided **highlight clamp** and settle frames after each exposure write. Defaults match `Davis346blue.xml` (AEC on, PID on, expDelta 0.5). AEC prefs persist on Hardware Config Save; Renew AEChip prompts first.

<!-- aec-target-grey.png -->

* **SingleCameraCalibration** — OpenCV chessboard calibration from DAVIS APS frames or accumulated DVS renderings. Typed Frame/Polarity packets, AEDAT-4 `FramePacket`s, `frameSource` Auto / ApsFrames / RenderedEventFrames, and on-screen capture/calibrate overlays (image count, focal length, principal point). Help (`?`) covers chessboard setup and sample recordings.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.3.0/SingleCameraCalibation.png" alt="SingleCameraCalibration (DAVIS346 chessboard)" width="50%" />

* **PigTracker** — Telluride 2010 line-segment object tracker is **Stable**. Each event near a segment updates a global homography (rotation, scale, optional shear/perspective); cyan overlay. Polarity is processed in place (unmatched events are no longer rewritten as Off). Geometry is any DVS size (verified Davis346). Help (`?`) includes the pig template PNG, [DVS09](https://sensors.ini.ch/datasets#h.3e6ntc261gha) samples, and **Reset Object**. Video: [YouTube](https://www.youtube.com/watch?v=yCOnDc5r7p8).

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.3.0/pigtracker.png" alt="PigTracker (DVS128 pig.aedat)" width="50%" />

* **PencilBalancer** — Conradt et al. ICCVW 2009 stereo DVS pencil/pole balancer. On a single DVS (file playback), both trackers share the same events so you can see line lock without stereo hardware. Help (`?`) covers [DVS09 Orientation stimulus](https://sensors.ini.ch/datasets#h.3e6ntc261gha), live stereo + USB servo table, and [doi:10.1109/iccvw.2009.5457625](https://doi.org/10.1109/iccvw.2009.5457625). Chip-size independent (verified Davis346). Related to PigTracker.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.3.0/pencil-balancer.png" alt="PencilBalancer line lock (DVS09 orientation stimulus)" width="50%" />

* **EventFilter Help** — remaining **Stable** filters have `@Help` (denoisers, trackers, AVI/text I/O, games, …): first expand and FilterPanel **`?`**. Relative classpath images next to the filter class (e.g. PigTracker `pig.png`) render in the dialog; `file:` links open with the system handler.

* **Recording save destination** — the save dialog uses the folder you are viewing (not a stale parent), adds a **recent-folders** pulldown, and **Show folder** / Recent Files match the saved file. Cross-filesystem copies show **Moving recording** only while the copy runs, then dismiss.

<!-- logging-save-folders.png -->

* **Already running?** — `JAERViewerRunning.txt` lives in `java.io.tmpdir` so installed copies can write it. On startup, if that file exists, jAER warns (possible running instance or unclean exit) and offers **Start anyhow** or **Cancel**. Leftover copies in the install/repo folder are deleted.

<!-- already-running.png -->

### Features

* **Recording terminology**
  * Sensor data capture is labeled **recording** (toolbar **Start recording**, File menu, Preferences) to distinguish it from console `java.util.logging`. The `l` shortcut is unchanged. Remote commands `startrecording` / `stoprecording` / `togglesyncrecording` are primary; `startlogging` / `stoplogging` / `togglesynclogging` remain as aliases.

* **File → Save As…** (`Ctrl+Shift+S`, playback only)
  * Native **AEDAT-4** export (default): DV-compatible `.aedat4`; IN/OUT clip; optional EventFilters; compression None / LZ4 / LZ4 high / ZSTD / ZSTD high.
  * CSV/text and DSEC-layout HDF5 unchanged; DAVIS/CDAVIS HVS sidecars still CSV/HDF5 only.
  * Skip corrupt or out-of-bounds events; report how many were dropped.
  * Re-recording (recording button) is unchanged.

* **File → Show file info…**
  * AEDAT-4 playback: nonmodal summary (counts, duration, size). Other formats stay disabled.

* **Live AEDAT-4 recording**
  * **Enable filtering of recorded or network output events** applies the EventFilter chain to `.aedat4` recordings (it previously omitted filtered events unless that checkbox was already on).
  * 32-bit camera timestamps are unwrapped so long recordings do not jump backward every ~36 min.

* **Display / Preferences**
  * **Show recording overlay** (default on): elapsed time while recording; total and remaining when a limit is set.
  * File → Preferences is nonmodal (Hide on close; reused for the viewer lifetime).
  * Filter panel **Hide disabled filters** defaults **on** (simpler view of enabled filters). File → Preferences → Filters still toggles it.
  * **XYTypeFilter** `lockSelections` defaults **on** so a stray click does not set a one-pixel ROI.

* **Davis AEC**
  * Target-grey from histogram mean vs midpoint of `[lowBoundary, highBoundary]` on the learned analog min–max DN range.
  * Highlight clamp: do not increase exposure while a high fraction of samples is in the clip band; shadows never force an increase.
  * After each exposure write, skip settle frames so the next decision uses the new integration.
  * ParameterControlPanel labels sized to the longest key; AEC description rewritten.

* **SingleCameraCalibration**
  * Typed packets and DAVIS pixmap layout for jAER 3 / AEDAT-4.
  * `frameSource` Auto / ApsFrames / RenderedEventFrames.
  * On-screen capture/calibrate feedback; `@Help` guide.

* **PigTracker / PencilBalancer**
  * **PigTracker** (`org.ine.telluride.jaer.tell2010.pigtracker`) is **Stable**; `processPolarity` updates the homography in place; optional `filterUnmatchedEvents` hides misses without flipping ON/OFF.
  * **PencilBalancer** (`ch.unizh.ini.jaer.projects.pencilbalancer`): `@Help` for playback vs live stereo + servo table; single-DVS playback feeds both X/Y trackers.
  * Both trackers use chip width/height instead of a hardcoded 128×128 (verified Davis346).

* **AEChip status**
  * **Stable**: DVXplorer, Prophesee EVK4 (`PropheseeIMX636HD`), NRV DELTA01 (`NRVS5KRC1S`).
  * **Experimental**: DAVIS240A / DAVIS240B (not produced).

* **EventFilter Help**
  * `@Help` on remaining Stable filters (BA / OrderN / Density / DoubleWindow denoisers, NoiseTesterFilter, RectangularClusterTracker, MedianTracker, AVI writers, Davis text I/O, LabyrinthGame, SlotCarRacer, FlexTimePlayer, …).
  * Help HTML document base is the filter class package so classpath PNGs render; `file:` links use `Desktop.open`.

* **Installer / OpenCV / splash**
  * `ant split-opencv-natives` packs only the current OS into 3.3.0 media (~220–230 MB). Git clones keep the openpnp fat jar for `ant run`.
  * Bundled JRE is Adoptium Temurin **25.0.4** (same as `ant run`; noticeably faster startup than 3.2.0’s Temurin 21). Ivy no longer retrieves `*-javadoc.jar` / `*-sources.jar` (~177 MB, including OpenCV sources and ant-commons-net sources). Installers also omit NetBeans source zips in `jars/` (`apache-ant-*-src.zip`, `jogamp-fat-java-src.zip`, usb4java sources).
  * Installed copies use the **install4j native splash** (`SplashScreen.writeMessage`); no Java/Swing overlay by default (`-Djaer.splashLogOverlay=true` restores it). ESC aborts during splash (Windows `GetAsyncKeyState`). Do not pass `-splash:` in install4j `vmParameters`.

* **Packaged class loading**
  * `ant compile` writes a compile-time allowlist of AEChip / EventFilter / DisplayMethod FQCNs into `jAER.jar`. Packaged installs (`install4j.appDir` or `jAER.jar`) load only those types; missing resource fail-closes (no directory classpath walk). Git/`ant run` still scans if the resource is absent; `-Djaer.scanClasspath=true` forces a rescan.
  * Customize stays populated if an allowlisted class fails to link (e.g. optional TensorFlow). TensorFlow native install adds only `tensorflow-core-native-…jar` from `~/.jaer/lib`.
  * `ant replace-installed-jar` copies `dist/jAER.jar` over an existing install4j tree (Windows UAC elevation when needed).

* **Help / FOV / Ant**
  * Event-camera FOV calculator lives in [`SensorsINI/lensFOV`](https://github.com/SensorsINI/lensFOV) ([GitHub Pages](https://sensorsini.github.io/lensFOV/)).
  * `ant help` / `ant -p` prints developer targets.

### Bug fixes and minor improvements

* Fixed **recording save** when the destination is a different folder (`JFileChooser` parent vs current directory); **Show folder** and Recent Files now match the saved file.
* Dismiss **Moving recording** when the copy finishes; recent-folders pulldown on the save dialog.
* Live `.aedat4` recording now writes **EventFilter output** when recording-filtered is enabled.
* Unwrapped **32-bit AEDAT-4 timestamps** so seeks and duration stay monotonic past ~36 min.
* **JAERViewerRunning.txt** moved to `java.io.tmpdir`; warn on leftover semaphore; delete leftovers in the install/repo folder.
* AEC panel layout: clamp ParameterControlPanel labels; score under/over from occupied histogram min/max, not ADC full scale.
* Persist AEC prefs on Hardware Config Save; prompt before **Renew AEChip**.
* Hide-disabled-filters default **true** for a simpler first filter list.
* Fixed **File → Open preview** while browsing (closed-preview NPE, leftover APS frames, AEDAT-4 accessory playback; csv/bag/h5 show summaries without video).
* Skip **wedged Dropbox/UNC folders** on startup (`File.exists()` timeout; fall back to tmpdir; drop unreachable Recent Files after the first failure). ESC (and Abort) can quit during splash.
* Failed **libusb open** no longer leaves a Java `DeviceHandle` with a null native pointer (`IllegalStateException` in ViewLoop on DVS128 hot-plug).
* Stopped **live-view and AEDAT-4 recording RSS growth** (reuse LZ4 compressor buffers, FlatBuffer/event arrays, cached TextRenderers; skip `TextRenderer.getBounds` for left-aligned overlay strings).
* **HotPixelFilter** identifies pixels by `x,y,polarity` so typed USB events with address 0 are not all dropped.
* **Recording time limit**: Cancel on the dialog no longer crashes; applying a limit updates an in-progress recording.
* **RectangularClusterTracker** skips unzoom when `AEViewer` is unset so **DavisAutoShooter** can load at startup.

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/3.2.0...3.3.0
