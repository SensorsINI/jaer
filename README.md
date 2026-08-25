# jAER

**Welcome to the jAER Desktop Application for Event Sensors**

[See latest jAER release](https://github.com/SensorsINI/jaer/releases/latest). **Permanent link:** http://jaerproject.org

![jAER supported cameras](/images/supported-cameras-annotated.jpg)

**Why use proprietary vendor camera software?** jAER is a full-featured cross-platform (Linux, Windows, MacOS) Desktop Java Application for Address-Event Representation (AER) neuromorphic event cameras and silicon cochleas from all major manufacturers ([inilabs](https://inilabs.com), [iniVation](https://inivation.com/), [Prophesee](https://www.prophesee.ai/), and [NRV](https://www.nrv.kr/)). 

(You get plug-and-play, "it just works" for all the cameras above.) 

jAER efficiently 
 * captures USB event camera output from [most devices](#device-hardware-support), 
 * denoises -- with fast, accurate event filtering algorithms
 * displays -- with a variety of color and 2d/3d options
 * records -- in a high-speed compressed format (AEDAT-4)
 * plays back -- a big variety of [formats](docs/README-file-formats.md), with flexible time/event rate, markers, and IN/OUT points
 * allows complex post camera algorithmic processing of the device output stream (see [jAER Applications](#jaer-applications)).

It can record your datasets and serve recorded or live event sensor output to 
 * your favorite DNN (File → Remote → **DNN shared memory output…**, filter [`DNNOutputViaSharedMemory`](https://github.com/SensorsINI/jaer/blob/master/src/net/sf/jaer/util/avioutput/DNNOutputViaSharedMemory.java)) 
 * or robot running [ROS2](https://www.ros.org/) / [Foxglove](https://foxglove.dev/) back-end (File → Remote → ROS2 / Foxglove frame output…; Foxglove Studio connects to `ws://127.0.0.1:8765` with no ROS2 install).


![jAER demo](/images/using_jaer_2021-01-22_08-16-47_1.gif)
*What it looks like to interact with jAER*


## Installation

You can find the latest releases and binary [install4j](https://www.ej-technologies.com/products/install4j/overview.html) installers at <https://github.com/SensorsINI/jaer/releases>. See video [installing and updating jaer on YouTube](https://youtu.be/qQVt8_gwYVY).

**Installers are GitHub Release assets** (~200 MB each for 3.3.0, bundled [Eclipse Temurin](https://adoptium.net/) from Adoptium — 3.3.0 is Temurin **25**; 3.2.0 was Temurin 21). Older installers remain on [Dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0) (`jaer-older-installers`).

 * **Linux:** Run the installer with `sh <installer>.sh`. Then you can *jaer* from the installation directory or gnome menu. Official apt is not provided (USB cameras need an unsandboxed install); optional `.deb` notes are in [`packaging/deb`](packaging/deb).

 * **Windows:** For current unsigned builds: Click *More info*, *Run anyway* and *Install anyway*. Later: `winget install SensorsINI.jAER` (manifests in [`packaging/winget`](packaging/winget); submit to winget-pkgs after the exe is on GitHub).

 * **MacOS:** See [opening unsigned dmg on MacOS](https://support.apple.com/guide/mac-help/open-a-mac-app-from-an-unidentified-developer-mh40616/mac). Right click, open with Archive Manager, and run the installer. Recommend to install to a user folder. **Apple Silicon:** USB cameras (and jAER startup) need Homebrew [libusb](https://formulae.brew.sh/formula/libusb): `brew install libusb`. `ant run` installs it when Homebrew is present. Homebrew cask (own tap first): see [`packaging/homebrew`](packaging/homebrew).

Installed copies (not git checkouts) can **Download and install** from Help → Check for release updates…. 

* install4j installers bundle [Eclipse Temurin](https://adoptium.net/) from Adoptium. `ant run` from a git clone needs [JDK 25+](https://adoptium.net/). See [Guide to Java versions and features](https://www.marcobehler.com/guides/a-guide-to-java-versions-and-features).
* you can [initialize the release to a git working copy and pull+build within jAER](https://youtu.be/qQVt8_gwYVY). 


## Quick start sample data

* Download [DVS128 data files from the DVS09 dataset](https://docs.google.com/document/d/16b4H78f4vG_QvYDK2Tq0sNBA-y7UFnRbNnsGbD1jJOg/edit?usp=sharing) and
drop them onto the jAER window to play them with the *DVS128* *AEChip*.
* Download [DAVIS346 sample data files from the DAVIS24 dataset](https://sites.google.com/view/davis24-davis-sample-data/home) and
drop them onto the jAER window to play them with the *Davis346blue* *AEChip*.
* See the *Help/Sample Data* menu in jAER for more sample data.
* See [`docs/README-file-formats.md`](docs/README-file-formats.md) for detailed information about supported file formats in jAER.

## Device hardware support

Live USB cameras selectable in the AEViewer **AEChip** menu (default list and related variants). Live USB operation is verified on **Windows, macOS (including Apple Silicon), and Linux**. File playback for many more sensors is listed in [`docs/README-file-formats.md`](docs/README-file-formats.md). Status is the chip’s `@DevelopmentStatus` (`Stable` or `Experimental`).


| Camera / product | Manufacturer | Sensor / resolution | Interface | jAER chip class(es) | Status |
|------------------|--------------|---------------------|-----------|---------------------|--------|
| **DAVIS346** (red/blue/color) | [iniVation](https://inivation.com/) | APS+DVS 346×260 | USB 3 (FX3) | `Davis346red`, `Davis346blue`, `Davis346redColor`, … | Stable |
| **DAVIS240** (A/B/C) | [iniVation](https://inivation.com/) / [inilabs](https://inilabs.com/) | APS+DVS 240×180 | USB 2/3 | `DAVIS240C`, `DAVIS240B`, … | Stable (`DAVIS240C`); Experimental (`DAVIS240A`/`B`) |
| **DVXplorer** | [iniVation](https://inivation.com/) | DVS (Samsung S5K231Y) 640×480, 9 µm | USB 3 (FX3) | `DVXplorer` | Stable |
| **DVXplorer Mini** | [iniVation](https://inivation.com/) | same Samsung DVS 640×480, 9 µm | USB 3 (CX3 MIPI) | `DVXplorer` | Stable |
| **DVXplorer Micro** | [iniVation](https://inivation.com/) | same Samsung DVS 640×480, 9 µm | USB-C (CX3 MIPI) | `DVXplorer` | Stable |
| **DVS128** | [inilabs](https://inilabs.com/) / [SensorsINI](https://sensors.ini.ch) | DVS 128×128 | USB 2 (FX2) | `DVS128` | Stable |
| **EVK4 HD** | [Prophesee](https://www.prophesee.ai/) | Sony IMX636 DVS 1280×720 | USB 3 (Cypress) | `PropheseeIMX636HD` | Stable ([notes](src/prophesee/README.md)); also Metavision `.raw` EVT3 playback |
| **DELTA01** | [NRV](https://nrvcorp.github.io/docs/) | Samsung S5KRC1S DVS 960×720 | USB 3 (FX20/CX3) | `NRVS5KRC1S` | Stable ([notes](src/nrv/README.md)) |
| **CDAVIS** | [SensorsINI](https://sensors.ini.ch) / [iniVation](https://inivation.com/) | Color APS+DVS 640×480 / 320×240 DVS | USB 3 | `CDAVIS` | Experimental |
| **SciDVS** | [SensorsINI](https://sensors.ini.ch) | Sensitive 100x114 DVS / Basic APS  | USB 3 | `SciDVS` | Experimental |
| **CochleaAMS / CochleaLP** | [SensorsINI](https://sensors.ini.ch) / [inilabs](https://inilabs.com/) | Silicon cochlea (audio AER) | USB 2/3 | `CochleaAMS1c`, `CochleaLP`, … | Stable (`CochleaAMS1c`); Experimental (`CochleaLP`) |
| Generic DVS viewers | — | 640×480, 1280×720 | Playback / viz | `DVS640`, `DVS1280x720SD` | Stable |

Stereo and multi-camera wrappers (e.g. `DVS128StereoPair`, `MultiDAVIS346BCameraChip`) combine several of the above over separate USB interfaces.

Hardware docs in Help menu: iniVation cameras, Prophesee sensors, NRV cameras.

**FOV calculator:** estimate field of view from pixel pitch, array size, and lens focal length. Lives in the sibling repo [`SensorsINI/lensFOV`](https://github.com/SensorsINI/lensFOV) (local checkout `../lensFOV` next to this `jaer` folder). Open `../lensFOV/index.html` locally; after Pages is enabled it will be at [sensorsini.github.io/lensFOV](https://sensorsini.github.io/lensFOV/).

## Citation and credits

jAER was developed since 2007 by the [Sensors Group, Inst. of Neuroinformatics, UZH-ETH Zurich](https://sensors.ini.ch) to support event sensors and robot demonstrators.

T. Delbruck, “Frame-free dynamic digital vision,” 
in International Symposium on Secure-Life Electronics, University of Tokyo, 
Mar. 2008, pp. 21–26. 
doi: 10.5167/uzh-17620. Available: http://dx.doi.org/10.5167/uzh-17620

jAER relies on many open source projects, including:

- [Eclipse Temurin (Adoptium)](https://adoptium.net/) – OpenJDK Java runtime bundled in installers.
- [JOGL (Java OpenGL)](https://jogamp.org/jogl/www/) – Java OpenGL bindings for visualization and display.
- [libusb](https://libusb.info/) – User space USB library for cross-platform device access.
- [Apache Ant](https://ant.apache.org/) – Java-based build tool.
- [Apache Ivy](https://ant.apache.org/ivy/) – Dependency manager integrated with Ant.
- [JNA (Java Native Access)](https://github.com/java-native-access/jna) – Java wrapper for native libraries.
- [JUnit](https://junit.org/) – Unit testing framework.
- [jna-platform](https://github.com/java-native-access/jna/tree/master/contrib/platform) – Platform-specific native library access.
- [JCodec](https://github.com/jcodec/jcodec) – Pure Java video/audio encoding/decoding.
- [Guava](https://github.com/google/guava) – Google core Java libraries.
- and many more.

We thank the developers and contributors to these and other open source projects that make jAER possible.

### jAER applications
jAER originally targetted characterization of Sensors Group [event cameras and silicon cochleas](https://sensors.ini.ch/research/event-sensors),
but has also been used to build many robots:

1. [robogoalie](https://youtu.be/IC5x7ftJ96w?si=ajsJWWYJW-tSJ2MI) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/tobi/goalie/Goalie.java))
2. [audio localization by spike ITD](https://www.youtube.com/watch?v=-Klbmm4vgew) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/cochsoundloc/ITDFilter.java))
3. [speaker identification from spiking cochlea](https://www.youtube.com/watch?v=KFPi65WV-S8) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/speakerid/CochleaSVMTwoEars.java))
4. [laser goalie](https://www.youtube.com/watch?v=5c5W18nuPQk) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/tobi/goalie/LaserGoalie.java))
5. [pencil balancer](https://www.youtube.com/watch?v=yCOnDc5r7p8) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/pencilbalancer/PencilBalancer.java))
6. [bill (money) catcher](https://www.youtube.com/watch?v=XtOS7jZzMaU) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/tobi/billcatcher/BillCatcher.java))
7. [slot car racer](https://www.youtube.com/watch?v=CnGPGiZuFRI) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/virtualslotcar/SlotCarRacer.java))
8. [Dextra roshambo (rock-scissors-paper)](https://www.youtube.com/watch?v=95GsOQbwNLU) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/npp/RoShamBoCNN.java)) — hello world: File → Remote → **DNN shared memory output…** (`DNNOutputViaSharedMemory`, `outputMode=EventCountFrames`) and run [dextra-roshambo-python](https://github.com/SensorsINI/dextra-roshambo-python) `consumer.py --jaer-mmap` (see [3.2.0 notes](release-notes/jaer-3.2.0-release-notes.md))
9. [incremental learning of new roshambo hand symbols](https://www.youtube.com/watch?v=uVruhxYu5gc) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/npp/RoShamBoIncremental.java))

jAER was also used to develop many event camera algorithms, including:

1. [Feature extraction](https://www.youtube.com/watch?v=IEsMkIpCE1o) ([code](https://github.com/SensorsINI/jaer/blob/master/src/net/sf/jaer/eventprocessing/label/SimpleOrientationFilter.java))
2. [tracking](https://www.youtube.com/watch?v=5I6haFXVuD8) ([code](https://github.com/SensorsINI/jaer/blob/master/src/net/sf/jaer/eventprocessing/tracking/RectangularClusterTracker.java))
3. [optical flow methods](https://www.youtube.com/watch?v=Ji1MzE4QbM4) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/rbodo/opticalflow/AbstractMotionFlowIMU.java))
4. [EDFLOW hardware optical flow](https://www.youtube.com/watch?v=8LedyiHMe_A) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/minliu/PatchMatchFlow.java))
5. [efficient and accurate event denoising](https://sites.google.com/view/dnd21/home?authuser=0) ([code](https://github.com/SensorsINI/jaer/blob/master/src/net/sf/jaer/eventprocessing/filter/NoiseTesterFilter.java))

## Developing with jAER

To develop with jAER, see the [jAER User Guide gdoc](https://docs.google.com/document/d/1fb7VA8tdoxuYqZfrPfT46_wiT1isQZwTHgX8O22dJ0Q/edit?usp=sharing), or the [Developing with jAER](#developing-with-jaer) section above for setup and build instructions.

### Developing in an LLM AI client (Cursor, VS Code, …)

jAER is an Ant + Ivy Java project (not Maven/Gradle). An AI coding client works well for navigation, edits, and agents if you treat **Ant as the source of truth for builds**.

1. **Install a JDK 25+** (for example [Eclipse Temurin](https://adoptium.net/)) and [Apache Ant](https://ant.apache.org/), both on your `PATH`. `javac` still targets 21; `ant run` needs 25+ for `-XX:+UseCompactObjectHeaders`.
2. **Install the Java extension in Cursor / VS Code.** Prefer Microsoft’s [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) (or at least [Language Support for Java](https://marketplace.visualstudio.com/items?itemName=redhat.java)). Without it, Java navigation, launch configs, and agent context are much weaker.
3. Open the repo root as the workspace. First-time build:

   ```bash
   ant compile
   ```

   Then run with `ant run`, or the fast scripts `scripts/run-jaer-fast.bat` (Windows) / `scripts/run-jaer-fast.sh` (Linux/macOS) after classes exist under `build/classes`. New `AEChip` / `EventFilter2D` classes appear in Customize only after `ant compile` (that writes an allowlist into `jAER.jar`); IDE compile-on-save is not enough. Packaged installers load only types from that list. To force a classpath rescan from a git tree, use `-Djaer.scanClasspath=true`.

   To test `dist/jAER.jar` inside an already-installed install4j copy (no installer rebuild): `ant jar-fast replace-installed-jar`. The task reads `applicationId` / `shortName` from `install4j/jaer.install4j` (Windows: install4j registry, else `C:\Program Files\jAER`). On Windows, if Program Files is not writable, PowerShell shows a UAC prompt (`Start-Process -Verb RunAs`) and waits; approve it in the Cursor terminal session. Override with `-Djaer.install.dir=...`. Close `jaer.exe` first. A `jAER.jar.bak` is left next to the replaced file.
4. **Prefer Ant over the IDE compiler for packaging a runnable tree.** The Java language server can leave Eclipse-style stub `.class` files (`Unresolved compilation problem`) under `build/classes` if its output path overlaps Ant’s. This repo’s VS Code settings disable Java autobuild and point output at `build/classes`; if launch fails with that error, run `ant clean` then `ant compile`.
5. Use the included `.vscode/launch.json` configs (**jAER**, **jAER (fast)**, **jAER (fast debug)**) once dependencies are in `lib/` (created by Ivy on `ant compile` / `ant run`).

Ask the agent for Ant targets, chip/filter code under `src/`, and device USB notes rather than inventing a Maven layout.

## Code signing policy

Free code signing provided by [SignPath.io](https://signpath.io), certificate by [SignPath Foundation](https://signpath.org/).

Windows installers submitted for signing are built from this repository on GitHub Actions (see [`.github/workflows/sign-windows-test.yml`](.github/workflows/sign-windows-test.yml) and [`README-releasing-tagging.md`](README-releasing-tagging.md)). Publisher identity on signed builds is **SignPath Foundation**.

**Team roles**

* **Authors / reviewers:** [SensorsINI/jaer](https://github.com/SensorsINI/jaer) maintainers with commit access (pull requests reviewed by a team member when required).
* **Approvers:** repository owners / maintainers who approve SignPath release signing requests in the SignPath UI.

**Privacy:** This program will not transfer any information to other networked systems unless specifically requested by the user or the person installing or operating it (for example opening a camera, downloading sample data, or using optional online Help links).

## Support

Please use our GitHub bug tracker to report issues and bugs, or our Google Groups mailing list forum to ask questions.

* **USER GUIDE:** [jAER User Guide gdoc](https://docs.google.com/document/d/1fb7VA8tdoxuYqZfrPfT46_wiT1isQZwTHgX8O22dJ0Q/edit?usp=sharing)
* **VIDEO TUTORIALS:** https://www.youtube.com/playlist?list=PLVtZ8f-q0U5hD9KOM4OZ1lixhwupj9uOm
* **BUG TRACKER:** https://github.com/SensorsINI/jaer/issues/
* **USER FORUM:** https://groups.google.com/d/forum/jaer-users/

See also
* **DAVIS-USERS user forum:** https://groups.google.com/forum/#!forum/davis-users
* **inivation support pages:** https://inivation.com/support/

![Hotel bar scene with DAVIS140C](/images/HotelBarDavis.png)
