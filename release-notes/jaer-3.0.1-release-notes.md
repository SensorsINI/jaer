Go to [install4j jAER installers on Dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0) to download installers.

See video [installing and updating jaer on YouTube](https://youtu.be/qQVt8_gwYVY).

**jAER 3.0.1** builds on the 3.0 typed `PacketBundle` / AEDAT-4 foundation with **much smaller installers** (~3× smaller vs 3.0.0 on all platforms), **on-demand TensorFlow** for MLPNoiseFilter, **automatic AEChip matching** when a USB camera is plugged in, and live USB demux into typed PacketBundles for Davis FX3, NRV, Prophesee, DVS128, and SciDVS. Includes the NRV control-panel update from [#55](https://github.com/SensorsINI/jaer/pull/55) (external trigger + global reset/hold) and several AEDAT-4 / export / 3D-view fixes.

### Highlights

* **Smaller installers** — OS-specific TensorFlow JNI jars, repo `images/`, generated javadoc, and Ivy test-scope leftovers (GraalJS, ScalaTest, …) are no longer packed into media. Upgrade leftovers such as `javacpp-1.4.jar` are removed on install.
* **On-demand TensorFlow for MLPNoiseFilter** — first use prompts to download the current-OS native jar from Maven Central into `lib/` or `~/.jaer/lib/` (~74 MB on Windows); a restart may be required.

![Download TensorFlow natives for MLPNoiseFilter](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.0.1/auto-tensorflow-install.png)

* **AEChip for USB device** — when a live USB device’s VID/PID matches chips in the AEChip menu, jAER offers to switch (or choose among matches) before opening the interface. Help menu links for iniVation / Prophesee / NRV camera docs.

![AEChip chooser for USB VID/PID](https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.0.1/auto-chip-select.png)

* **Live USB → typed PacketBundles** — Davis FX3 / SciDVS, NRV, Prophesee EVT3, and DVS128 libusb paths emit polarity PacketBundles at the USB reader (shared demux helpers), aligning live capture with the 3.0 filter/render pipeline.

### Features

* **Installer / TensorFlow**
  * Ivy keeps `tensorflow-core-api` + unclassified native stub + **javacpp 1.5.10** (required by TF Java); platform natives download via `TensorFlowNativeSupport`.
  * install4j excludes large optional jars; post-install script deletes known upgrade leftovers; launcher classpath prefers javacpp 1.5.10; bundled Temurin JRE updated to **21.0.12**.
  * `ant release` / splash / `VERSION.txt` workflow unchanged; release packaging docs updated.

* **USB / cameras**
  * Offer matching **AEChip** from USB VID/PID (`LiveDeviceChipDetector` / AEViewer prompt).
  * Live PacketBundle demux for Davis FX3, SciDVS, NRV, Prophesee, DVS128.
  * **NRV control panel ([#55](https://github.com/SensorsINI/jaer/pull/55))** — thanks [@alster1028](https://github.com/alster1028):
    * **External Trigger** panel: enable external trigger-in; modes Single / Burst / Burst Single (register 0x3A02).
    * **Global reset / hold** checkboxes for 0x320C, separated from the scan-rate slider (some scan-rate values stopped capture when those bits were tied to it).
    * Tooltips for global settings and timing controls.
  * NRV LibUsb: clearer open/claim errors (prefer WinUSB via Zadig); Interface menu still lists NRV when a probe `open` returns ACCESS.
  * Live USB acquisition bench / launcher `-D` handling for developer measurements.

* **AEDAT-4 / playback / export**
  * Restore and persist playback marks for AEDAT-4 files.
  * Fix AEDAT-4 logging timestamps when filters mark events `filteredOut`.
  * Synchronize **Export video…** to the AEViewer target frame rate; deselect writer after export ends.

* **Filters / rendering**
  * HotPixelFilter on `processPolarity` for typed DVS packets; filter performance windows restored on the PacketBundle path.
  * SpaceTimeRolling: frame z-range and event pruning fixes.
  * NoiseTesterFilter / denoising study labeling WIP on typed polarity path.

* **Developer**
  * Prefer `scripts/` launchers; remove obsolete local launcher wrappers.
  * Draft `docs/README-jaer3.md` for the jaer3 architecture.

### Bug fixes and minor improvements

* Fixed NRV user-panel scanrate slider incorrectly writing register **0x320C** (global reset/hold); that register is now controlled only by the dedicated checkboxes ([#55](https://github.com/SensorsINI/jaer/pull/55)).
* Fixed spurious NRV “uninitialized config” warning.
* Fixed MLPNoiseFilter so TensorFlow linkage Errors are caught (no EDT crash) when natives/javacpp are missing or wrong.
* Silenced javac options lint when building on JDK 25+ with `-source`/`-target` 21.
* install4j leftover-cleanup **Run script** action corrected for install4j 13 (`actions.control.RunScriptAction`).

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/3.0.0...3.0.1

