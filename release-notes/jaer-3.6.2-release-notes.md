<!--
  Paste-ready for GitHub Releases. Image links use raw.githubusercontent.com so they
  render in the Release body. Relative paths work in the repo, not in a Release description.

  Public 3.6.2 notes after 3.6.1.
  Download table stays. Install steps are only the website guide.
-->

**jAER 3.6.2** follows **[3.6.1](https://github.com/SensorsINI/jaer/releases/tag/3.6.1)**. It adds lossy timestamp compression, markers during recording, and a much faster VCR deck merge, and fixes long-file info, FlyEye USB open, and timeshift recording.

## Download

| You have | CPU | Download |
|---|---|---|
| Windows 10 / 11 | x64 | [jAER_windows-x64_3_6_2.exe](https://github.com/SensorsINI/jaer/releases/download/3.6.2/jAER_windows-x64_3_6_2.exe) |
| macOS | Apple Silicon (M1–M4) | [jAER_macos_aarch64_3_6_2.dmg](https://github.com/SensorsINI/jaer/releases/download/3.6.2/jAER_macos_aarch64_3_6_2.dmg) |
| macOS | Intel | [jAER_macos_3_6_2.dmg](https://github.com/SensorsINI/jaer/releases/download/3.6.2/jAER_macos_3_6_2.dmg) |
| Linux | x64 | [jAER_unix_3_6_2.sh](https://github.com/SensorsINI/jaer/releases/download/3.6.2/jAER_unix_3_6_2.sh) |
| Linux | x64 `.deb` | [jAER_linux-amd64_3_6_2.deb](https://github.com/SensorsINI/jaer/releases/download/3.6.2/jAER_linux-amd64_3_6_2.deb) |
| Any OS | Sample data (~995 MB) | [jaer-sample-data.zip](https://github.com/SensorsINI/jaer/releases/download/3.6.2/jaer-sample-data.zip) ([README](https://github.com/SensorsINI/jaer/blob/master/jaerSampleData/README.md)) |

Each installer includes a bundled [Eclipse Temurin](https://adoptium.net/) JDK 25. You do not install Java yourself. GitHub lists the same files again under **Assets**.

Install, update, and uninstall steps are in the **[Install Guide](https://jaerproject.org/install/)**.

## New features

1. [Lossy timestamp resolution](#lossy-timestamp-resolution)
2. [Markers while recording](#markers-while-recording)
3. [Faster VCR deck merge](#vcr-deck-merge)
4. [Recording overlay](#recording-overlay)
5. [Latency bench on every OS](#latency-bench)
6. [FlyMotion arrow scale](#flymotion)

<h3 id="lossy-timestamp-resolution">Lossy timestamp resolution</h3>

File → Save As and live recording can coarsen event timestamps. **AEDAT-4** lossy time bins collapse events that share a quantized time, pixel, and polarity into a count (optional On+Off collapse). **AEDAT-Z** keeps every event and right-shifts the timestamp. Off by default. A shift of 10 is about 1 ms. Details: [Lossy timestamp resolution](https://github.com/SensorsINI/jaer/blob/master/docs/README-file-formats.md#lossy-timestamp-resolution).

<h3 id="markers-while-recording">Markers while recording</h3>

**i**, **o**, **m**, and Clear work while an AEDAT-4 recording is open. On the live view they land at the current end of the file (**m** is earlier by the reaction-time preference). During timeshift they land at the playhead. Marks stay on the scrubber as the file grows and are saved in the playback-mark sidecar, including after a rename.

<h3 id="vcr-deck-merge">Faster VCR deck merge</h3>

Merging a multi-cassette ZSTD deck rewrites later cassettes on several cores instead of decompressing, shifting, and recompressing each packet on one thread. A 25-cassette deck that took about an hour finished in about a minute.

<h3 id="recording-overlay">Recording overlay</h3>

The recording overlay shows the file size next to the elapsed time. The auto-stop estimate scales from hours through days, weeks, and months.

<h3 id="latency-bench">Latency bench on every OS</h3>

DVSLatencyMeasurement can pick a USB serial port on macOS, Linux, and Windows. Setup remains in the [Low-Latency section](https://docs.google.com/document/d/1fb7VA8tdoxuYqZfrPfT46_wiT1isQZwTHgX8O22dJ0Q/edit?tab=t.0#bookmark=id.og4z2km4xd8z) of the jAER User Guide.

<h3 id="flymotion">FlyMotion arrow scale</h3>

FlyMotion global-flow arrows scale separately from the optical-flow scale bar.

## Bug fixes

1. File info for a long AEDAT-4 file no longer saturates duration near 35.8 minutes, and the packet-index scan runs off the UI thread. ([980537128](https://github.com/SensorsINI/jaer/commit/980537128))
2. Panning works with a one-finger touchpad slide or a left-button drag. Filters that already use left-drag keep that gesture. ([7f7beabc3](https://github.com/SensorsINI/jaer/commit/7f7beabc3))
3. **Back to live** no longer closes the AEDAT-4 writer and stops the recording. ([cea6f8455](https://github.com/SensorsINI/jaer/commit/cea6f8455))
4. USB acquisition pauses while a recording-stop dialog is open, including Prophesee. ([4e8880354](https://github.com/SensorsINI/jaer/commit/4e8880354))
5. Opening a second DVS128 for FlyEye no longer aborts the USB open. ([62b8f5050](https://github.com/SensorsINI/jaer/commit/62b8f5050))
6. The recording folder chooser shows the folder list on macOS. ([19e788378](https://github.com/SensorsINI/jaer/commit/19e788378))
7. Opening a bogus CSV no longer floods the log with exceptions. ([a89115ccf](https://github.com/SensorsINI/jaer/commit/a89115ccf))
