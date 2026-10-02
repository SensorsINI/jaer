<!--
  Paste-ready for GitHub Releases. Image links use raw.githubusercontent.com so they
  render in the Release body. Relative paths work in the repo, not in a Release description.

  Public 3.6.0 notes. There is no public 3.5.4.
  New features: live playback while recording, and low-latency processing.
  Then packaging and bug fixes since 3.5.3.
  Do not paste the OS how-to; link the install guide.
-->

**jAER 3.6.0** adds two useful features: **Live playback while recording** and **low-latency processing** on the USB acquisition cycle, after **[3.5.3](https://github.com/SensorsINI/jaer/releases/tag/3.5.3)**. 

The rest of the release is packaging and bug fixes: installers show the sample-recording size and count the unpacked files in the disk-space check. The viewer warns when timestamps freeze, loads shipped biases when you pick a sensor, points event drops at the rendering buffer and pauses USB while Save-As dialog shows (to prevent OOM during unattended recordings).

## Download

| You have | CPU | Download |
|---|---|---|
| Windows 10 / 11 | x64 | [jAER_windows-x64_3_6_0.exe](https://github.com/SensorsINI/jaer/releases/download/3.6.0/jAER_windows-x64_3_6_0.exe) |
| macOS | Apple Silicon (M1–M4) | [jAER_macos_aarch64_3_6_0.dmg](https://github.com/SensorsINI/jaer/releases/download/3.6.0/jAER_macos_aarch64_3_6_0.dmg) |
| macOS | Intel | [jAER_macos_3_6_0.dmg](https://github.com/SensorsINI/jaer/releases/download/3.6.0/jAER_macos_3_6_0.dmg) |
| Linux | x64 | [jAER_unix_3_6_0.sh](https://github.com/SensorsINI/jaer/releases/download/3.6.0/jAER_unix_3_6_0.sh) |
| Linux | x64 `.deb` | [jAER_linux-amd64_3_6_0.deb](https://github.com/SensorsINI/jaer/releases/download/3.6.0/jAER_linux-amd64_3_6_0.deb) |
| Any OS | Sample data (~995 MB) | [jaer-sample-data.zip](https://github.com/SensorsINI/jaer/releases/download/3.6.0/jaer-sample-data.zip) ([README](https://github.com/SensorsINI/jaer/blob/master/jaerSampleData/README.md)) |

Each installer includes a bundled [Eclipse Temurin](https://adoptium.net/) JDK 25. You do not install Java yourself. GitHub lists the same files again under **Assets**.

Steps for Windows, macOS, and Linux are on the **[Install Guide](https://jaerproject.org/install/)**. The homepage Download button stays the usual installer (the Linux button is still the `.sh`).

The `.deb` installs into `/opt/jAER` and adds a GNOME menu entry named **jAER**. Start it from that menu or with `/opt/jAER/jaer`. There is no `apt install jaer` by name.

`winget install SensorsINI.jAER` and `brew install --cask jaer` work after the winget catalog PR for this version is merged and the Homebrew tap `sensorsini/jaer` is updated. On Homebrew 7, trust the tap before the short name works (`brew trust --cask sensorsini/jaer/jaer`). Until then, use the downloads above. The winget installer passes install4j `-q`, so an unattended install does not stop on the GUI.

jAER can self-update from the `.exe`, `.dmg`, and `.sh` (Help → Check for release updates… → **Download and install**). A `.deb`, winget, or Homebrew install hides that button; upgrade those with a newer `.deb`, `winget upgrade SensorsINI.jAER`, or `brew upgrade --cask jaer`.

## Installers

The Welcome screen **Download sample recordings** checkbox shows the zip size, the unpacked size, and a time estimate. Sizes come from [`jaerSampleData/SIZE.txt`](https://github.com/SensorsINI/jaer/blob/master/jaerSampleData/SIZE.txt). When the box is checked, the destination screen adds the unpacked size to the disk-space check. The zip downloads while the application files are copied, then unpacks into `jaerSampleData` before Finish, with no extra prompt. Cancel on that wait skips the recordings; jAER is already installed.

**Help → Sample data** and **File → Open** put recordings in a folder named `jaerSampleData` (an older `sampleData` folder is renamed when the new name is free). Help → **jAER home page** opens [jaerproject.org](https://jaerproject.org/). The git updater is no longer on the Help menu.

On [jaerproject.org](https://jaerproject.org/), the Snapshot line shows the real build date and a link to commits since the current Stable release.

## New features

**Live playback while recording.** While one viewer is writing its own AEDAT-4 file, the position scrubber stays under the canvas. The sparkline is a 1-second activity histogram, and the view stays on the camera until you scrub behind the live edge or jog backward. Playback reads the file written so far; recording continues. **Back to live**, next to **Stop recording**, returns the view to the camera without closing the file. Pause, step, and the slider affect playback only. This is for one viewer’s own AEDAT-4 recording (not AEDAT-2 or AEDZ, and not a synchronized multi-camera file). The first version plays the current cassette; a VCR roll follows the new file.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.6.0/live-playback.webp" alt="Scrubbing an AEDAT-4 recording while it is still being written, then returning to the live camera" width="70%" />

**Low-latency processing on the USB acquisition cycle.** Filters → Options → **Process on acquisition cycle** runs the chain on each new USB request block (URB), **on the high priority USB thread**. This mode allows system latencies of <1ms when the event camera and USB computer output device (e.g. Arduino microcontroller) use at least USB 2.0 high speed mode, and even smaller latencies when both camera and system output are USB3.x devices.  This low-latency mode was used for many of the <a href="https://github.com/SensorsINI/jaer#jaer-applications">jAER demonstrator robots</a>, e.g. the well-known robot goalie, slot car racer, and Trixsy card magic robot.

A label under the chip (**Low-latency mode**) shows the mean ± standard deviation of the last 100 filter chain processing intervals in seconds, along with the mean rate in Hz: `1/mean`. Turn off this overlay with **Show acquisition cycle overlay**. See the [Low-Latency section](https://docs.google.com/document/d/1fb7VA8tdoxuYqZfrPfT46_wiT1isQZwTHgX8O22dJ0Q/edit?tab=t.0#bookmark=id.og4z2km4xd8z) of the jAER User Guide.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.6.0/low-latency-menu-item.png" alt="Filters Options menu with Process on acquisition cycle selected" width="70%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.6.0/low-latency-cycle-stats-display.png" alt="Low-latency mode overlay with interval mean, standard deviation, and rate" width="70%" />

## Fixes

* **Timestamps frozen.** A blinking red caption appears when live event timestamps stop advancing.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.6.0/timestamps-frozen-warning.png" alt="Blinking red Timestamps frozen caption on the chip view" width="50%" />

* **Shipped biases.** Choosing a sensor with nothing plugged in, or switching chips while the viewer is already live, loads the shipped bias file. Hardware Configuration no longer opens with every pot at zero.
* **Event drops.** A red **(DROP)** or **(overrun)** on the status line tells you to raise **Render events** in USB tuning (and **Live keep** on Prophesee), or to raise the DVS threshold or refractory period. USB → **USB tuning…** Help describes FIFO size, buffer count, and the IN statistics.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.6.0/dropping-events.png" alt="Dropping events overlay beside USB tuning Render events" width="50%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.6.0/usb-tuning.png" alt="USB tuning window with FIFO, buffers, Render events, and USB IN statistics" width="50%" />

* **Function keys.** Page Up, Page Down, Home, and End show a notice: those keys are not shortcuts. If you meant the arrow keys (contrast and render rate), turn off function-key mode.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.6.0/function-key-warning.png" alt="Notice that Page Up is not a shortcut and to turn off function-key mode" width="50%" />

* **Remote output.** OpenCV and ROS slice settings take effect on the next frame.
* **Preferences.** The preferences dialog uses a narrower layout.
