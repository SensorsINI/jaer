<!--
  Paste-ready for GitHub Releases. Image links use raw.githubusercontent.com so they
  render in the Release body. Relative paths work in the repo, not in a Release description.

  Put the download table and a link to https://jaerproject.org/install/ at the top.
  GitHub always appends its own Assets list at the bottom of the Release page.

  Images: GFM has no size syntax. Use HTML <img src="..." alt="..." width="50%" />

  There is no public 3.5.1. These notes cover master since tag 3.5.0.

  Public 3.5.2 notes. Download URLs and changelog point at tag 3.5.2.

  Screenshots still to capture into release-notes/3.5.2/:
    usb-in-statistics.png (USB → USB tuning… IN table at 1 Hz)
    hw-config-user-friendly-tab.png (Hardware Configuration first tab)
    evk4-drop-stats.png (DROP on the statistics bar / DVSBiasController overlay)
    gnss-overlay.png (NmeaGnssFilter live overlay / playback map)
    flymotion.png (FlyMotion L/R global flow vectors)
-->

**jAER 3.5.2** is a point release after **[3.5.0](https://github.com/SensorsINI/jaer/releases/tag/3.5.0)** (there is no public 3.5.1). After the first start trains an **ahead-of-time (AOT) cache**, later **runtime** launches are about **2× quicker** (click to live camera). The statistics bar shows **CD / CC / AEC / RT**; zoom keeps **square chip pixels**. File → **Save As** logs background ETA, warns on exit, and names exports from the current chip. File → Open plays **TU Delft event_planar** DAVIS240C `.h5`. Click the **statistics bar** for a field legend, noise-filter overlays **fill the chip width**, **USB IN** numbers live in USB tuning, and **EVK4 / Prophesee** live-drop handling is first-class. **NmeaGnssFilter** records a phone GNSS sidecar; Help → **FOV calculator** opens the lens FOV page; **FlyEye** gets **FlyMotion** and better timestamp-master / rewind behavior. Hardware Configuration opens on the **user-friendly** tab. See [Highlights](#highlights) below.

## Download

| You have | CPU | Download |
|---|---|---|
| Windows 10 / 11 | x64 | [jAER_windows-x64_3_5_2.exe](https://github.com/SensorsINI/jaer/releases/download/3.5.2/jAER_windows-x64_3_5_2.exe) |
| macOS | Apple Silicon (M1–M4) | [jAER_macos_aarch64_3_5_2.dmg](https://github.com/SensorsINI/jaer/releases/download/3.5.2/jAER_macos_aarch64_3_5_2.dmg) |
| macOS | Intel | [jAER_macos_3_5_2.dmg](https://github.com/SensorsINI/jaer/releases/download/3.5.2/jAER_macos_3_5_2.dmg) |
| Linux | x64 | [jAER_unix_3_5_2.sh](https://github.com/SensorsINI/jaer/releases/download/3.5.2/jAER_unix_3_5_2.sh) |
| Any OS | Sample data (~995 MB) | [jaer-sample-data.zip](https://github.com/SensorsINI/jaer/releases/download/3.5.2/jaer-sample-data.zip) ([README](https://github.com/SensorsINI/jaer/blob/master/sampleData/README.md)) |

Each installer includes a bundled [Eclipse Temurin](https://adoptium.net/) JDK from Adoptium (same **25** LTS as 3.5.0) — you do not install Java yourself. GitHub lists the same files again under **Assets** at the bottom of this page.

The public landing page is **[jaerproject.org](https://jaerproject.org/)** (also [sensorsini.github.io/jaer](https://sensorsini.github.io/jaer/)). **[Install Guide](https://jaerproject.org/install/)** has the Windows / macOS / Linux steps (SmartScreen, which DMG, libusb, `.sh`, USB).

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.2/jaerproject-homepage.png" alt="jaerproject.org homepage with OS-specific download" width="80%" />

Video: [installing and updating jAER on YouTube](https://youtu.be/qQVt8_gwYVY) (also covers *git clone* and rebuild from master).

jAER can self-update (Help → Check for release updates… → **Download and install**). Older archival releases may remain on [Dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0).

Installers offer a **sample recordings** download (off by default, ~995 MB). You can also fetch them later from Help → **Sample data**. Skipping or cancelling that download does not roll back the install.

---

### Highlights

* [2× quicker startup](#aot) — ahead-of-time (AOT) cache on every launch after the first
* [Statistics bar](#statistics-bar) — CD / CC / AEC / RT after the event count; click for a legend
* [Overlays that fill the chip](#overlays) — noise-filter stats + analog clock; square zoom
* [File → Save As](#save-as) — background ETA in the log; warn on exit; chip-named exports
* [Event Planar HDF5](#event-planar) — play TU Delft DAVIS240C `.h5` (cooked `xs,ys,ts,ps`)
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
<img src="https://github.com/user-attachments/assets/1707daf3-4a15-4e2b-b210-13db8b29b57b" alt="jaer-startup-AOT" width="80%" />

<h4 id="statistics-bar">Click the statistics bar for a legend</h4>

The compact line at the top of the viewer (`+19.8ms @5.323s`, `eps`, `nX`, `ARS`, `FS=…`) used to have a hover tooltip that vanished on every update. **Click the numbers** (or the bar) for an HTML legend that stays until you click again. F1 Quick help says the same thing.

After the event count the bar now shows the accumulation mode: **CD** (CountDuration), **CC** (ConstantCount), **AEC** (AreaEventCount), or **RT** (RealTime). **t** still cycles CD / CC / AEC.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.2/stat-bar-tooltip-click-show.png" alt="Click the statistics bar for a field legend" width="80%" />

Live keep-cap drops show as **(DROP)** (bar turns red) with a short hint: raise DVS threshold or refractory, or enable **DVS Auto Controller**. Host USB overruns still show as **(overrun)**.

<h4 id="overlays">Noise-filter overlay and analog clock</h4>

Noise-filter statistics (STCF and the other `AbstractNoiseFilter` overlays) **auto-fit the chip width** on first use. Change **show filtering statistics font size** yourself to keep a manual size; **Defaults** refits.

File → Preferences still has **Always display time** and **Analog clock** (absolute clock or relative stopwatch at the lower-left, with `hh:mm:ss.dd` above and the date below). Absolute time is shown in **your local timezone**.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.2/analog-clock%2Bstatistics-sizing.png" alt="Analog clock overlay and noise-filter statistics fitted to chip width" width="80%" />

**Zoom** (Ctrl+wheel) keeps **square chip pixels**. Vertical scale used to grow faster than horizontal because screen-pixel insets were mixed into the clip size.

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

If you use **File → Save As** after recording, the GNSS sidecar follows the recording: `<old>.gnss.csv` is renamed to match the saved AEDAT file (or deleted if the take is discarded).

Playback **showMap** draws a north-up track fitted to the chip, with COG/SOG and metre / m/s scale bars. New CSVs store `aedat4_unix_us` (same Unix µs as AEDAT-4 packets). Older sidecars without that column map the slider fraction onto `unix_ms`.

<!-- gnss-overlay.png -->

<h4 id="fov">Help → FOV calculator</h4>

**Help → FOV calculator** estimates horizontal and vertical field of view from pixel pitch, array size, lens focal length, and distance. Presets match jAER chip classes. It opens a local `../lensFOV/index.html` if that repo sits next to jAER, otherwise [sensorsini.github.io/lensFOV](https://sensorsini.github.io/lensFOV/).

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.2/lensFOV-help.png" alt="Help → FOV calculator menu" width="80%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.2/lensFOV-site.png" alt="Event-camera field of view calculator (DVS128, 1.8 mm lens)" width="50%" />

<h4 id="flyeye">FlyEye and FlyMotion</h4>

**FlyEye** (panoramic DVS128 pair) ships factory biases and a default **FlyMotion** filter: per-eye wide-field flow vectors (lobula-plate-style sum of local **DirectionSelectiveFlow**), drawn over the left and right eyes. Left and right clocks are never compared, so unsynchronized cameras work.

With **Timestamp master** Left or Right, a sync cable makes timestamps comparable (two-pointer merge). **None** concatenates the two streams (independent clocks must not be merge-sorted). Master/slave is re-applied after DVS128 timestamp reset. A desync dialog warns if last polarity times differ by more than 100 ms (cable unplug vs drift). Cabling check uses a 20 ms last-packet window.

AEDAT-4 rewind no longer swallows the second camera when independent timestamps jump backward in file order. Each camera keeps its own `lastTimesMap` plane. CountDuration playback keeps both eyes on the same time slices. Returning **LIVE** after playback resets DVS128 timestamps so the pair does not start with leftover file time.

<!-- flymotion.png -->

<h4 id="event-planar">Event Planar HDF5 (DAVIS240C)</h4>

File → Open now plays cooked event HDF5 from the TU Delft **event_planar** dataset (DAVIS240C 240×180). Layout is `/events/{xs,ys,ts,ps}` with Unix-second timestamps — not DSEC `/events/{x,y,t,p}` and not DDD17/DDD20 `/dvs`. Chip auto-detect selects **DAVIS240C**. Optional IMU / OptiTrack groups in the same file are not played.

* Dataset (4TU.ResearchData): [doi:10.34894/QTFHQX](https://doi.org/10.34894/QTFHQX)
* Training code: [tudelft/event_planar](https://github.com/tudelft/event_planar)
* Project page: [Fully neuromorphic vision and control for autonomous drone flight](https://mavlab.tudelft.nl/fully_neuromorphic_drone/)
* Paper: Paredes-Vallés et al., *Science Robotics* 9(90), eadi0591 (2024) — [doi:10.1126/scirobotics.adi0591](https://doi.org/10.1126/scirobotics.adi0591)

<h4 id="save-as">File → Save As</h4>

**Save As** from a file whose name is not a chip token (for example `3.h5` → `3-export.aedat4`) no longer guesses a wrong AEChip. The export is prefixed with the **current chip** (`DAVIS240C-3-export.aedat4`). File → Preferences → **Autoswitch Recording Sensor** is **Ask** / **Always** / **No** (replaces the old checkbox).

If the Save As window is hidden, progress and ETA go to the jAER log about every 10 s (not while the dialog is showing). **Close** becomes **Hide** once export starts. File → Exit or closing the viewer while Save As is running unhides the job and asks **Stay** vs abort.

<h4 id="hw-config">Hardware Configuration and window restore</h4>

Hardware Configuration opens on the **user-friendly** tab. Saved tab indices from prefs (and `Davis346blue.xml`) had restored Chip Config after extra tabs were added.

Packed dialogs and HW config keep a usable size and stay on the work area. First-open Hardware Configuration sits beside the viewer. USB tuning packs compactly with a wrapping Note strip. NRV user-friendly bias sliders and hint text no longer clip when the Biases window is narrow.

<!-- hw-config-user-friendly-tab.png -->

### Bug fixes and minor improvements

* **Statistics bar**: shows **CD / CC / AEC / RT** after the event count (**t** cycles CD / CC / AEC).
* **Zoom**: chip pixels stay square (Ctrl+wheel no longer stretches the vertical axis).
* **File → Save As**: chip-prefixed names; numeric tokens such as `3-export` are not treated as an AEChip; background ETA in the log every 10 s while the window is hidden; warn before quit or close; **Hide** instead of Close during export. File → Preferences → **Autoswitch Recording Sensor** is Ask / Always / No.
* **Event Planar HDF5**: File → Open plays TU Delft DAVIS240C `.h5` (`/events/{xs,ys,ts,ps}`); [dataset](https://doi.org/10.34894/QTFHQX), [project](https://mavlab.tudelft.nl/fully_neuromorphic_drone/), [paper](https://doi.org/10.1126/scirobotics.adi0591).
* **Space-time 3-D**: event points stay visible in the fitted cube.
* **DirectionSelectiveFlow**: **displayRawInput** shows DVS polarity again.
* **macOS**: Finder drop of AEDAT onto the chip view opens the file (`acceptDrop` + transferable; 3.5.2-rc.0 was broken).
* **macOS startup**: a saved empty viewer list no longer starts with 0 windows and exits code 0; attach `$TMPDIR/jaer/jAER-0.log` when reporting startup issues.
* **macOS download**: if the browser cannot report CPU (Safari often cannot), jaerproject.org does not guess a DMG. The main button goes to the Release page; pick **macOS Apple Silicon** or **macOS Intel** from About This Mac.
* **OpenCV webcams**: jAER no longer probes or opens webcams during splash, and webcams are never autobound. Use **Interface → Refresh**, then select the OpenCV camera explicitly.
* **Startup input**: mouse-wheel events over the image panel are ignored until chip/renderer/canvas startup is complete, avoiding an uncaught `chip instance is null` dialog during restore.
* **Customize / class chooser**: drag between Available and Selected to add, reorder, or remove filters and chips.
* **File → Export video**: **Rewind before recording** and **Close on rewind** sit next to IN/OUT. Quitting while ffmpeg is converting asks Stay or Quit anyway (truncated MP4 discarded).
* **ConstantCount / AreaEventCount** no longer behave like CountDuration after switching away from RealTime (a leftover ~25 ms min exposure).
* **FlyEye** ships factory DVS128 biases so both sensors have polarity; Timestamp master **None** no longer warns; CountDuration keeps both eyes aligned; LIVE after playback resets DVS128 timestamps.
* **NRV** user-friendly bias panel layout clipping; HTML hints reflow when the Biases window is narrow.
* **WindowSaver** restore: packed dialogs stay on screen; Preferences export/reset can schedule an install4j restart instead of only quitting.
* **EngineeringFormat** prints NaN (not 0); FilterPanel missing-setter noise is FINE.
* F1 Quick help: click the top bar for the statistics legend; screenshots live in `images/help` inside the jar.

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/3.5.0...3.5.2
