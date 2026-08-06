<!--
  Paste-ready for GitHub Releases once these files are on GitHub (master or tag 3.1.0).
  Image links below use raw.githubusercontent.com so they render in the Release body.
  After you push tag 3.1.0, optionally change /master/ → /3.1.0/ in the image URLs for permanence.

  Relative paths (3.1.0/....png) also work when viewing this file in the repo on GitHub,
  but they do NOT work when pasted into a Release description — use the absolute URLs.
-->

Go to [install4j jAER installers on Dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0) to download installers.

See video [installing and updating jaer on YouTube](https://youtu.be/qQVt8_gwYVY).

**jAER 3.1.0** is a minor release with major usability and format support: **first-use camera setup** (auto-load `deviceSettings`, open Hardware Configuration), **smarter USB AEChip selection** (Remember + hot-plug re-offer), **Metavision RAW EVT3 playback**, and richer **AEDAT-4** open/playback (faster indexing, multi-camera EVTS choice). Builds on the 3.0 typed `PacketBundle` / AEDAT-4 foundation from 3.0.x.

### Highlights

* **First-use device preferences** — the first time you open a production camera live, jAER imports shipped defaults from `deviceSettings/<Family>/<Chip>.xml`, shows what was loaded, and opens Hardware Configuration so you can review biases and options immediately.

![Initial preferences loaded automatically](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.1.0/initial-prefs-loaded-automatically.png)

![Hardware Configuration on first use](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.1.0/hw-controls-shown-first-use.png)

* **AEChip for USB device (Remember)** — when several AEChips share a VID/PID (e.g. Davis346 red vs blue), choose before open. **Remember this selection** stores a per-device mapping. Hot-plug between different cameras (e.g. NRV → Davis) re-offers when the current AEChip cannot drive the device.

![AEChip chooser with Remember](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.1.0/auto-chip-detect.png)

* **`deviceSettings` (was `biasgenSettings`)** — shipped bias/config XMLs renamed and organized as `deviceSettings/<Family>/<ChipSimpleName>.xml` for production chips; older/experimental presets archived out of the default tree.

* **Metavision RAW EVT3 playback** — open Prophesee Metavision `.raw` (EVT3) recordings; seek index cached under `java.io.tmpdir`. Help → sample-data links include Prophesee datasets.

![Help sample-data menu](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.1.0/sample-data-help-menu.png)

* **AEDAT-4 multi-camera + faster open** — pick which EVTS stream to play when a file muxes cameras; FileDataTable-based indexing speeds first open.

![AEDAT-4 multi-stream choice](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.1.0/aedat4-multistream-choice.png)

![Davis346 live color / events](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.1.0/Davis346RedColor.png)

### Features

* **First-use / hardware UX**
  * Auto-import shipped defaults with batched USB configuration (Davis FX3 no longer floods SPI during import).
  * Info dialog + Hardware Configuration after `LIVE` starts.
  * Waiting prompt: *Choose AEChip (Interface menu if needed)*.
  * Interface menu: skip USB re-scan when a device is already open; manual select clears `None`, checks AEChip compatibility, wakes the view loop.
  * Hot-plug: re-offer AEChip when the current chip cannot drive the plugged device.
  * Tighter Davis **User-Friendly Controls**; expert tabs scroll-wrapped for a usable window width.

* **deviceSettings**
  * Folder rename from `biasgenSettings` across code and docs.
  * Production families: Davis240, Davis346, DVS128, CochleaAMS1c, NRV, PropheseeIMX636HD, SciDVS.
  * Default file convention: `deviceSettings/<Family>/<SimpleName>.xml`.

* **Playback / file formats**
  * **Metavision RAW EVT3** (`.raw`) playback; index cache `*.metavisionrawidx`.
  * Faster AEDAT-4 first open (FileDataTable); sparse index under `java.io.tmpdir`.
  * AEDAT-4 multi-camera EVTS stream chooser.
  * Docs: [`docs/README-file-formats.md`](../docs/README-file-formats.md); Help sample-data + Prophesee / format spec links.

* **Installer / packaging**
  * install4j excludes local scratch and sources (`tmp/`, `src/`, `scripts/`, `logs/`, `bin/`, `tools/`, editor folders).
  * Release checklist updated.

### Bug fixes and minor improvements

* Fixed Davis FX3 **prefs-import hang** (per-change SPI during import) that could freeze the EDT / block window close; `DavisConfig` (and Tower / SciDVS) respect Biasgen batch edit.
* Fixed AEDAT-4 **previous-marker** jump and jog backwards.
* Fixed AEDAT-4 playback for **DV color** recordings; Davis event/frame XY alignment with typed extract.
* Fixed **DVXplorer** AEDAT-4 polarity packing (bit 11).
* Bound **NRV LibUsb.close** so USB teardown cannot hang forever.
* Default **autoContrast=true** for a better first view.
* Cleaned obsolete USBIO / unused libraries; `filterSettings` tidy.

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/3.0.1...3.1.0
