<!--
  Paste-ready for GitHub Releases. Image links use raw.githubusercontent.com so they
  render in the Release body. Relative paths work in the repo, not in a Release description.

  Put the download table and short OS notes at the top. GitHub always appends its own
  Assets list at the bottom of the Release page.

  Images: GFM has no size syntax. Use HTML <img src="..." alt="..." width="50%" />

  Screenshots still to capture into release-notes/3.5.0/:
    signed-windows.png (Properties → Digital Signatures / no SmartScreen)
    notarized-mac-installer.png (Finder: jAER 3.5.0 Installer)
    vcr-setup-dialog.png (Start recording timed/VCR section)
    analog-clock.png (playback analog clock overlay)
    sample-data-writable-folder.png (Help → Sample data chooser when Program Files is not writable)
    save-as-background.png (playback continuing while Save As runs)
    file-association.png (optional .aedat4 association / double-click)
    tab-overlay-hints.png (7 s color / accumulation reminder after File Open)
-->

**jAER 3.5.0** is a feature release after **[3.4.0](https://github.com/SensorsINI/jaer/releases/tag/3.4.0)**. **Signed Windows and macOS installers**, **timed / rotating VCR recordings**, **sample-data install and download**, **one jAER instance with cross-platform AEDAT file association**, and **OpenCV cameras** (synchronized webcam / USB frame recording next to an event sensor) are the main user-facing additions. Export video, Save As, and playback UI also moved a long way. This pre-release **replaces 3.4.1** (that short bug-fix pre-release is folded in here). See [Highlights](#highlights) below.

## Download

| You have | CPU | Download |
|---|---|---|
| Windows 10 / 11 | x64 | [jAER_windows-x64_3_5_0.exe](https://github.com/SensorsINI/jaer/releases/download/3.5.0/jAER_windows-x64_3_5_0.exe) |
| macOS | Apple Silicon (M1–M4) | [jAER_macos_aarch64_3_5_0.dmg](https://github.com/SensorsINI/jaer/releases/download/3.5.0/jAER_macos_aarch64_3_5_0.dmg) |
| macOS | Intel | [jAER_macos_3_5_0.dmg](https://github.com/SensorsINI/jaer/releases/download/3.5.0/jAER_macos_3_5_0.dmg) |
| Linux | x64 | [jAER_unix_3_5_0.sh](https://github.com/SensorsINI/jaer/releases/download/3.5.0/jAER_unix_3_5_0.sh) |

Each installer is <250 MB and includes a bundled [Eclipse Temurin](https://adoptium.net/) JDK from Adoptium (same **25** LTS as 3.4.0) — you do not install Java yourself. GitHub lists the same files again under **Assets** at the bottom of this page.

Video: [installing and updating jAER on YouTube](https://youtu.be/qQVt8_gwYVY) (also covers *git clone* and rebuild from master).

jAER can self-update (Help → Check for release updates… → **Download and install**). Older archival releases may remain on [Dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0).

Installers offer a **sample recordings** download (off by default, ~995 MB). You can also fetch them later from Help → **Sample data**. Skipping or cancelling that download does not roll back the install.

### Linux

```bash
chmod +x jAER_unix_3_5_0.sh
sh jAER_unix_3_5_0.sh
```

Start jAER from the install directory or the desktop / GNOME entry the installer created. No official apt / `.deb` (USB cameras need an unsandboxed install). If USB udev rules are missing for a jAER device that is plugged in, jAER shows the exact shell command to add the rule(s).

---

### Windows

Download the `.exe` and run it. **3.5.0 is Authenticode-signed** (publisher **Tobias Delbruck**). SmartScreen will still say *Windows protected your PC* until that signature has enough downloads — **More info** → **Run anyway**. If **Smart App Control** blocks the launcher, allow the app or turn that feature off. `winget` can install this signed build even while SmartScreen still warns.

USB cameras: if jAER reports `LIBUSB_ERROR_NOT_SUPPORTED`, bind **WinUSB** with [Zadig](https://zadig.akeo.ie/) (not libusb-win32). Prophesee EVK4 can use Prophesee **wdi-simple**.

### macOS

Apple menu → About This Mac: **Chip** Apple M1–M4 → `aarch64` DMG; **Processor** Intel → `jAER_macos_3_5_0.dmg` (no `aarch64` in the name). Terminal: `uname -m` is `arm64` or `x86_64`.

**3.5.0 DMGs are signed with Apple Developer ID and notarized by Apple.** Gatekeeper treats them as from identified developer **Tobias Delbruck**. Double-click the `.dmg` (it mounts a disk). In the Finder window, double-click **`jAER 3.5.0 Installer`**. A notarized GitHub DMG should open normally. If you still have an older unsigned DMG, use [right-click → Open](https://support.apple.com/guide/mac-help/open-a-mac-app-from-an-unidentified-developer-mh40616/mac) (or Privacy & Security → **Open Anyway**). Prefer a user folder (`~/Applications` or `~/jaer`) unless you want an admin install into `/Applications`.

**Apple Silicon USB cameras** need Homebrew [libusb](https://formulae.brew.sh/formula/libusb): `brew install libusb`. If the dylib is missing, jAER shows a how-to and quits so the next launch can load it.

---

### Highlights

* [Signed Windows and macOS installers](#signed-installers) — Authenticode + notarized Developer ID
* [Timed / rotating VCR recordings](#vcr) — cassette files, merge on stop
* [Sample recordings](#sample-data) — installer + Help → Sample data; writable folder when Program Files is locked
* [OpenCV cameras](#opencv-cameras) — synchronized webcam / USB frames with an event sensor (NRV + webcam)
* [Double-click AEDAT in one jAER](#double-click-aedat) — single instance; Windows / Linux / macOS file association
* [Export video](#export-video) — File name / folder, IN/OUT, ffmpeg MP4
* [Playback marks and time](#playback) — IN/OUT/m on the sparkline; analog clock; FlexTime min/max
* [Save As](#save-as) — background export; record-order AEDAT-4; taskbar ETA
* [Bug fixes](#bug-fixes-and-minor-improvements)

<h4 id="signed-installers">Signed Windows and macOS installers</h4>

Windows `.exe` media is **Authenticode-signed**. The publisher is **Tobias Delbruck** (the same name Gatekeeper shows on Mac). Keep using **More info** → **Run anyway**; SmartScreen lags new signatures. `winget` can install this signed build even while that warning still appears.

macOS Intel and Apple Silicon DMGs are **Developer ID signed and notarized by Apple**. Gatekeeper shows identified developer **Tobias Delbruck** (same name as the Windows publisher). The installer app inside the disk image is named **`jAER 3.5.0 Installer`**.

Linux `.sh` installers stay unsigned (normal for a shell installer). USB cameras still need an unsandboxed install.

<!-- signed-windows.png -->
<!-- notarized-mac-installer.png -->

<h4 id="vcr">Timed and rotating VCR recordings</h4>

**File → Start recording** (and any timed start) opens a setup dialog: name, folder, AEDAT-4 compression, optional **recorded-event filtering**, and a **Time limit / VCR** section. A non-zero limit is the length of each cassette. VCR is AEDAT-4 only.

<!-- vcr-recording-setup.png -->

* **Infinite** — keep adding cassette files (`…_c0001.aedat4`, `_c0002`, …) in a `…-VCR` session folder
* **Finite** — stop after N cassettes
* **Rotate** — keep only the last N files (oldest deleted as the next cassette opens)

Each cassette is closed fully so a synced folder can be inspected; there is no Save As per roll. Overlay shows compact `h/m/s` duration, cassette index, and remaining time. A once-per-session warning appears if the computer would sleep during a long run.

On stop, **Merge VCR deck** concatenates cassettes to one AEDAT-4 (stitches cassette-boundary timestamp jumps). Optional: delete the source folder after a successful merge; **Play** / **Show folder** on the done dialog.

<!-- vcr-setup-dialog.png -->

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.0/vcr-recording-overlay.png" alt="VCR recording overlay with cassette duration and remaining time" width="50%" />

<h4 id="sample-data">Sample recordings: install, download, writable folder</h4>

Help → **Sample data** downloads about **995 MB** of short AEDAT-4 clips (DVS128, DAVIS240/346, NRV DELTA01, Prophesee IMX636, DDD20 driving, Steadicam, RoboGoalie, [EssacSim](https://github.com/spikelab-jhu/isaac-sim-event-camera-plugin) warehouse quadruped, Telluride mountain biking, hummingbirds). Permanent zip: [jaer-sample-data.zip](https://github.com/SensorsINI/jaer/releases/latest/download/jaer-sample-data.zip). File list with 5 s WebP previews: [sampleData/README.md](https://github.com/SensorsINI/jaer/blob/master/sampleData/README.md).

3.4.0 could not unpack under a default Windows **Program Files** install. Help → Sample data and File → Open now choose an unpack folder: install `sampleData` when it is writable, otherwise `jaerSampleData` in the home directory. The Welcome screen shows zip/unpacked size and a **minutes** ETA at 10 MB/s Wi-Fi. After files are copied, **Skip** or **Download**; cancelling does not roll back jAER. Uninstall deletes the default install `sampleData` (with a warning about extra files) and does **not** delete `~/jaerSampleData`.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/downloading-sample-data.png" alt="Downloading sample recordings progress" width="50%" />

<!-- sample-data-writable-folder.png -->

<h4 id="double-click-aedat">Double-click AEDAT in one jAER</h4>

The installed launcher stays **one process**. Windows and Linux can optionally associate `.aedat`, `.aedat2`, `.aedatz`, and `.aedat4`. macOS declares those types in Info.plist. A cold start opens the file from argv. A later double-click is delivered through install4j StartupNotification (including paths with spaces) and starts playback in the running viewer. On Linux, a second `.aedat4` while jAER was already running used to show splash and then do nothing; the StartupNotification listener and a tmpdir open-request handoff now open that file. Double-clicked files are added to Recent Files.

If a leftover `JAERViewerRunning.txt` points at another jAER process, you can **Force quit previous** so the camera is not stuck with `LIBUSB_ERROR_ACCESS`.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.4.0/force-quit-previous-jaer.png" alt="Force quit previous jAER if a leftover instance holds the camera" width="50%" />

<!-- file-association.png -->

<h4 id="opencv-cameras">OpenCV cameras</h4>

Open a **UVC webcam or other OpenCV USB camera** in a second viewer next to an event sensor (for example **NRV DELTA01**), then **File → Synchronize** and record. RGB frames and events share one AEDAT-4 session. Mux start zeros every viewer and flushes leftover USB packets so the first slice is real t=0 (not mixed pre-reset events). NRV USB time is stretched onto the host clock so it stays with OpenCV frames (CX3 prototype DELTA01 runs about 6% fast). Playback of a synchronized group uses one CountDuration timeslice, FPS, and playhead; **R** / auto-repeat rewinds the whole group when the shortest stream ends.

**Interface** lists OpenCV cameras next to libusb event cameras (`OpenCV: 0 DSHOW 640x480`, AVFoundation on macOS, V4L2 on Linux). Selecting one switches **Sensor** to `OpenCvFrameCamera`. Live RGB frames record as AEDAT-4 **FRME**. The **OpenCV** menu requests size, YUY2/MJPG, frame rate, brightness/contrast (mouse wheel or Left/Right), autofocus, and on Windows the DirectShow **Camera settings…** dialog.

Playback assigns FRME-only cameras (or OpenCV EVTS+FRME with the same `source`) to `OpenCvFrameCamera`. Frame/IMU-only files play on a time playhead (CountDuration). **Remember last interface** off plus Interface → **None** leaves the webcam free for other apps. Alignment is software (about one frame period), not a hardware trigger.

<!-- webp: 3.5.0/NRV+webcam-synchronized.webp -->
<img src="https://github.com/user-attachments/assets/c3d9bb9f-c473-4726-be5f-72a8647f1e04" alt="NRV+webcam-synchronized" width="80%" />

<h4 id="export-video">File → Export video</h4>

**File → Export video…** splits **File name** vs **Folder** (recent folders, same extension handling as Save As). Playback defaults to the open recording’s basename and parent folder; live capture uses `Chip_yyyy-MM-dd-HH-mm` in the last recording folder. Capture **rewinds to IN** (or start) and stops at **OUT** / EOF. Overwrite asks once (Don’t show again). Optional ffmpeg convert to MP4 after close; odd canvas sizes no longer break the convert. Opening an exported `.mp4` / `.avi` from Recent Files launches the OS player instead of treating it as an event recording.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.0/video-export.png" alt="Export video dialog" width="50%" />

<h4 id="playback">Playback marks, time overlay, FlexTime</h4>

IN (green), OUT (orange), and **m** markers draw on the event-rate sparkline. Marks map from the file stream so they survive sparkline bind and no longer NPE in SyncPlayer. Restored IN/OUT stay visible. Marks persist as CSV under `tmpdir/jaer/markers`.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.0/new-markers.png" alt="IN, OUT, and m marks on the playback sparkline" width="80%" />

**File → Preferences**: relative vs absolute playback time, **Always display time**, and an **analog clock** (absolute) or stopwatch (relative) at the lower-left of the chip view. Recording timezone is preserved for AEDAT-4 playback. HUD overlay fonts scale with chip size and stay in the viewport when zoomed.

**ConstantCount** and **AreaEventCount** wait for a minimum event-time span (default 1 ms; 0 disables) so same-timestamp bursts are one frame, with an optional max (0 disables the old 1 s AreaEventCount cap). Dragging the playback slider restores play/pause afterward.

Space-time 3-D: an options menu is available when that view is the startup method; zoom is kept while rotating the view.

After a recording starts, a short **Tab** overlay (about 7 s) reminds new users of color mode, event accumulation, and **c** / **t** / **f** / **s**. Tab still toggles the same summary.

<!-- analog-clock.png -->
<!-- tab-overlay-hints.png -->

<h4 id="save-as">File → Save As</h4>

**There was a critical bug in 3.4.0 Save As that could duplicate DAVIS frames and leave events, frames, and IMU samples unsynchronized.** Native AEDAT-4 Save As **copies packets in file order** (EVTS / FRME / IMUS as recorded) instead of reslicing events to 8192 or reassembling Davis APS. Copied frames/IMU use DV FileDataTable Unix µs so IN/OUT + EventFilters still attach APS and IMU. RotateFilter no longer drops all APS frames on export.

Export runs on a **low-priority worker** with its own reader and a headless chip copy, so the viewer can keep playing or open another recording, and several Save As jobs can run at once. The taskbar shows progress; the bar is IN/OUT-relative; ETA and filter % are shown. The done dialog reports original vs exported size, compression ratio, and duration to hundredths of a second.

Save As **refuses to overwrite** a recording that is currently playing. Output names missing a suffix (or a forgotten dot, e.g. `1aedat4`) get the selected format extension.

*Saved-as* AEDAT-4 is readable by [PyPI aedat](https://pypi.org/project/aedat/) (events and IMU; Davis APS is still a known limit of that library). `jAERConfigSnapshot` is a **sibling of `outInfo`**, not a child, so aedat 2.2.0 no longer raises `invalid digit found in string`.

<!-- save-as-background.png -->

### Bug fixes and minor improvements

* **File → Synchronize** muxed recording flushes leftover USB/raw packets on timestamp zero (DAVIS, DVS128, EVK4, DVX, SciDVS, Cochlea, Prophesee, NRV, OpenCV) so the first packet is a real t=0 slice. Playback shares CountDuration, target FPS, and playhead; **R** / auto-repeat rewinds the whole group at the shortest stream.
* **NRV DELTA01** USB timestamps are stretched onto host time so webcam frames stay aligned in muxed files (CX3 prototype ~6% fast).
* **Pause**: leftover PLAYBACK pause no longer blocks Interface from reopening USB; Space pauses synced playback.
* **macOS Aqua**: playback slider thumb scrubs instead of a wide bar; Save As filename field shows chip-datestamp names.
* **Sample data** unpack folder when the install tree is not writable; in-app download can be cancelled; installer Skip after the rollback barrier; Welcome ETA in minutes; zip table has sizes and 240 px WebP previews.
* **Uninstall** removes default `jaer/sampleData` (warns about extra files); leftover install-dir contents are opened. Unix uninstall no longer prints `posix_spawn /bin/sh` after the bundled JRE is deleted.
* **Single-instance file open** on Windows/Linux/macOS; Linux second-file `%U` / `file://` handoff; double-clicked files go to Recent Files.
* **Save As** background export; record-order AEDAT-4 copy; no overwrite of the playing file; missing/wrong output extensions; taskbar ETA; RotateFilter APS frames.
* **Davis** leftover APS extract after File Close.
* **Export video** odd-size MP4 convert; rewind to IN; overwrite confirm; OS player for `.mp4`/`.avi`.
* **IN/OUT/m marks** on the sparkline; persist as CSV under `tmpdir/jaer/markers`.
* **OpenGL** after window resize on Windows; HD OpenCV pixmap crash; FlexTime NPE when growing AEDAT-4 raw packets.
* **rosbag** IMU timestamps and unnamed-bag chip guess.
* **Rendering-rate warning** no longer double-dialogs; Don’t show again sticks.
* First recording this session shows a setup dialog; recording overlay font scaled; compact `h/m/s` duration.
* AEDAT-4 write compression bench and recording codec help in the Start recording dialog.
* Rebuild `dist/jAER.jar` before replacing the installed copy (`replace-installed-jaer-jar`).
* Splash PNGs (`800w` / `256h` / `1024w`) regenerate from `VERSION.txt` on `ant install4j`.
* Hide the historical **MonSeq** menu. Status logging attaches after the viewer UI exists.
* Python / File → Remote guide: [docs/README-DNN-OpenCV-ROS.md](https://github.com/SensorsINI/jaer/blob/master/docs/README-DNN-OpenCV-ROS.md).

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/3.4.0...3.5.0
