# Event-sensor software comparison

Living snapshot of desktop / library software for event cameras. **Last reviewed: 2026-09-14.** Vendor cells are from public docs and may lag; `?` means not verified here. Corrections welcome.

Packages as **columns**, attributes as **rows** (easy to add a feature; adding a package means a new column). Start set: [jAER](https://jaerproject.org), [DV](https://docs.inivation.com/software/dv/), [Metavision SDK](https://docs.prophesee.ai/stable/) (Studio + paid/dev SDK), [OpenEB](https://github.com/prophesee-ai/openeb), [neuromorphic-drivers](https://github.com/neuromorphicsystems/neuromorphic-drivers).

Legend in the feature table: **Yes** / **Partial** / **No** / **n/a** / **?**.

---

## Package identity

| | [jAER](https://jaerproject.org) | [DV](https://docs.inivation.com/software/dv/) | [Metavision SDK](https://docs.prophesee.ai/stable/) | [OpenEB](https://github.com/prophesee-ai/openeb) | [neuromorphic-drivers](https://github.com/neuromorphicsystems/neuromorphic-drivers) |
|---|---|---|---|---|---|
| **package_name** | [jAER](https://jaerproject.org) | [DV](https://docs.inivation.com/software/dv/) (`dv-gui` + `dv-runtime`) | [Metavision SDK](https://docs.prophesee.ai/stable/) (Studio + SDK) | [OpenEB](https://github.com/prophesee-ai/openeb) | [neuromorphic-drivers](https://github.com/neuromorphicsystems/neuromorphic-drivers) |
| **version** | **3.5.0** (GitHub *draft* as of 2026-09-14). Latest *published*: [3.4.0](https://github.com/SensorsINI/jaer/releases/tag/3.4.0) | **dv-runtime 1.7.2** / **dv-processing 2.0.3**. AUR `dv-gui` last seen **1.7.1** | **5.3.1** ([docs](https://docs.prophesee.ai/stable/release_notes.html)) | **5.2.0** ([GitHub](https://github.com/prophesee-ai/openeb/releases/tag/5.2.0); open modules of the SDK, not the full 5.3.1 tree) | **0.17.0** ([PyPI](https://pypi.org/project/neuromorphic-drivers/) / [crates.io](https://crates.io/crates/neuromorphic-drivers)) |
| **Release date** | 3.5.0 draft 2026-09-14; 3.4.0 published 2026-09-04 | 1.7.2 / 2.0.3: **2026-02-19** | **2026-04-28** | **2026-01-13** | **2026-03-26** (crate) / **2026-03-28** (PyPI) |
| **Vendor / community** | Community: [Sensors Group, UZH-ETH / SensorsINI](https://sensors.ini.ch). Camera help from iniVation, inilabs, NRV, Prophesee | Vendor: [iniVation AG](https://inivation.com/) | Vendor: [Prophesee](https://www.prophesee.ai/) | Vendor-hosted open source (Prophesee) | Community: [neuromorphicsystems](https://github.com/neuromorphicsystems) / ICNS (Alexandre Marcireau) |
| **Open / closed / partly open** | **Open** ([LGPL-2.1](https://github.com/SensorsINI/jaer)) | **Partly open**: `dv-runtime` / `dv-processing` [Apache-2.0](https://gitlab.com/inivation/dv/dv-runtime); `dv-gui` listed as custom redistributable (AUR), source on [GitLab](https://gitlab.com/inivation/dv/dv-gui/) | **Closed / licensed**. From 5.0 the full SDK is not free; from 5.3 a **development license** by default, separate **commercial** license for production. Included with a Prophesee USB EVK; also sold as SDK Pro | **Open** (Apache-2.0 open modules of Metavision) | **Open** (MIT) |
| **Target** | Desktop application (Java). Not an embedded SDK | Desktop GUI **and** embedded `dv-runtime` (headless, remote GUI) | Desktop SDK + Studio. Jetson / other: compile OpenEB or SDK Pro from source | Desktop / other platforms you compile (Jetson, etc.) | Library (Python + Rust). Desktop or embedded if you wrap it |
| **Full GUI** | **Yes** — `AEViewer` (capture, playback, filters, biases, record) | **Yes** — `dv-gui` (projects, module graph, visualize / record / playback). GUI **x86_64 only** on Linux; macOS has Intel + ARM builds | **Yes** — [Metavision Studio](https://docs.prophesee.ai/stable/metavision_studio/) (Electron). **Not** in OpenEB. x64 only | **No** (Studio excluded). [`metavision_viewer`](https://docs.prophesee.ai/stable/samples/modules/stream/viewer.html) is a basic viewer / recorder | **No** official GUI |
| **CLI only?** | **No**. GUI app. Some Ant / helper scripts; no camera CLI product | **No**. GUI + CLI (`dv-runtime`, `dv-list-devices`, `dv-filestat`, …) | **No**. Studio + many CLI samples (`metavision_viewer`, file converters, …) | **Mostly CLI / samples** (`metavision_viewer`, HAL samples). No Studio | **Yes** (library; you write the program). Related: [davis346-recorder](https://github.com/neuromorphicsystems/davis346-recorder) |
| **Primary language** | Java (JDK 25 bundled in installers) | C++ runtime; Java GUI; Python via dv-processing | C++ / Python; Studio is Electron | C++ / Python | Rust + Python (wheels, no Metavision / libcaer / dv-processing) |
| **OS support (summary)** | Windows 10/11 x64, Linux x64, macOS Intel + Apple Silicon | Windows x64, macOS Intel + ARM, Linux (PPA/COPR/AUR). GUI not shipped for Linux ARM | Official: **Ubuntu 22.04 / 24.04 x64** and **Windows 11 x64**. **No macOS** | Same family as SDK if you compile; Windows 11 + Ubuntu documented. **No official macOS** | Linux x64 + ARM, macOS x64 + ARM, Windows x64 |
| **Linux** | Yes — `.sh` installer (not apt). USB unsandboxed | Yes — Ubuntu PPA, Fedora COPR, AUR, Gentoo. `dv-gui` x86_64; `dv-runtime` also arm64/armhf | Yes — apt from Prophesee JFrog (Ubuntu 22.04/24.04 amd64) | Yes — compile; Dockerfile | Yes — wheels / cargo |
| **Windows** | Yes — `.exe` (WinUSB / Zadig for some cameras) | Yes — vendor installer (x86_64) | Yes — Windows 11 installer (`Metavision_SDK_*_Setup.exe`). 5.3.1 dropped Windows 10 packages | Yes — compile on Windows 11 (5.2.0 dropped Win10 source install) | Yes — wheels (x64) |
| **Mac** | Yes — Intel + Apple Silicon DMGs. USB needs Homebrew `libusb` on Apple Silicon | Yes — Intel + ARM download pages | **No** official support | **No** official support | Yes — x64 + ARM (library) |
| **Signed installers on OS** | **Windows:** Authenticode (publisher Tobias Delbruck) from **3.5.0**. **macOS:** Developer ID + Apple notarization from **3.5.0**. **Linux:** unsigned `.sh` (usual) | **?** Windows/macOS vendor packages (not checked here). Linux packages from iniVation PPA/COPR | **Linux:** apt repo GPG-signed. **Windows:** vendor `.exe` (**?** Authenticode). **macOS:** n/a | Source tarballs; no OS-signed desktop installer | PyPI / crates; not OS installers |
| **Sensors supported (live USB)** | **iniVation / inilabs:** DAVIS346, DAVIS240, DVXplorer / Mini / Micro, DVS128. **Prophesee:** EVK4 HD (IMX636). **NRV:** DELTA01. **SensorsINI:** CDAVIS, SciDVS (experimental), CochleaAMS/LP. **OpenCV** UVC webcams as a second viewer. File playback covers more (see [file formats](README-file-formats.md)). Table: [README device list](../README.md#device-hardware-support) | **iniVation current:** DAVIS, DVXplorer / Mini / Micro. Also DVS128 module. Discontinued modules exist (DVS132, eDVS, Samsung EVK). **Not** Prophesee EVKs, **not** NRV | **Prophesee EVK3 320/HD, EVK4 HD** (5.3.1). Partner cameras via HAL plugins. Older EVK1/2 need older SDK. **Not** iniVation / NRV as first-party | Same Prophesee HAL plugins as open modules (Gen3.1 / Gen4.1 / IMX636 in recent OpenEB). EVK2 dropped in 5.2.0 | Prophesee **EVK4**, **EVK3 HD**; **IDS uEye XCP-E**; **SilkyEvCam HD**; iniVation **DVXplorer**, **DAVIS 346**. Independent USB protocol (not Metavision / DV) |
| **Record / play formats** | Record **AEDAT-4** (default, LZ4/ZSTD) or AEDAT-2. Play AEDAT-4/3/2/1, Metavision **RAW EVT3** / **DAT**, DSEC HDF5, ROS1 bag, CSV. Save As: AEDAT-4, CSV, DSEC HDF5. Export video MP4 | Record / play **AEDAT-4** (events, frames, IMU, triggers) | Record **RAW**; Studio export **HDF5** or **AVI**. CLI: DAT, CSV, HDF5 | RAW / HDF5 via open modules + `metavision_viewer` | None built-in (you serialize in Python/Rust) |
| **Source / docs** | [github.com/SensorsINI/jaer](https://github.com/SensorsINI/jaer), [User Guide](https://docs.google.com/document/d/1fb7VA8tdoxuYqZfrPfT46_wiT1isQZwTHgX8O22dJ0Q/edit?usp=sharing) | [docs.inivation.com](https://docs.inivation.com/software/dv/), GitLab `dv-runtime` / `dv-gui` / `dv-processing` | [docs.prophesee.ai](https://docs.prophesee.ai/stable/) (account for installers) | [github.com/prophesee-ai/openeb](https://github.com/prophesee-ai/openeb) | [github.com/neuromorphicsystems/neuromorphic-drivers](https://github.com/neuromorphicsystems/neuromorphic-drivers) |

---

## Features

Rows start from what jAER advertises, then a few capabilities that are stronger in other packages.

| Feature | jAER | DV | Metavision SDK / Studio | OpenEB | neuromorphic-drivers |
|---|---|---|---|---|---|
| Live USB capture | Yes | Yes (iniVation) | Yes (Prophesee / plugins) | Yes (Prophesee / plugins) | Yes (listed devices) |
| Bias / camera settings UI | Yes (per-chip) | Yes | Yes (Studio + JSON settings files) | Partial (API / viewer; no Studio panel) | Partial (Python config files; not a GUI) |
| Hardware ESP (anti-flicker, ERC, digital crop, …) | Partial (EVK4 init/ROI; not a full Studio ESP panel) | n/a on iniVation sensors in the same form | Yes (Studio + API) | Partial (HAL API; no Studio) | Partial (ROI / rate limiter on some devices; AF/noise filter still ☐ on EVK4) |
| 2D event display (polarity color) | Yes | Yes | Yes | Yes (`metavision_viewer`) | No (bring your own) |
| Accumulation / decay / sliding window | Yes (strong: color, fading, sliding window) | Yes (Accumulator module) | Yes (display timing in Studio) | Partial (viewer) | No |
| 3D space-time view | Yes | ? | ? | No | No |
| APS frames (DAVIS / HVS) | Yes | Yes (DAVIS) | n/a (no DAVIS) | n/a | Yes (DAVIS 346 frames in the stream) |
| IMU | Yes (DAVIS / DVX / recordings) | Yes | n/a on EVK4 as in DAVIS | n/a | Yes (DVX / DAVIS packets) |
| Software denoising | Yes (several EventFilters, NoiseTesterFilter) | Yes (noise-filter module) | Yes (advanced modules; trail filter also in-sensor) | Partial (open Core; advanced denoise is SDK) | No |
| Filter / module chain + auto UI | Yes (`EventFilter` / FilterPanel) | Yes (DV Modules graph + config bar) | Partial (Studio is capture/tune; algorithms are samples / API) | Partial (you write the pipeline) | No |
| Tracking / optical flow in-app | Yes (many project filters) | Partial (modules; not the same depth as jAER’s 20y filter set) | Yes (advanced CV modules — SDK, not OpenEB) | No (open modules are generic) | No |
| Record compressed long sessions | Yes (AEDAT-4 + **timed / rotating VCR**, merge on stop) | Yes (AEDAT-4; no VCR cassette UI known) | Yes (RAW; cut in Studio). No VCR-style rotate known | Yes (viewer record RAW) | No (DIY) |
| Playback with IN/OUT / markers / variable rate | Yes | Partial (file playback in Visualize) | Partial (Studio play RAW/HDF5) | Partial | n/a |
| Play other vendors’ files | Yes (AEDAT, RAW EVT3, DAT, DSEC h5, ROS bag, CSV) | Mostly AEDAT-4 | RAW / HDF5 / DAT (Prophesee). Not AEDAT | Same open formats as SDK open modules | n/a |
| Save As / clip / filtered export | Yes (background AEDAT-4 / CSV / HDF5) | ? | Yes (Studio cut + HDF5/AVI export; CLI converters) | Partial (CLI `metavision_file_to_*` if built) | n/a |
| Export video (MP4/AVI) | Yes (File → Export video, ffmpeg) | ? | Yes (AVI from Studio) | ? | No |
| Multi-camera sync record | Yes (File → Synchronize, muxed AEDAT-4) | Yes (`syncWith` / multi-cam in dv-processing 2) | Yes (`SyncedCamera` / master–slave in recent SDK) | Partial (open API; check samples) | ? |
| Second RGB / OpenCV camera muxed with events | Yes (OpenCV USB + event sensor → one AEDAT-4) | ? | ? | No | No |
| Sample recordings in-app | Yes (installer + Help → Sample data, ~1 GB) | ? | Yes (Prophesee sample RAW/HDF5) | Docs point at the same samples | No |
| Signed / notarized desktop installers | Yes from 3.5.0 (Win + Mac) | ? | Linux apt signed; Win installer vendor | No | n/a |
| One-click OS install (no compile) | Yes (install4j, bundled JRE) | Yes (exe / dmg / packages) | Yes if you have SDK credentials (Win exe / Ubuntu apt) | No (compile OpenEB) | `pip install` / cargo |
| Self-update | Yes (Help → Check for release updates) | ? | Via SDK packages | No | pip / cargo |
| Double-click file association | Yes (`.aedat` / `.aedat4`, single instance) | ? | ? | No | n/a |
| Python API | Partial (read `aedat` / h5 / CSV; live via Remote / mmap, not a native Python driver) | Yes (dv-processing) | Yes | Yes | Yes (first-class) |
| C++ API | No (Java) | Yes | Yes | Yes | Via Rust / bindings |
| Rust API | No | No | No | No | Yes |
| ROS / Foxglove | Yes (File → Remote ROS2 publisher) | ? | Historical ROS wrappers (**?** current 5.3) | ? | No |
| Serve frames to OpenCV / Zoom / v4l2 | Yes (HTTP MJPEG; Linux v4l2 loopback) | No | No | No | No |
| DNN shared-memory output | Yes (EventCountFrames / EventWindows) | No | ML is in SDK (Core ML / advanced); not the same mmap path | Partial (Core ML event_to_video / video_to_event) | No |
| Event-to-video / video-to-event ML | No in-app (external Python) | No | Yes (SDK / Core ML) | Yes (Core ML) | No |
| Calibration helpers | Partial (FOV sibling repo; some filters) | Yes (DV calibrate docs) | Yes (calibration samples — advanced module) | No | No |
| Silicon cochlea / audio AER | Yes | No | No | No | No |
| Extensible plugins | Yes (EventFilter2D / AEChip after `ant compile`) | Yes (dv-sdk modules) | Yes (HAL camera plugins) | Yes | New devices in-tree (Rust) |
| Embedded / headless runtime | No (desktop JVM) | Yes (`dv-runtime` without GUI) | Compile SDK / OpenEB | Yes (you compile) | Yes (library) |
| Vendor-neutral multi-brand USB | **Yes** (iniVation + Prophesee + NRV + OpenCV in one GUI) | No (iniVation family) | No (Prophesee + partner plugins) | No (Prophesee HAL) | **Partial** (Prophesee-class + DAVIS/DVX; no NRV / DVS128) |

---

## Notes

- **jAER 3.5.0** is the in-tree version (`VERSION.txt`) and a GitHub **draft** release on 2026-09-14. Feature rows for signed installers, VCR, and OpenCV cameras describe **3.5.0**; 3.4.0 is the last published tag without those installer signatures.
- **Metavision vs OpenEB:** Studio, advanced algorithms, and prebuilt installers are **SDK / SDK Pro**. OpenEB is Apache-2.0 HAL + Base + Core + Core ML + Stream + UI viewer.
- **DV vs jAER:** both speak **AEDAT-4**. DV is the iniVation-native stack (C++ runtime, module graph). jAER is the older SensorsINI desktop app and the only one in this table with **NRV**, **EVK4 + DAVIS in one GUI**, **cochleas**, and **VCR-style** recordings.
- **neuromorphic-drivers** is a *driver library*, not a replacement GUI. It is the right comparison for “open USB protocol, pip install, no vendor SDK.” jAER’s EVK4 path was guided by it.

---

## Candidates to add later

Not filled yet: `libcaer` / `libcaer-java`, `tonic`, `aestream`, `rpg_dvs_ros` / `dvs_msgs`, Celex / Century Arks tools, NRV vendor apps if any, v2e / ESIM (simulators).

---

## How to update

1. Bump **Last reviewed**.
2. Check GitHub / GitLab tags and [Metavision release notes](https://docs.prophesee.ai/stable/release_notes.html).
3. Prefer **Yes / Partial / No / ?** over marketing copy.
4. Keep jAER sensor lists in sync with the [README device table](../README.md#device-hardware-support) and [file formats](README-file-formats.md).
