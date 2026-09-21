<!--
  Paste-ready for GitHub Releases. Image links use raw.githubusercontent.com so they
  render in the Release body. Relative paths work in the repo, not in a Release description.

  Put the download table and short OS notes at the top. GitHub always appends its own
  Assets list at the bottom of the Release page.

  Images: GFM has no size syntax. Use HTML <img src="..." alt="..." width="50%" />

  There is no public 3.5.1. These notes cover master since tag 3.5.0.

  This file is also the --notes-file for 3.5.2-rc.N. On public 3.5.2: drop the
  prerelease banner, change download URLs and changelog from 3.5.2-rc.1 to 3.5.2.

  Screenshots still to capture into release-notes/3.5.2/:
    usb-in-statistics.png (USB → USB tuning… IN table at 1 Hz)
    hw-config-user-friendly-tab.png (Hardware Configuration first tab)
    evk4-drop-stats.png (DROP on the statistics bar / DVSBiasController overlay)
    gnss-overlay.png (NmeaGnssFilter live overlay / playback map)
    flymotion.png (FlyMotion L/R global flow vectors)
-->

**This is prerelease [3.5.2-rc.1](https://github.com/SensorsINI/jaer/releases/tag/3.5.2-rc.1)** (not GitHub Latest). It supersedes [3.5.2-rc.0](https://github.com/SensorsINI/jaer/releases/tag/3.5.2-rc.0). In-app **Help → Check for updates** stays on **3.5.0**. Testers: use the table below, **Assets** at the bottom of this page, or [jaerproject.org](https://jaerproject.org/) **Download Prerelease**.

**jAER 3.5.2** is a point release after **[3.5.0](https://github.com/SensorsINI/jaer/releases/tag/3.5.0)** (there is no public 3.5.1). After the first start trains an **ahead-of-time (AOT) cache**, later **runtime** launches are about **2× quicker** (click to live camera). Click the **statistics bar** for a field legend, noise-filter overlays **fill the chip width**, **USB IN** numbers live in USB tuning, and **EVK4 / Prophesee** live-drop handling is first-class. **NmeaGnssFilter** records a phone GNSS sidecar; Help → **FOV calculator** opens the lens FOV page; **FlyEye** gets **FlyMotion** and better timestamp-master / rewind behavior. Hardware Configuration opens on the **user-friendly** tab. See [Highlights](#highlights) below.

## Download

Installer filenames stay `*_3_5_2.*` (public version). This candidate’s assets are on tag **3.5.2-rc.1**.

| You have | CPU | Download |
|---|---|---|
| Windows 10 / 11 | x64 | [jAER_windows-x64_3_5_2.exe](https://github.com/SensorsINI/jaer/releases/download/3.5.2-rc.1/jAER_windows-x64_3_5_2.exe) |
| macOS | Apple Silicon (M1–M4) | [jAER_macos_aarch64_3_5_2.dmg](https://github.com/SensorsINI/jaer/releases/download/3.5.2-rc.1/jAER_macos_aarch64_3_5_2.dmg) |
| macOS | Intel | [jAER_macos_3_5_2.dmg](https://github.com/SensorsINI/jaer/releases/download/3.5.2-rc.1/jAER_macos_3_5_2.dmg) |
| Linux | x64 | [jAER_unix_3_5_2.sh](https://github.com/SensorsINI/jaer/releases/download/3.5.2-rc.1/jAER_unix_3_5_2.sh) |

Each installer includes a bundled [Eclipse Temurin](https://adoptium.net/) JDK from Adoptium (same **25** LTS as 3.5.0) — you do not install Java yourself. GitHub lists the same files again under **Assets** at the bottom of this page.

The public landing page is **[jaerproject.org](https://jaerproject.org/)** (also [sensorsini.github.io/jaer](https://sensorsini.github.io/jaer/)). **Download Stable** is still Latest (**3.5.0**). When a newer rc exists, **Download Prerelease** appears next to it.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.2/jaerproject-homepage.png" alt="jaerproject.org homepage with OS-specific download" width="80%" />

Video: [installing and updating jAER on YouTube](https://youtu.be/qQVt8_gwYVY) (also covers *git clone* and rebuild from master).

jAER can self-update (Help → Check for release updates… → **Download and install**) once this version is Latest. Older archival releases may remain on [Dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0).

Installers offer a **sample recordings** download (off by default, ~995 MB). You can also fetch them later from Help → **Sample data**. Skipping or cancelling that download does not roll back the install.

### Linux

```bash
chmod +x jAER_unix_3_5_2.sh
sh jAER_unix_3_5_2.sh
```

Start jAER from the install directory or the desktop / GNOME entry the installer created. No official apt / `.deb` (USB cameras need an unsandboxed install). If USB udev rules are missing for a jAER device that is plugged in, jAER shows the exact shell command to add the rule(s).

---

### Windows

Download the `.exe` and run it. **3.5.2 is Authenticode-signed** (publisher **Tobias Delbruck**). SmartScreen will still say *Windows protected your PC* until that signature has enough downloads — **More info** → **Run anyway**. If **Smart App Control** blocks the launcher, allow the app or turn that feature off. `winget` can install this signed build even while SmartScreen still warns.

USB cameras: if jAER reports `LIBUSB_ERROR_NOT_SUPPORTED`, bind **WinUSB** with [Zadig](https://zadig.akeo.ie/) (not libusb-win32). Prophesee EVK4 can use Prophesee **wdi-simple**.

### macOS

Apple menu → About This Mac: **Chip** Apple M1–M4 → `aarch64` DMG; **Processor** Intel → `jAER_macos_3_5_2.dmg` (no `aarch64` in the name). Terminal: `uname -m` is `arm64` or `x86_64`.

**3.5.2 DMGs are signed with Apple Developer ID and notarized by Apple.** Gatekeeper treats them as from identified developer **Tobias Delbruck**. Double-click the `.dmg` (it mounts a disk). In the Finder window, double-click **`jAER 3.5.2 Installer`**. A notarized GitHub DMG should open normally. If you still have an older unsigned DMG, use [right-click → Open](https://support.apple.com/guide/mac-help/open-a-mac-app-from-an-unidentified-developer-mh40616/mac) (or Privacy & Security → **Open Anyway**). Prefer a user folder (`~/Applications` or `~/jaer`) unless you want an admin install into `/Applications`.

**Apple Silicon USB cameras** need Homebrew [libusb](https://formulae.brew.sh/formula/libusb): `brew install libusb`. If the dylib is missing, jAER shows a how-to and quits so the next launch can load it.

Dropping an `.aedat` / `.aedat4` from Finder onto the chip view now opens the file (this was broken in 3.5.0 / 3.5.2-rc.0).

---

### Highlights

* [2× quicker startup](#aot) — ahead-of-time (AOT) cache on every launch after the first
* [Statistics bar legend](#statistics-bar) — click the top numbers; F1 Quick help matches
* [Overlays that fill the chip](#overlays) — noise-filter stats + analog clock
* [Live USB statistics](#usb-in) — live FIFO / fill / throughput in USB tuning
* [EVK4 / Prophesee live drops](#evk4) — DROP on the stats line; bias and Auto Controller
* [GNSS sidecar](#gnss) — phone NMEA over Wi-Fi; `.gnss.csv` next to the recording
* [FOV calculator](#fov) — Help → FOV calculator (pixel pitch, array, focal length)
* [FlyEye / FlyMotion](#flyeye) — timestamp master, rewind, per-eye global flow
* [Hardware Configuration](#hw-config) — user-friendly tab; windows stay on screen
* [Bug fixes](#bug-fixes-and-minor-improvements)

<h4 id="aot">2× quicker runtime (ahead-of-time cache)</h4>

The first Java 25 jAER start after install trains a HotSpot **ahead-of-time (AOT)** file under `tmpdir/jaer/` (`%TEMP%\jaer\` on Windows). Every later launch reuses that cache. On a GEEKOM laptop with Intel Core Ultra 9 185H (2.50 GHz), full startup from click to live camera dropped from **9 s to about 4 s**.

Production media also **jlink** a JRE module set and strip unused install4j runtime classes (`shrinkRuntime`). Linux and macOS media no longer pack a leftover install4j setup download (~176 MB).

<!-- webp: 3.5.2/jaer-startup-AOT.webp -->

<h4 id="statistics-bar">Click the statistics bar for a legend</h4>

The compact line at the top of the viewer (`+19.8ms @5.323s`, `eps`, `nX`, `ARS`, `FS=…`) used to have a hover tooltip that vanished on every update. **Click the numbers** (or the bar) for an HTML legend that stays until you click again. F1 Quick help says the same thing.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.2/stat-bar-tooltip-click-show.png" alt="Click the statistics bar for a field legend" width="80%" />

Live keep-cap drops show as **(DROP)** (bar turns red) with a short hint: raise DVS threshold or refractory, or enable **DVS Auto Controller**. Host USB overruns still show as **(overrun)**.

<h4 id="overlays">Noise-filter overlay and analog clock</h4>

Noise-filter statistics (STCF and the other `AbstractNoiseFilter` overlays) **auto-fit the chip width** on first use. Change **show filtering statistics font size** yourself to keep a manual size; **Defaults** refits.

File → Preferences still has **Always display time** and **Analog clock** (absolute clock or relative stopwatch at the lower-left, with `hh:mm:ss.dd` above and the date below). Absolute time is shown in **your local timezone**.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.2/analog-clock%2Bstatistics-sizing.png" alt="Analog clock overlay and noise-filter statistics fitted to chip width" width="80%" />

<h4 id="usb-in">USB IN statistics</h4>

**USB → USB tuning…** now shows a 1 Hz table of bulk-IN transfer windows while that window is open: fill, completed URB size, interval, throughput, empty/short/error counts. Fill is yellow / orange / red above 50 / 75 / 100%. Size rows are **bytes on the wire**, not events (per-row tooltips). Statistics are collected only while tuning is open — there is no separate USB → Log USB statistics menu.

<!-- usb-in-statistics.png -->

<h4 id="evk4">EVK4 / Prophesee: drops, biases, Auto Controller</h4>

Live keep-cap drops are a first-class **DroppedDataInfo** kind. Prophesee reports kept / cap / rate. EVK4 expert bias sliders write idac bytes **while dragging**. IMX636 `bias_refr` friendly range matches Metavision (−20…+235). A **busy-scene** IMX636 HD bias preset ships with a higher threshold and longer refractory.

**DVSBiasController** overlay uses DrawGL (resolution-independent, drop shadow), defaults detailed info on, and shows only the tweak the current goal controls. The rate estimator is no longer stuck at a displayed 0 Hz after a bias change.

<!-- evk4-drop-stats.png -->

<h4 id="gnss">GNSS sidecar (phone NMEA over Wi-Fi)</h4>

**NmeaGnssFilter** shows lat/lon, course, and speed on the chip view. While you record, it writes `<name>.gnss.csv` next to the AEDAT file. Playback reloads that sidecar. The stream is **NMEA over TCP/UDP on Wi-Fi**, not Bluetooth and not a COM port.

Typical field setup: install **[gpsdRelay](https://f-droid.org/packages/io.github.project_kaat.gpsdrelay/)** on the phone (F-Droid; not on the Play Store), start a TCP server on port **2947**, turn on the phone **Wi-Fi hotspot**, join it from the laptop, set `host` to the hotspot gateway, `transport` = **TCP_CLIENT**, enable the filter in **LIVE** or **WAITING**. Muxed cameras: enable this filter on **one** viewer only. Filter **?** (or F1 on the panel) has the full how-to.

Playback **showMap** draws a north-up track fitted to the chip, with COG/SOG and metre / m/s scale bars. New CSVs store `aedat4_unix_us` (same Unix µs as AEDAT-4 packets). Older sidecars without that column map the slider fraction onto `unix_ms`.

<!-- gnss-overlay.png -->

<h4 id="fov">Help → FOV calculator</h4>

**Help → FOV calculator** estimates horizontal and vertical field of view from pixel pitch, array size, lens focal length, and distance. Presets match jAER chip classes. It opens a local `../lensFOV/index.html` if that repo sits next to jAER, otherwise [sensorsini.github.io/lensFOV](https://sensorsini.github.io/lensFOV/).

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.2/lensFOV-help.png" alt="Help → FOV calculator menu" width="80%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.2/lensFOV-site.png" alt="Event-camera field of view calculator (DVS128, 1.8 mm lens)" width="50%" />

<h4 id="flyeye">FlyEye and FlyMotion</h4>

**FlyEye** (panoramic DVS128 pair) ships factory biases and a default **FlyMotion** filter: per-eye wide-field flow vectors (lobula-plate-style sum of local **DirectionSelectiveFlow**), drawn over the left and right eyes. Left and right clocks are never compared, so unsynchronized cameras work.

With **Timestamp master** Left or Right, a sync cable makes timestamps comparable (two-pointer merge). **None** concatenates the two streams (independent clocks must not be merge-sorted). Master/slave is re-applied after DVS128 timestamp reset. A desync dialog warns if last polarity times differ by more than 100 ms (cable unplug vs drift). Cabling check uses a 20 ms last-packet window.

AEDAT-4 rewind no longer swallows the second camera when independent timestamps jump backward in file order. Each camera keeps its own `lastTimesMap` plane.

<!-- flymotion.png -->

<h4 id="hw-config">Hardware Configuration and window restore</h4>

Hardware Configuration opens on the **user-friendly** tab. Saved tab indices from prefs (and `Davis346blue.xml`) had restored Chip Config after extra tabs were added.

Packed dialogs and HW config keep a usable size and stay on the work area. First-open Hardware Configuration sits beside the viewer. USB tuning packs compactly with a wrapping Note strip. NRV user-friendly bias sliders and hint text no longer clip when the Biases window is narrow.

<!-- hw-config-user-friendly-tab.png -->

### Bug fixes and minor improvements

* **macOS**: Finder drop of AEDAT onto the chip view opens the file (`acceptDrop` + transferable; 3.5.2-rc.0 was broken).
* **Customize / class chooser**: drag between Available and Selected to add, reorder, or remove filters and chips.
* **File → Export video**: **Rewind before recording** and **Close on rewind** sit next to IN/OUT. Quitting while ffmpeg is converting asks Stay or Quit anyway (truncated MP4 discarded).
* **ConstantCount / AreaEventCount** no longer behave like CountDuration after switching away from RealTime (a leftover ~25 ms min exposure).
* **FlyEye** ships factory DVS128 biases so both sensors have polarity; Timestamp master **None** no longer warns.
* **NRV** user-friendly bias panel layout clipping; HTML hints reflow when the Biases window is narrow.
* **WindowSaver** restore: packed dialogs stay on screen; Preferences export/reset can schedule an install4j restart instead of only quitting.
* **EngineeringFormat** prints NaN (not 0); FilterPanel missing-setter noise is FINE.
* F1 Quick help: click the top bar for the statistics legend; screenshots live in `images/help` inside the jar.

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/3.5.0...3.5.2-rc.1
