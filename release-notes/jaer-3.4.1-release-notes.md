<!--
  Paste-ready for GitHub Releases. Image links use raw.githubusercontent.com so they
  render in the Release body. Relative paths work in the repo, not in a Release description.

  Put the download table and short OS notes at the top. GitHub always appends its own
  Assets list at the bottom of the Release page.

  Images: GFM has no size syntax. Use HTML <img src="..." alt="..." width="50%" />

  Screenshots still to capture into release-notes/3.4.1/:
    sample-data-writable-folder.png (Help → Sample data chooser when Program Files is not writable)
    save-as-background.png (playback continuing while Save As runs)
    file-association.png (optional .aedat4 association / double-click)
    tab-overlay-hints.png (7 s color / accumulation reminder after File Open)
-->

**jAER 3.4.1** is mainly a **bug-fix** release to fix bugs in the major feature release **[3.4.0](https://github.com/SensorsINI/jaer/releases/tag/3.4.0)**. **Sample-data install** under Program Files, **File → Save As** saves compatible correct AEDAT-4, **single jAER instance** with double-click AEDAT files on Linux, and Davis File Close leftovers are the main user-facing fixes. See [Highlights](#highlights) below.

## Download

| You have | CPU | Download |
|---|---|---|
| Windows 10 / 11 | x64 | [jAER_windows-x64_3_4_1.exe](https://github.com/SensorsINI/jaer/releases/download/3.4.1/jAER_windows-x64_3_4_1.exe) |
| macOS | Apple Silicon (M1–M4) | [jAER_macos_aarch64_3_4_1.dmg](https://github.com/SensorsINI/jaer/releases/download/3.4.1/jAER_macos_aarch64_3_4_1.dmg) |
| macOS | Intel | [jAER_macos_3_4_1.dmg](https://github.com/SensorsINI/jaer/releases/download/3.4.1/jAER_macos_3_4_1.dmg) |
| Linux | x64 | [jAER_unix_3_4_1.sh](https://github.com/SensorsINI/jaer/releases/download/3.4.1/jAER_unix_3_4_1.sh) |

Each installer is <250 MB and includes a bundled [Eclipse Temurin](https://adoptium.net/) JDK from Adoptium (same **25** LTS as 3.4.0) — you do not install Java yourself.  GitHub lists the same files again under **Assets** at the bottom of this page.

Video: [installing and updating jAER on YouTube](https://youtu.be/qQVt8_gwYVY) (also covers *git clone* and rebuild from master).

jAER can self-update (Help → Check for release updates… → **Download and install**). Older archival releases may remain on [Dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0).

Installers offer a **sample recordings** download (off by default). You can also fetch them later from Help → **Sample data**. Skipping or cancelling that download no longer rolls back the install.

### Linux

```bash
chmod +x jAER_unix_3_4_1.sh
sh jAER_unix_3_4_1.sh
```

Start jAER from the install directory or the desktop / GNOME entry the installer created. No official apt / `.deb` (USB cameras need an unsandboxed install). If USB udev rules are missing for a jAER device that is plugged in, jAER shows the exact shell command to add the rule(s).

Optional installer checkbox: associate `.aedat` / `.aedat2` / `.aedatz` / `.aedat4` so a double-click opens the file in the running jAER (single instance). A second double-click while jAER is already up now starts playback of that file.

---

### Windows

Download the `.exe` and run it. This build is not Authenticode-signed yet: SmartScreen may say *Windows protected your PC* — **More info** → **Run anyway** (and **Install anyway** if the installer also warns). If **Smart App Control** blocks the unsigned launcher, turn that feature off or allow the app. USB cameras: if jAER reports `LIBUSB_ERROR_NOT_SUPPORTED`, bind **WinUSB** with [Zadig](https://zadig.akeo.ie/) (not libusb-win32). Prophesee EVK4 can use Prophesee **wdi-simple**.

**Sample recordings** work with a Program Files install. Help → Sample data (and File → Open) now asks for a writable folder when `C:\Program Files\jAER\sampleData` cannot be written; the default is `jaerSampleData` in your home directory. That folder is added to File recent folders. Optional installer checkbox: associate AEDAT extensions so a double-click opens the file in the single running instance.

### macOS

Apple menu → About This Mac: **Chip** Apple M1–M4 → `aarch64` DMG; **Processor** Intel → `jAER_macos_3_4_1.dmg` (no `aarch64` in the name). Terminal: `uname -m` is `arm64` or `x86_64`.

Open the DMG and run the installer. The build is unsigned; if macOS blocks it, [right-click → Open](https://support.apple.com/guide/mac-help/open-a-mac-app-from-an-unidentified-developer-mh40616/mac) (or Privacy & Security → **Open Anyway**). Prefer a user folder (`~/Applications` or `~/jaer`) if you do not want an admin install.

**Apple Silicon USB cameras** need Homebrew [libusb](https://formulae.brew.sh/formula/libusb): `brew install libusb`. If the dylib is missing, jAER shows a how-to and quits so the next launch can load it.

AEDAT types are declared in Info.plist so Finder can open recordings in the installed jAER.

---

### Highlights

* [Sample data without Program Files](#sample-data) — writable folder chooser; installer Skip / Cancel no longer uninstalls jAER
* [Double-click AEDAT](#double-click-aedat) — one installed instance; later files hand off to the running viewer
* [Save As](#save-as) — export in the background; native AEDAT-4 copied in record order; no overwrite of the playing file
* [Davis File Close](#davis-file-close) — leftover APS extract after Close is stopped
* [Hints on open](#hints-on-open) — short Tab overlay after a recording starts
* [Python / Remote](#python-remote) — File → Remote and dataloader guide; AEDAT-4 packing that PyPI `aedat` can parse
* [Bug fixes](#bug-fixes-and-minor-improvements)

<h4 id="sample-data">Sample recordings: writable folder, Skip, uninstall</h4>

3.4.0 could not unpack sample data under a default Windows **Program Files** install. Help → **Sample data** and File → Open now choose an unpack folder: install `sampleData` when it is writable, otherwise `jaerSampleData` in the home directory. The Welcome screen shows zip/unpacked size and a **minutes** ETA at 10 MB/s Wi-Fi. After files are copied, a **Sample recordings** screen can **Skip** or **Download**; cancelling the download does not roll back jAER. Uninstall deletes the default install `sampleData` (with a warning about extra files there) and does **not** delete `~/jaerSampleData`. If leftover files remain in the install folder, that folder is opened.

New clips in the zip: DVS128 RoboGoalie, [EssacSim](https://github.com/spikelab-jhu/isaac-sim-event-camera-plugin) warehouse quadruped (`DVS640`). File list: [sampleData/README.md](https://github.com/SensorsINI/jaer/blob/master/sampleData/README.md).

<!-- sample-data-writable-folder.png -->

<h4 id="double-click-aedat">Double-click AEDAT in one jAER</h4>

The installed launcher stays **one process**. Windows and Linux can optionally associate `.aedat`, `.aedat2`, `.aedatz`, and `.aedat4`. macOS declares those types in Info.plist. A cold start still opens the file from argv. A later double-click is delivered through install4j StartupNotification (including paths with spaces) and starts playback in the running viewer. On Linux, a second `.aedat4` while jAER was already running used to show splash and then do nothing; the StartupNotification listener and a tmpdir open-request handoff now open that file.

<!-- file-association.png -->

<h4 id="save-as">File → Save As</h4>

**There was an critical bug in previous Save-As that could duplicate DAVIS frames and leave events, frames, and IMU samples unsynchronized.** This is fixed: Native AEDAT-4 Save As **copies packets in file order** (EVTS / FRME / IMUS as recorded) instead of reslicing events to 8192 or reassembling Davis APS (that duplicated frames, e.g. 39 → 91). Copied frames/IMU use DV FileDataTable Unix µs so IN/OUT + EventFilters still attach APS and IMU.

Export runs on a **low-priority worker** with its own reader and a headless chip copy, so the viewer can keep playing or open another recording, and several Save As jobs can run at once. The done dialog reports original vs exported size, compression ratio, and duration to hundredths of a second.

Save As **refuses to overwrite** a recording that is currently playing. Output names missing a suffix (or a forgotten dot, e.g. `1aedat4`) get the selected format extension.

*Saved-as* AEDAT-4 confirmed to readable by Alex's [aedat pypi libray](https://pypi.org/project/aedat/) (except for DAVIS frames)

<!-- save-as-background.png -->

<h4 id="davis-file-close">Davis File Close leftover frames</h4>

After **File → Close**, ViewLoop could stay WAITING but still extract the last AEDAT-2 APS slice, so DavisFrameAssembler logged torn ResetRead SOF and DavisRenderer re-applied the same FramePacket. Extract is skipped while WAITING; empty raw packets are ignored; pixmap / last bundle reset on stopPlayback.

<h4 id="hints-on-open">Color and accumulation hints</h4>

After a recording starts, a short **Tab** overlay (about 7 s) reminds new users of color mode, event accumulation, and **c** / **t** / **f** / **s**. Tab still toggles the same summary.

<!-- tab-overlay-hints.png -->

<h4 id="python-remote">Python dataloaders and File → Remote</h4>

[docs/README-DNN-OpenCV-ROS.md](https://github.com/SensorsINI/jaer/blob/master/docs/README-DNN-OpenCV-ROS.md) is the path for OpenCV MJPEG, DNN mmap, ROS2/Foxglove, and reading recordings in Python (`aedat`, HDF5, CSV).

AEDAT-4 packing: `jAERConfigSnapshot` is a **sibling of `outInfo`**, not a child. PyPI **aedat 2.2.0** treated every `outInfo` child name as a numeric stream id; a snapshot inside `outInfo` raised `invalid digit found in string`. New recordings and File → Save As parse. Existing 3.4.0 sample zip files still need a re-export for that library.

**Known limit:** aedat 2.2.0 still cannot decode Davis APS (`OPENCV_16U_C1`, 10-bit ADC in 16-bit samples). It only maps 8-bit Gray/BGR/BGRA and aborts the iterator on the first APS packet. Color DV files (8U_C3) work. Events and IMU already decode.

### Bug fixes and minor improvements

* **Sample data** unpack folder when the install tree is not writable; in-app download can be cancelled; installer Skip after the rollback barrier; Welcome ETA in minutes and multi-line HTML on the Sample recordings screen.
* **Uninstall** removes default `jaer/sampleData` (warns about extra files); leftover install-dir contents are opened. Unix uninstall no longer prints `posix_spawn /bin/sh` after the bundled JRE is deleted; `replace-installed-jaer-jar.sh` line endings are LF.
* **Single-instance file open** on Windows/Linux/macOS; Linux second-file `%U` / `file://` handoff via tmpdir open-requests.
* **Save As** background export; record-order AEDAT-4 copy; no overwrite of the playing file; missing/wrong output extensions.
* **Davis** leftover APS extract after File Close.
* **IN/OUT marks** persist as CSV under `tmpdir/jaer/markers`.
* HotSpot `-XX:ErrorFile` and Help → File Issue on Github tweaks.
* Splash PNGs (`800w` / `256h` / `1024w`) regenerate from `VERSION.txt` on `ant install4j` (gitignored build products).
* README: Smart App Control; File → Remote / Python guide; new sample recordings (RoboGoalie, EssacSim).

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/3.4.0...3.4.1
