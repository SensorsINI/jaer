# Live USB acquisition benchmarks

Use these flags to capture **before/after** baselines when enabling USB-side
typed `PacketBundle` demux. AEViewer's authoritative route currently applies to
DAVIS FX3, SciDVS, and DVXplorer / Mini / Micro; other interfaces remain
explicit legacy raw compatibility routes even if they expose a typed helper.

## Flags

| System property | Purpose |
|-----------------|--------|
| `-Djaer.live.bench=true` | ViewLoop metrics: FPS, rendered polarity rate, source accepted counts/rate, structured loss, timestamp epochs, legacy raw overruns, loop ms, heap |
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

| Family                    | Pref / property                        | AEViewer route      |
|---------------------------|----------------------------------------|---------------------|
| Davis FX3 (Davis346, SciDVS via same PID) | `hardware/DAViSFX3/usbTypedDemux` | Authoritative typed by default (mono); color RGB stays legacy |
| Davis dual-write APS/IMU AE | `hardware/DAViSFX3/dualWriteApsImuAe` | `false` when demux on |
| DVXplorer / Mini / Micro | `hardware/DVXplorerFX3/usbTypedDemux` | Authoritative typed by default; skips live `AEPacketRaw` |
| NRV | `hardware/NRV/usbTypedDemux` | Legacy raw compatibility |
| Prophesee | `hardware/Prophesee/usbTypedDemux` | Legacy raw compatibility |
| DVS128 libusb FX2 | `hardware/CypressFX2DVS128/usbTypedDemux` | Legacy raw compatibility |

Set the boolean pref to `false` to restore raw + `extractBundle` during validation.

## Route and accounting columns

For normal DAVIS/SciDVS or DVXplorer rendering, filtering, AEDAT-4, and AEDZ
(with no raw sink), `typedDemux=true` means the USB source published a sealed
authoritative bundle with no raw sidecar. The CSV keeps the original columns and
appends:

- `sourceEvents`, `kepsSource`, and `sourceAcceptedCounts`: accepted source
  elements from sealed metadata, grouped by `PacketType`;
- `exactLossEvents` and `unquantifiedLossRecords`: structured loss without
  treating an unavailable count as zero;
- `timestampEpochObservations`, `acquisitionSessionId`, `sequenceId`, and
  `timestampEpochs`: source lifecycle context for the last slice in the window;
- `lossDetail`: packet type and source-provided reason.

The legacy adapter continues to populate `rawEvents`, `kepsRaw`, and `overruns`
from the raw source count and `DroppedDataInfo`. It does not inspect an
`AEPacketRaw` inside the benchmark collector.

AEDAT-2 recording, sequencing, raw unicast/queue output, acquisition-thread
filtering, RGB DAVIS, and unmigrated interfaces select legacy raw acquisition.
If this classification changes while live acquisition is enabled, AEViewer
disables acquisition before Cypress switches mode; typed and raw acquisition
are never run together.

## Baseline checklist

For **Davis346/SciDVS** (DVS-only and DVS+APS) and **DVXplorer / Mini / Micro**:

1. Run ~60 s with demux **off**; note legacy raw rate, overrun count, loop time, and heap from the live-bench log.
2. Enable demux (pref / restart); repeat same scene / illumination.
3. Compare rendered polarity rate with `sourceAcceptedCounts`, structured loss,
   epoch observations, and heap pressure. Davis APS+DVS should show lower heap /
   AE-buffer pressure when APS AE dual-write is off.

For **NRV, Prophesee EVK4, and DVS128**, use the same measurements as legacy
compatibility baselines; they do not demonstrate authoritative routing yet.

Record results next to the CSV path for the PR / release notes.

## USB FIFO / buffer-count reconfiguration (manual)

Live USB readers replace the whole transfer session after a 1 s idle delay
(`UsbAsyncBulkReaderLifecycle.DEFAULT_DEBOUNCE_MS`),
so rapid edits coalesce into one restart. The lifecycle logs
`USB buffer config … active after N ms of no acquisition`; on healthy hardware N
is well under a second. A multi-second N means the device path is blocking (see
the EVK4 note below) and the live view will visibly freeze.

For each of **Prophesee EVK4**, **NRV**, **DAViS FX3**, **DVS128 FX2 libusb**,
and **SiLabs PAER** (plus USBIO DAViS/DVS128 if available):

1. Start live acquisition at default FIFO/buffers.
2. Change USB FIFO several steps quickly; confirm a **single** restart after the
   pause (not one restart per click). Capture should resume without closing the
   viewer.
3. Change USB buffer count ± several steps the same way.
4. Confirm that a failed/hung stop does **not** start a second reader (device
   closes/recovers instead of `LIBUSB_ERROR_IO` from overlapping transfers).

EVK4 specifics: the event endpoint is only drained while the sensor is idle, and
that drain has a hard time budget. Draining a streaming EVK4 blocks for tens of
seconds and stalls 0x81, which then fails every new transfer submit with
`LIBUSB_ERROR_IO`. Such an IO failure aborts the start instead of retrying with a
halved FIFO, so the requested size is not silently degraded.

## USB tuning window (manual)

USB > **USB tuning...** opens a separate top-level window usable with mouse and
arrow keys (no wheel required). Values auto-apply after a short pause.

For each live device above:

1. Open the window during acquisition; confirm **Requested**, **Active session**,
   allocation (`fifo × buffers`), and **Status** match the current setup.
2. Change FIFO with spinner arrows and by typing; confirm Status goes
   `Queued` → `Restarting` → `Active` and Active session updates after the pause.
3. Rapidly change buffers several times; confirm only the final value restarts
   the reader once.
4. Change AE render packet size; confirm it applies without restarting USB.
5. Confirm the live view resumes within about a second of each restart.
6. Optional: force a hung stop path and confirm Status shows `Failed` with
   detail and the device recovers/closes without overlapping transfers.
