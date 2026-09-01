<!--
  Paste-ready for GitHub Releases. Still screenshots that are already in git use
  raw.githubusercontent.com so they render in the Release body. Relative paths work
  in the repo, not in a Release description.

  Put the download table and short OS notes at the top. GitHub always appends its own
  Assets list at the bottom of the Release page — do not duplicate a long installer
  section there. Copy this layout into later jaer-*-release-notes.md and bump 3.4.0 / 3_4_0.

  Images: GFM has no size syntax. Use HTML <img src="..." alt="..." width="50%" />
  (GitHub allows width/height on img; it strips style=). Animated WebP loops in GitHub
  README and Release markdown when encoded with ffmpeg -loop 0.

  Looping clips (gitignored .webp next to gitignored source .mp4): HTML comment
    <!-- webp: 3.4.0/foo.webp -->
  After ant upload-release-notes-media (not landed yet), the next line is
    <img src="https://github.com/user-attachments/assets/<uuid>" alt="…" width="80%" />
  Until then leave the comment as a placeholder. Do not upload the source mp4s.
  Encode locally (800px, ~15 fps, few seconds, -loop 0, <10 MB), e.g.:
    ffmpeg -y -i "release-notes/3.4.0/8-cams-startup-cellphone-video.mp4" -t 5 -vf "scale=800:-2:flags=lanczos,fps=15" -loop 0 -an -q:v 60 "release-notes/3.4.0/8-cams-startup.webp"
  firenet.webp is already committed (raw.githubusercontent.com); do not gitignore it.

  Encode from Dropbox-only mp4s:
    8-cams-startup-cellphone-video.mp4 → 8-cams-startup.webp          (prove-clip)
    8-event-cams-startup.mp4            → 8-event-cams-startup.webp     (optional desktop take)
    8-event-cams-12pc-cpu-load.mp4      → 8-event-cams-12pc-cpu-load.webp
    dvxplorer-micro-steadicam.mp4       → dvxplorer-micro-steadicam.webp
    jaer-zoom-2026-08-28_11.17.56.mp4   → jaer-zoom.webp
    firenet.mp4                         → already firenet.webp

  Still screenshots to capture into release-notes/3.4.0/:
    SplashScreen.png, dnnoutput-menu.png, OpenCV Remote dialog,
    muxed AEDAT-4 multi-viewer, DVXplorer Mini/Micro live + IMU, file-dialog preview,
-->

