<!--
  Paste-ready for GitHub Releases. Image links use raw.githubusercontent.com so they
  render in the Release body. Relative paths work in the repo, not in a Release description.

  Public 3.5.4 notes. New feature: low-latency processing. Then packaging and bug fixes after 3.5.3.
  Do not paste the OS how-to; link the install guide.
-->

**jAER 3.5.4** adds **low-latency processing** on the USB acquisition cycle, after **[3.5.3](https://github.com/SensorsINI/jaer/releases/tag/3.5.3)**. The rest of the release is packaging and bug fixes: installers show the sample-recording size and count the unpacked files in the disk-space check. The viewer warns when timestamps freeze, loads shipped biases when you pick a sensor, and points event drops at the rendering buffer.

## Download

| You have | CPU | Download |
|---|---|---|
| Windows 10 / 11 | x64 | [jAER_windows-x64_3_5_4.exe](https://github.com/SensorsINI/jaer/releases/download/3.5.4/jAER_windows-x64_3_5_4.exe) |
| macOS | Apple Silicon (M1–M4) | [jAER_macos_aarch64_3_5_4.dmg](https://github.com/SensorsINI/jaer/releases/download/3.5.4/jAER_macos_aarch64_3_5_4.dmg) |
| macOS | Intel | [jAER_macos_3_5_4.dmg](https://github.com/SensorsINI/jaer/releases/download/3.5.4/jAER_macos_3_5_4.dmg) |
| Linux | x64 | [jAER_unix_3_5_4.sh](https://github.com/SensorsINI/jaer/releases/download/3.5.4/jAER_unix_3_5_4.sh) |
| Linux | x64 `.deb` | [jAER_linux-amd64_3_5_4.deb](https://github.com/SensorsINI/jaer/releases/download/3.5.4/jAER_linux-amd64_3_5_4.deb) |
| Any OS | Sample data (~995 MB) | [jaer-sample-data.zip](https://github.com/SensorsINI/jaer/releases/download/3.5.4/jaer-sample-data.zip) ([README](https://github.com/SensorsINI/jaer/blob/master/jaerSampleData/README.md)) |

Each installer includes a bundled [Eclipse Temurin](https://adoptium.net/) JDK 25. You do not install Java yourself. GitHub lists the same files again under **Assets**.

Steps for Windows, macOS, and Linux are on the **[Install Guide](https://jaerproject.org/install/)**. The homepage Download button stays the usual installer (the Linux button is still the `.sh`).

The `.deb` installs into `/opt/jAER` and adds a GNOME menu entry named **jAER**. Start it from that menu or with `/opt/jAER/jaer`. There is no `apt install jaer` by name.

`winget install SensorsINI.jAER` and `brew install --cask jaer` work after the winget catalog PR for this version is merged and the Homebrew tap `sensorsini/jaer` is updated. On Homebrew 7, trust the tap before the short name works (`brew trust --cask sensorsini/jaer/jaer`). Until then, use the downloads above. The winget installer passes install4j `-q`, so an unattended install does not stop on the GUI.

jAER can self-update from the `.exe`, `.dmg`, and `.sh` (Help → Check for release updates… → **Download and install**). A `.deb`, winget, or Homebrew install hides that button; upgrade those with a newer `.deb`, `winget upgrade SensorsINI.jAER`, or `brew upgrade --cask jaer`.

## Installers

The Welcome screen **Download sample recordings** checkbox shows the zip size, the unpacked size, and a time estimate. Sizes come from [`jaerSampleData/SIZE.txt`](https://github.com/SensorsINI/jaer/blob/master/jaerSampleData/SIZE.txt). When the box is checked, the destination screen adds the unpacked size to the disk-space check. The zip downloads while the application files are copied, then unpacks into `jaerSampleData` before Finish, with no extra prompt. Cancel on that wait skips the recordings; jAER is already installed.

**Help → Sample data** and **File → Open** put recordings in a folder named `jaerSampleData` (an older `sampleData` folder is renamed when the new name is free). Help → **jAER home page** opens [jaerproject.org](https://jaerproject.org/). The git updater is no longer on the Help menu.

On [jaerproject.org](https://jaerproject.org/), the Snapshot line shows the real build date and a link to commits since the current Stable release.

## New feature

**Low-latency processing on the USB acquisition cycle.** Filters → Options → **Process on acquisition cycle** runs the chain on each new USB request block (URB), **on the high priority USB thread**. This mode allows system latencies of <1ms when the event camera and USB computer output device (e.g. Arduino microcontroller) use at least USB 2.0 high speed mode, and even smaller latencies when both camera and system output are USB3.x devices.  This low-latency mode was used for many of the <a href="https://github.com/SensorsINI/jaer#jaer-applications">jAER demonstrator robots</a>.

A label under the chip (**Low-latency mode**) shows the mean ± standard deviation of the last 100 filter chain processing intervals in seconds, along with the mean rate in Hz: `1/mean`. Turn off this overlay with **Show acquisition cycle overlay**. See the [Low-Latency section](https://docs.google.com/document/d/1fb7VA8tdoxuYqZfrPfT46_wiT1isQZwTHgX8O22dJ0Q/edit?tab=t.0#bookmark=id.og4z2km4xd8z) of the jAER User Guide.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.4/low-latency-menu-item.png" alt="Filters Options menu with Process on acquisition cycle selected" width="70%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.4/low-latency-cycle-stats-display.png" alt="Low-latency mode overlay with interval mean, standard deviation, and rate" width="70%" />

## Fixes

* **Timestamps frozen.** A blinking red caption appears when live event timestamps stop advancing.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.4/timestamps-frozen-warning.png" alt="Blinking red Timestamps frozen caption on the chip view" width="50%" />

* **Shipped biases.** Choosing a sensor with nothing plugged in, or switching chips while the viewer is already live, loads the shipped bias file. Hardware Configuration no longer opens with every pot at zero.
* **Event drops.** A red **(DROP)** or **(overrun)** on the status line tells you to raise **Render events** in USB tuning (and **Live keep** on Prophesee), or to raise the DVS threshold or refractory period. USB → **USB tuning…** Help describes FIFO size, buffer count, and the IN statistics.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.4/dropping-events.png" alt="Dropping events overlay beside USB tuning Render events" width="50%" />

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.4/usb-tuning.png" alt="USB tuning window with FIFO, buffers, Render events, and USB IN statistics" width="50%" />

* **Function keys.** Page Up, Page Down, Home, and End show a notice: those keys are not shortcuts. If you meant the arrow keys (contrast and render rate), turn off function-key mode.

<img src="https://raw.githubusercontent.com/SensorsINI/jaer/master/release-notes/3.5.4/function-key-warning.png" alt="Notice that Page Up is not a shortcut and to turn off function-key mode" width="50%" />

* **Remote output.** OpenCV and ROS slice settings take effect on the next frame.
* **Preferences.** The preferences dialog uses a narrower layout.
