# Live USB acquisition benchmarks

Use these flags to capture **before/after** baselines when enabling USB-side
typed `PacketBundle` demux (retire live `AEPacketRaw` → `extractBundle`).

## Flags

| System property | Purpose |
|-----------------|--------|
| `-Djaer.live.bench=true` | ViewLoop metrics: FPS, polarity keps, overruns, loop ms, heap |
| `-Djaer.live.bench.file=logs/live-bench.csv` | Append CSV summary rows |
| `-Djaer.live.bench.intervalMs=2000` | Summary interval |
| `-Djaer.usb.trace.pipeline=true` | USB-thread chunk timings (`UsbPipelineBench`) |
| `-Djaer.usb.trace.file=logs/usb-pipeline.csv` | Per-chunk USB CSV |

Example (Windows / PowerShell — quote `-D` or use `--%`, else PowerShell mangles them):

```powershell
.\scripts\run-jaer-fast.bat --% -Djaer.live.bench=true -Djaer.live.bench.file=logs/davis346-baseline.csv
# or:
.\scripts\run-jaer-fast.bat "-Djaer.live.bench=true" "-Djaer.live.bench.file=logs/davis346-baseline.csv"
# or:
$env:JAER_JVM_ARGS='-Djaer.live.bench=true -Djaer.live.bench.file=logs/davis346-baseline.csv'
.\scripts\run-jaer-fast.bat
```

## Sensor-specific configuration: preferences / kill-switches

The following table lists preference keys ("prefs") and system properties you can set to enable or disable typed demux for each sensor family. 

- **How to use:**  
  - **From the GUI:** Set the preference key (e.g. `hardware/DAViSFX3/usbTypedDemux`) in the Preferences panel.
  - **From command line:**  
    Add `-D<key>=<value>` arguments, for example:  
    ```powershell
    .\scripts\run-jaer-fast.bat --% -Dhardware/DAViSFX3/usbTypedDemux=false
    ```
    (Replace `<key>` and `<value>` as needed. On Linux/macOS, use `./scripts/run-jaer-fast.sh -D...`.)

| Family                    | Pref / property                        | Default demux      |
|---------------------------|----------------------------------------|--------------------|
| Davis FX3 (Davis346, SciDVS via same PID) | `hardware/DAViSFX3/usbTypedDemux` | `true` (mono); color RGB stays off |
| Davis dual-write APS/IMU AE | `hardware/DAViSFX3/dualWriteApsImuAe` | `false` when demux on |
| NRV | `hardware/NRV/usbTypedDemux` | `true` (skips live `AEPacketRaw` dual-write) |
| Prophesee | `hardware/Prophesee/usbTypedDemux` | `true` |
| DVS128 libusb FX2 | `hardware/CypressFX2DVS128/usbTypedDemux` | `true` |

Set the boolean pref to `false` to restore raw + `extractBundle` during validation.

## Baseline checklist

For each of **Davis346** (DVS-only and DVS+APS), **NRV**, **Prophesee EVK4**, **DVS128**:

1. Run ~60 s with demux **off**; note keps, overrun count, heapMB from live-bench log.
2. Enable demux (pref / restart); repeat same scene / illumination.
3. Success: polarity rate within ~10% of baseline, no overrun regression; Davis APS+DVS should show lower heap / AE-buffer pressure when APS AE dual-write is off.

Record results next to the CSV path for the PR / release notes.