**jAER 3.4.0** is a big feature release after **3.3.0**. See [Highlights](#highlights) below.

## Download

| You have | CPU | Download |
|---|---|---|
| Windows 10 / 11 | x64 | [jAER_windows-x64_3_4_0.exe](https://github.com/SensorsINI/jaer/releases/download/3.4.0/jAER_windows-x64_3_4_0.exe) |
| macOS | Apple Silicon (M1–M4) | [jAER_macos_aarch64_3_4_0.dmg](https://github.com/SensorsINI/jaer/releases/download/3.4.0/jAER_macos_aarch64_3_4_0.dmg) |
| macOS | Intel | [jAER_macos_3_4_0.dmg](https://github.com/SensorsINI/jaer/releases/download/3.4.0/jAER_macos_3_4_0.dmg) |
| Linux | x64 | [jAER_unix_3_4_0.sh](https://github.com/SensorsINI/jaer/releases/download/3.4.0/jAER_unix_3_4_0.sh) |

Each installer is ~200 MB and includes a bundled [Eclipse Temurin](https://adoptium.net/) JDK from Adoptium (same **25** LTS as 3.3.0) — you do not install Java yourself. OpenCV and JOGL natives are per-OS in the installer (the fat jar stays in git clones for `ant run`). GitHub lists the same files again under **Assets** at the bottom of this page. To clone and `ant run`, install [Adoptium JDK 25+](https://adoptium.net/) (`javac` target 25).

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


---

### Highlights

* [Robust multicamera USB autobinding, recording, and playback](#eight-usb-cameras) — mixed-set autobind and hotplug
* [Quicker startup](#quicker-startup) — 3.5X quicker startup
* [F1 Quick help](#f1-quick-help) — searchable shortcuts
* [Save As](#save-as) — parked export with filters, IN/OUT, and stats
* [Give Feedback](#give-feedback) — anonymous feedback for new features and camera support, bugs/annoyances, praise
* [DVXplorer Mini / Micro](#dvxplorer-mini-micro) — 6480x480 inivation/Samsung DVS, Micro with IMU for low-latency derotation
* [DNN shared memory](#dnn-shared-memory) — File → Remote mmap to Roshambo / FireNet
* [ROS2 / Foxglove](#ros2-foxglove) — DVS frames, no ROS2 install
* [OpenCV camera output](#opencv-camera-output) — HTTP MJPEG; Linux Zoom / Cheese webcam
* [Muxed AEDAT-4](#muxed-aedat-4) — one file per session, synced multi-viewer playback
* [Features](#features) · [Bug fixes](#bug-fixes-and-minor-improvements)

<h4 id="eight-usb-cameras">Robust multicamera USB autobinding, recording, and playback</h4>

Autobind and hotplug were rebuilt so a mixed set (DVS128, DAVIS240/346, DVXplorer classic + Micro, EVK4, NRV DELTA01) can come up together. **Ctrl+Shift+U** refreshes USB and auto-opens a sole camera; Interface → **Refresh** finds a second camera while one is already open. Windows discovery decays 1 s → 3 s → 15 s after the last plug/unplug. The AEChip menu is now **Sensor**.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/8-cams-devmgmt.png" alt="Windows Device Manager: eight event cameras bound as WinUSB" width="50%" />

<!-- webp: 3.4.0/8-cams-startup.webp -->
<!-- encode from 8-cams-startup-cellphone-video.mp4 (prove-clip); optional desktop take: 8-event-cams-startup.webp -->

<!-- webp: 3.4.0/8-event-cams-12pc-cpu-load.webp -->
<!-- encode from 8-event-cams-12pc-cpu-load.mp4 -->

<h4 id="quicker-startup">Quicker startup</h4>

jAER (at least on Window 11) starts up 3.5X quicker. The viewer window paints before USB enumeration and JOGL (`windowOpened` 0.75 s vs 2.62 s idle). Status reads “Starting sensor / OpenGL…” until the chip is ready. Open-file accessory now plays a short AWT preview of AEDAT-4/2 (no second `GLCanvas`; Intel Arc-safe) and aborts the previous file when you scroll.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/3x-quicker-startup.png" alt="Startup profile: windowOpened 0.75 s vs 2.62 s" width="80%" />

<!-- file-dialog preview screenshot -->

<h4 id="f1-quick-help">F1 Quick help</h4>

Help → **Quick help/Shortcuts** (F1) opens a searchable shortcuts page (type to filter, F3 / Shift+F3 cycles). Title-bar close asks new users to confirm exit (**Don't show again** defaults on).

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/quickhelp.png" alt="Help → Quick help/Shortcuts (F1)" width="80%" />

<h4 id="save-as">Save As</h4>

**File → Save As…** exports recordings as various other formats or lets you compress them by applying offline denoising and IN and OUT points. It now parks the ViewLoop so it no longer races playback. The dialog lists enabled EventFilters from FilterFrame (**Open Filters…**); the recording overlay shows Apply Filters on/off. When finished, a confirmation shows original vs saved stats (events, duration, size, compression) with **Show folder**, **Playback**, and **OK**.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/save-as-progress-filters-applied.png" alt="Save As with Apply EventFilters and export progress" width="50%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/save-as-extended-info-confirmation-dialog.png" alt="Save As confirmation: original vs exported stats" width="50%" />

<h4 id="give-feedback">Give Feedback</h4>

Help → **Give feedback…** opens an anonymous Google Form (no account needed) for feature ideas, camera support, bugs/annoyances, and praise. For a crash or a bug with logs, use Help → **File Issue on Github** instead (version, OS, and a **Show folder** button). First run of a new version can offer these release notes.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/give-feedback-poll.png" alt="Help → Give feedback… anonymous form" width="50%" />

<h4 id="dvxplorer-mini-micro">DVXplorer Mini / Micro</h4>

Among the smallest event cameras, with a usable IMU. Live USB on the same `DVXplorer` chip class as the FX3 DVXplorer. MIPI DVS on EP 0x82, BMI160 IMU on EP 0x81 (rate-limited, demuxed to `ImuPacket` for AEDAT-4 and Steadicam). Next-gen SPI and bias sliders. Firmware ≥10 keeps device IMU defaults. Playback Y-orientation matches live capture. Classic FX3 and CX3 Micro are split for USB so both can be open at once.

<!-- DVXplorer Mini/Micro live + IMU screenshot -->

<!-- webp: 3.4.0/dvxplorer-micro-steadicam.webp -->
<!-- encode from dvxplorer-micro-steadicam.mp4 -->

<h4 id="dnn-shared-memory">DNN shared memory</h4>

**File → Remote → DNN shared memory output…** — one filter (`DNNOutputViaSharedMemory`) replaces the old mmap senders. Choose `outputMode`:

* **EventCountFrames** — 64×64 uint8 histograms for [dextra-roshambo-python](https://github.com/SensorsINI/dextra-roshambo-python) (`consumer.py --jaer-mmap …`).
* **EventWindows** — packed `(t, x, y, p)` windows for [rpg_e2vid](https://github.com/SensorsINI/rpg_e2vid) / FireNet (`uv run python live_reconstruction.py --network firenet --auto_hdr --display --show_events`; TCP default `127.0.0.1:14101`).

Enable is a bold toggle at the top of the dialog (the Remote menu item only opens it). A canvas overlay shows mmap output while it is running. Default `flipY` is on for EventWindows (jAER lower-left → Python upper-left).

<!-- dnnoutput-menu.png — File → Remote → DNN shared memory output -->

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/firenet.webp" alt="FireNet live reconstruction from jAER EventWindows mmap" width="80%" />

<h4 id="ros2-foxglove">ROS2 / Foxglove</h4>

**File → Remote → ROS2 / Foxglove frame output…** — `ROSOutput` assembles DVS frames (not the OpenGL pixmap) and publishes to **ROS2** (IHMC jros2 / Fast-DDS; no ROS2 install on the jAER machine) and/or **Foxglove Studio** over a local WebSocket. Foxglove: Open connection → Foxglove WebSocket → `ws://localhost:8765` → Image layout → topic `/jaer/event_count` (or time-surface / voxel). Frame types: **EventCountHistogram**, **TimestampImages**, **VoxelGrid** (B bins stacked as height = B×H on `/jaer/voxel_grid`). Place after denoisers to publish filtered events. Overlay shows Foxglove URL, client count, and frame count. Experimental.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/ros2-output1.png" alt="ROSOutput EventCountHistogram → Foxglove Studio" width="80%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/ros2-voxelgrid.png" alt="ROSOutput VoxelGrid (5 bins) in Foxglove" width="80%" />

<h4 id="opencv-camera-output">OpenCV camera output</h4>

**File → Remote → OpenCV camera output…** — `OpenCVOutput` publishes the chip view as HTTP MJPEG (`cv2.VideoCapture("http://127.0.0.1:8090/video.mjpg")`). On Linux, **publishV4l2** (VGA MJPEG on `/dev/video10`) makes Cheese, Zoom, and Google Meet see a **jAER** webcam; use [`scripts/cheese-jaer.sh`](https://github.com/SensorsINI/jaer/blob/master/scripts/cheese-jaer.sh) (stock Cheese PipeWire will not negotiate). Overlay must show `/dev/video10 open MJPEG`. Experimental.

<!-- OpenCV Remote dialog screenshot -->

<!-- webp: 3.4.0/jaer-zoom.webp -->
<!-- encode from jaer-zoom-2026-08-28_11.17.56.mp4 -->

<h4 id="muxed-aedat-4">Muxed AEDAT-4</h4>

Synchronized live capture writes **one** `.aedat4` with EVTS/FRME/IMUS streams per camera (basenames from chip class + USB serial). Opening that file in several AEViewers turns File sync on; the slider scrubs every player to the nearest packet timestamp. AEDAT-2/AEDZ sync still uses `.aeidx`. **Ctrl+W** only stops recording (stop-all when synchronized) and no longer unbinds cameras mid-record. Opening such AEDAT-4 recording offers to open all necessary viewr windows.

<!-- muxed AEDAT-4 multi-viewer screenshot -->

### Features

* **File → Remote**
  * Menu items **ROS2 / Foxglove frame output…**, **DNN shared memory output…**, and **OpenCV camera output…** open nonmodal dialogs. Enable is inside each dialog (unique mnemonics on the Remote submenu).
  * `DNNOutputViaSharedMemory` merges the former `SharedMemoryDVSFrameSender` and `SharedMemoryEventWindowSender`. Cropping group for frameCut fields; obsolete Nullhop `normalizeDVSForZsNullhop` removed.
  * Changing `controlPort` while enabled rebinds the localhost TCP listener. Integer FilterPanel fields apply on focus-lost (not only Enter). Switching to EventWindows disposes the EventCountFrames histogram preview.
  * RemoteControls are optional, with Help.

* **ROS2 / Foxglove (`ROSOutput`)**
  * Topics: `sensor_msgs/Image` 32FC1 (signed counts / microseconds / voxel weights) under `topicPrefix` (default `/jaer`). Foxglove encodings: Float32, Rgb8 (ON red / OFF green), Mono8.
  * Optional Foxglove `RawImage` `sequence` for drop detection (Studio’s Image panel ignores it; overlay uses the same counter).
  * `skipChipRendering` skips OpenGL pixmap updates while still publishing.

* **OpenCV (`OpenCVOutput`)**
  * Frame sources: Auto / RenderedPixmap, ApsFrames, DvsEventCount. `outputSize` Native or standard (VGA for V4L2). `flipY` default on.
  * Linux v4l2loopback: `v4l2loopback devices=1 video_nr=10 card_label=jAER exclusive_caps=1`. Do not QUERYCAP the node before jAER has `S_FMT`. Zoom/Chrome: rescan PipeWire after overlay shows `open`.

* **DVXplorer Mini / Micro**
  * Detected from USB `bcdDevice` (not a separate AEChip). Live DVS + IMU; gyro zero works. IMU samples stay off the DVS raw packet (`ADDRESS_TYPE_IMU`).
  * AEDAT-4 playback Y flip matches live Mini/Micro orientation.
  * USB link speed is shown on ChipCanvas and the Interface menu. Hardware Configuration documents DVXplorer ReadoutFPS modes.

* **Steadicam** — IMU derotation; `hemisphereViewEnabled` paints a 180° world-fixed inpaint view (pinhole / lensFOV; verified on DVXplorer Micro). Disabled on chips without IMU.
* **HarmonicFilter** — rewritten for polarity events; `useLocalPhases` gates line-frequency / LED events; slow-mo resonator overlay (`orbitDisplayHz`).
* **FlyEye** (Experimental) — Sensor → FlyEye stitches two unused DVS128s into one panoramic chip.
* **File → Save As**
  * Pauses playback and scans as fast as possible (preferred over re-recording to clip with IN/OUT or apply EventFilters).
  * Confirmation reuses **Show folder** / **Playback** / Recent Files.

* **Recording**
  * Overlay shows free space / remaining time; autoclose is robust. Low space offers a new location. File → Preferences sets the recording folder.
  * First **x** with several windows stores *Exit completely with x* in Preferences. **Ctrl+W** aborts recording only.

* **Multi-viewer**
  * Focusing any jAER window raises the others (File → Preferences → **Raise all windows**, on by default). Minimized windows and popups are skipped.
  * USB-open overlay reports bind status. Muxed filenames are OS-safe.

* **EventFilter Help**
  * **F1** and **?** toggle Help when the filter controls have focus (and from the `?` button). Collapsing the panel still closes Help.

* **Help menu**
  * See [Give Feedback](#give-feedback). **File Issue on Github** stays on GitHub (version, OS, logs; **Show folder**).

* **Menus and shortcuts**
  * **Sensor** menu (formerly AEChip). Recent files/folders header; reset-to-defaults moved to Preferences. Recording/Filtering separator.
  * **Ctrl+Shift+P** opens File → Preferences (also when the chip canvas has focus). **Ctrl+Alt+P** toggles sliding-window persistence. **P** accumulate, **Shift+P** reset, **Ctrl+P** fading.

* **SciDVS / AEDZ**
  * **SciDVS** (Experimental) is in the Sensor table; `SciDVS.xml` is the first-use default bias profile. Shared-PID hotplug with Davis is autodetected.
  * Compressed **AEDZ** (`.aedz`) record/replay is wired through AEViewer preferences (Experimental). Live configuration snapshots are frozen into AEDAT-4 / AEDZ / legacy writers.

* **Developer / packaging**
  * install4j project, icon, and localization live under `install4j/` (Ant, `replace-installed-jar`, and releasing docs updated). Unused `build.cmd`, Dropbox-hardcoded `jaer.desktop`, and Eclipse `.classpath`/`.project` removed from git.
  * SignPath CI: updated test-signing cert and [remote signing workflow](README-releasing-tagging.md); Linux GitHub Release uploader fix. WIP macOS Developer ID for signed `.dmg`.
  * 800×800 install4j launcher splash so the status line fits. eDVS, SpiNNaker, and OpalKelly factories are unregistered (sources remain).

### Bug fixes and minor improvements

* Fixed **Intel Arc crash** when opening a recent folder in the file dialog (`ChipDataFilePreview` no longer constructs a second `GLCanvas` beside the live viewer; `DrawGL` disables TextRenderer vertex arrays, matching `ChipCanvas`).
* Fixed **Y flip** in playback of DVXplorer Mini/Micro recordings.
* **DVXplorer Mini/Micro IMU**: CX3 debug endpoint no longer completes at USB poll rate (~100 kHz); one URB is resubmitted at ~800 Hz and samples go to a side queue.
* **Davis346Blue** APS frames that tore after a good start: SOF abandons a stuck half-frame; USB reset closes every open interface first. After three short frames, unplug/replug is suggested.
* **ROS2/Foxglove** enable no longer re-enters the FilterPanel setter (property-change loop); sinks restart only when a sink flag actually changes.
* **DNN TCP** control server rebinds when `controlPort` changes while enabled.
* EventCountFrames preview window is disposed when switching to EventWindows.
* **3D viewer** `ArrayIndexOutOfBoundsException` when ViewLoop and OpenGL shared an `EventPacket`.
* USB readers no longer close on themselves; EVK4 ISSD phases are reported. Close best-attempts DVX `dvxDataStop` and Prophesee ISSD shutdown before abandon.
* WindowSaver no longer stretches packed tool windows. Overlapping Info overlay text on DVX fixed.
* File info shows compression ratio. Open-file dialog has extension filters.
* Update check runs once per JVM. Reduced FilterPanel / USB / version-info log chatter.

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/3.3.0...3.4.0
