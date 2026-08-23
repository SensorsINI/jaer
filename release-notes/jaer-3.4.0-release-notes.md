<!--
  Paste-ready for GitHub Releases. Image links use raw.githubusercontent.com so they
  render in the Release body. Relative paths work in the repo, not in a Release description.

  Put the download table and short OS notes at the top. GitHub always appends its own
  Assets list at the bottom of the Release page — do not duplicate a long installer
  section there. Copy this layout into later jaer-*-release-notes.md and bump 3.4.0 / 3_4_0.

  Images: GFM has no size syntax. Use HTML <img src="..." alt="..." width="50%" />
    (GitHub allows width/height on img; it strips style=). Animated WebP loops in GitHub
    README and Release markdown when encoded with ffmpeg -loop 0.

  Screenshots still to capture into release-notes/3.4.0/:
    SplashScreen.png, Steadicam hemisphere view, HarmonicFilter resonator overlay,
    DVXplorer Mini/Micro live + IMU
-->

## Download

| You have | CPU | Download |
|---|---|---|
| Windows 10 / 11 | x64 | [jAER_windows-x64_3_4_0.exe](https://github.com/SensorsINI/jaer/releases/download/3.4.0/jAER_windows-x64_3_4_0.exe) |
| macOS | Apple Silicon (M1–M4) | [jAER_macos_aarch64_3_4_0.dmg](https://github.com/SensorsINI/jaer/releases/download/3.4.0/jAER_macos_aarch64_3_4_0.dmg) |
| macOS | Intel | [jAER_macos_3_4_0.dmg](https://github.com/SensorsINI/jaer/releases/download/3.4.0/jAER_macos_3_4_0.dmg) |
| Linux | x64 | [jAER_unix_3_4_0.sh](https://github.com/SensorsINI/jaer/releases/download/3.4.0/jAER_unix_3_4_0.sh) |

Each installer is ~200 MB and includes a bundled [Eclipse Temurin](https://adoptium.net/) JDK from Adoptium (same **25** LTS as 3.3.0) — you do not install Java yourself. OpenCV and JOGL natives are per-OS in the installer (the fat jar stays in git clones for `ant run`). GitHub lists the same files again under **Assets** at the bottom of this page. To clone and `ant run`, install [Adoptium JDK 25+](https://adoptium.net/) (`javac` still targets 21).

Video: [installing and updating jAER on YouTube](https://youtu.be/qQVt8_gwYVY) (also covers *git clone* and rebuild from master).

Installers are GitHub Release assets (since 3.2.0) and jAER can self-update (Help → Check for release updates… → **Download and install**). Older archival releases may remain on [Dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0).

### Linux

```bash
chmod +x jAER_unix_3_4_0.sh
sh jAER_unix_3_4_0.sh
```

Start jAER from the install directory or the desktop / GNOME entry the installer created. No official apt / `.deb` (USB cameras need an unsandboxed install). If USB udev rules are missing for a jAER device that is plugged in, jAER shows the exact shell command to add the rule(s).

---

### Windows

Download the `.exe` and run it. This build is not Authenticode-signed yet: SmartScreen may say *Windows protected your PC* — **More info** → **Run anyway** (and **Install anyway** if the installer also warns). USB cameras: if jAER reports `LIBUSB_ERROR_NOT_SUPPORTED`, bind **WinUSB** with [Zadig](https://zadig.akeo.ie/) (not libusb-win32). Prophesee EVK4 can use Prophesee **wdi-simple**.

### macOS

Apple menu → About This Mac: **Chip** Apple M1–M4 → `aarch64` DMG; **Processor** Intel → `jAER_macos_3_4_0.dmg` (no `aarch64` in the name). Terminal: `uname -m` is `arm64` or `x86_64`.

Open the DMG and run the installer. The build is unsigned; if macOS blocks it, [right-click → Open](https://support.apple.com/guide/mac-help/open-a-mac-app-from-an-unidentified-developer-mh40616/mac) (or Privacy & Security → **Open Anyway**). Prefer a user folder (`~/Applications` or `~/jaer`) if you do not want an admin install.

**Apple Silicon USB cameras** need Homebrew [libusb](https://formulae.brew.sh/formula/libusb): `brew install libusb`. If the dylib is missing, jAER shows a how-to and quits so the next launch can load it.


**jAER 3.4.0** is a feature release after **3.3.0** for **remote DNN/ROS output**, **DVXplorer Mini/Micro**, and **Save As**. File → Remote now has **DNN shared memory output…** (`DNNOutputViaSharedMemory`: Roshambo event-count frames or FireNet/E2VID event windows over mmap + localhost TCP) and **ROS2 / Foxglove frame output…** (`ROSOutput`: assembled DVS frames to ROS2 DDS and/or Foxglove Studio at `ws://127.0.0.1:8765` with no ROS2 install). **DVXplorer Mini and Micro** (CX3 MIPI) share the `DVXplorer` chip class with live DVS + BMI160 IMU. **Steadicam** uses that IMU path and adds a 180° hemisphere inpaint view. **File → Save As** parks playback during export, lists the EventFilter chain, and shows original vs saved stats with Playback. **HarmonicFilter** is rewritten for polarity events with local-phase gating and a slow-mo resonator overlay.

### Highlights

* **File → Remote → DNN shared memory output…** — one filter (`DNNOutputViaSharedMemory`) replaces the old mmap senders. Choose `outputMode`:
  * **EventCountFrames** — 64×64 uint8 histograms for [dextra-roshambo-python](https://github.com/SensorsINI/dextra-roshambo-python) (`consumer.py --jaer-mmap …`).
  * **EventWindows** — packed `(t, x, y, p)` windows for [rpg_e2vid](https://github.com/SensorsINI/rpg_e2vid) / FireNet (`uv run python live_reconstruction.py --network firenet --auto_hdr --display --show_events`; TCP default `127.0.0.1:14101`).
  Enable is a bold toggle at the top of the dialog (the Remote menu item only opens it). A canvas overlay shows mmap output while it is running. Default `flipY` is on for EventWindows (jAER lower-left → Python upper-left).

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/dnnoutput-menu.png" alt="File → Remote → DNN shared memory output" width="50%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/firenet.webp" alt="FireNet live reconstruction from jAER EventWindows mmap" width="80%" />

* **File → Remote → ROS2 / Foxglove frame output…** — `ROSOutput` assembles DVS frames (not the OpenGL pixmap) and publishes to **ROS2** (IHMC jros2 / Fast-DDS; no ROS2 install on the jAER machine) and/or **Foxglove Studio** over a local WebSocket. Foxglove: Open connection → Foxglove WebSocket → `ws://localhost:8765` → Image layout → topic `/jaer/event_count` (or time-surface / voxel). Frame types: **EventCountHistogram**, **TimestampImages**, **VoxelGrid** (B bins stacked as height = B×H on `/jaer/voxel_grid`). Place after denoisers to publish filtered events. Overlay shows Foxglove URL, client count, and frame count. Experimental.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/ros2-output1.png" alt="ROSOutput EventCountHistogram → Foxglove Studio" width="80%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/ros2-voxelgrid.png" alt="ROSOutput VoxelGrid (5 bins) in Foxglove" width="80%" />

* **DVXplorer Mini / Micro (CX3 MIPI)** is probably the smallest, lightest event cameras, and it also has a decent IMU. It now is fully supported in jAER. It shows live USB on the same `DVXplorer` chip class as the FX3 DVXplorer. MIPI DVS on EP 0x82, BMI160 IMU on EP 0x81 (rate-limited, demuxed to `ImuPacket` for AEDAT-4 and Steadicam). Next-gen SPI and bias sliders. Firmware ≥10 keeps device IMU defaults. Playback Y-orientation matches live capture.

* **File → Save As…** — which exports recordings as various other formats or allows you to greatly compress recordings by applying offline denoising and IN and OUT points, now parks the ViewLoop so it no longer races playback. The dialog lists enabled EventFilters from FilterFrame (**Open Filters…**); the recording overlay shows Apply Filters on/off. When finished, a confirmation shows original vs saved stats (events, duration, size, compression) with **Show folder**, **Playback**, and **OK**.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/save-as-progress-filters-applied.png" alt="Save As with Apply EventFilters and export progress" width="50%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/save-as-extended-info-confirmation-dialog.png" alt="Save As confirmation: original vs exported stats" width="50%" />

* **Steadicam hemisphere view** — Steadicam, whose basic function is derotating events using integrated 'vestibular' IMU gyro, now has  `hemisphereViewEnabled` which paints DVS events onto a world-fixed 180° `ImageDisplay` from IMU pose (pinhole / lensFOV mapping; high-passed gyros by default). Gray or RedGreen accumulation, fade, and clear. Enabling hemisphere raises `transformResetLimitDegrees` to at least 110° (restored when turned off) and can skip main-chip rendering with a canvas overlay. Clockwise roll warp matches the IMU camera convention. Verified live with DVXplorer Micro (~400 Hz IMU).

<!-- Steadicam hemisphere screenshot -->

* **HarmonicFilter** — which attempts to filter out events matching global resonator phase to filter out line frequency or LED lighting events, is rewritten for typed polarity events. ON/OFF drive the resonator in antiphase; `useLocalPhases` gates each pixel by its usual oscillator phase. Overlay is a slow-mo phase portrait (`orbitDisplayHz`, default 3 Hz) so a 100 Hz / 1 kHz lock is visible at 30 Hz render. Help (`?`) covers freq/Q/threshold tuning.

<!-- HarmonicFilter overlay screenshot -->

### Features

* **File → Remote**
  * Menu items **ROS2 / Foxglove frame output…** and **DNN shared memory output…** open nonmodal dialogs. Enable is inside each dialog (unique mnemonics on the Remote submenu).
  * `DNNOutputViaSharedMemory` merges the former `SharedMemoryDVSFrameSender` and `SharedMemoryEventWindowSender`. Cropping group for frameCut fields; obsolete Nullhop `normalizeDVSForZsNullhop` removed.
  * Changing `controlPort` while enabled rebinds the localhost TCP listener. Integer FilterPanel fields apply on focus-lost (not only Enter). Switching to EventWindows disposes the EventCountFrames histogram preview.

* **ROS2 / Foxglove (`ROSOutput`)**
  * Topics: `sensor_msgs/Image` 32FC1 (signed counts / microseconds / voxel weights) under `topicPrefix` (default `/jaer`). Foxglove encodings: Float32, Rgb8 (ON red / OFF green), Mono8.
  * Optional Foxglove `RawImage` `sequence` for drop detection (Studio’s Image panel ignores it; overlay uses the same counter).
  * `skipChipRendering` skips OpenGL pixmap updates while still publishing.

* **DVXplorer Mini / Micro**
  * Detected from USB `bcdDevice` (not a separate AEChip). Live DVS + IMU; gyro zero works. IMU samples stay off the DVS raw packet (`ADDRESS_TYPE_IMU`).
  * AEDAT-4 playback Y flip matches live Mini/Micro orientation.

* **Steadicam**
  * Dedicated microsecond clock for highpass; skips implausible IMU sample gaps. Roll sign: positive `getGyroRollZ()` is camera CW from the viewpoint (image rolls CCW). Documented on `IMUSample` / `TransformAtTime`.
  * Filters can skip AEViewer chip rendering via a canvas overlay message (`ChipCanvas`); annotations are skipped while skip is active except during AVI recording.

* **File → Save As**
  * Pauses playback and scans as fast as possible (preferred over re-recording to clip with IN/OUT or apply EventFilters).
  * Confirmation reuses **Show folder** / **Playback** / Recent Files.

* **EventFilter Help**
  * **F1** and **?** toggle Help when the filter controls have focus (and from the `?` button). Collapsing the panel still closes Help.

* **Developer / packaging**
  * install4j project, icon, and localization live under `install4j/` (Ant, `replace-installed-jar`, and releasing docs updated). Unused `build.cmd`, Dropbox-hardcoded `jaer.desktop`, and Eclipse `.classpath`/`.project` removed from git.
  * SignPath CI: updated test-signing cert and [remote signing workflow](README-releasing-tagging.md); Linux GitHub Release uploader fix.

### Bug fixes and minor improvements

* Fixed **Intel Arc crash** when opening a recent folder in the file dialog (`ChipDataFilePreview` no longer constructs a second `GLCanvas` beside the live viewer; `DrawGL` disables TextRenderer vertex arrays, matching `ChipCanvas`).
* Fixed **Y flip** in playback of DVXplorer Mini/Micro recordings.
* **DVXplorer Mini/Micro IMU**: CX3 debug endpoint no longer completes at USB poll rate (~100 kHz); one URB is resubmitted at ~800 Hz and samples go to a side queue.
* **ROS2/Foxglove** enable no longer re-enters the FilterPanel setter (property-change loop); sinks restart only when a sink flag actually changes.
* **DNN TCP** control server rebinds when `controlPort` changes while enabled.
* EventCountFrames preview window is disposed when switching to EventWindows.
* Reduced FilterPanel log chatter.

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/3.3.0...3.4.0
