# Prophesee camera support in jAER

jAER driver for [Prophesee](https://www.prophesee.ai/) EVK4 HD (Sony IMX636, 1280×720), EVT3 event format.

| Package | Role |
|---------|------|
| `prophesee.chip` | AEChip, bias UI |
| `prophesee.usb` | LibUsb hardware interface, USB readout |
| `prophesee.usb.evt3` | EVT3 decoder |
| `prophesee.usb.evk4` | Board commands, IMX636 init/start/stop |

## Hardware

- **USB:** Cypress `VID 0x04B4`, `PID 0x00F5` (EVK4 HD)
- **Events:** bulk IN endpoint `0x81`, **EVT3** (2 bytes per token)
- **Control:** Treuzell bulk protocol via `Evk4BoardCommand` (register read/write on the EVK4 board)

## USB readout

Prophesee uses **pipelined async bulk transfer** (`USBTransferThread`) on endpoint `0x81`, same approach as NRV. Parsing runs on the transfer callback outside the `AEPacketRawPool` lock; only a brief lock is taken to commit parsed events. Sync `LibUsb.bulkTransfer()` remains in `Evk4BoardCommand` for flush/poll and control traffic.

`Evt3Parser` decodes the EVT3 stream (port of Metavision / openeb `evt3_decoder.h`):

- **CD events** (types `0b0010`, `0b0011`, Vect12/Vect8 bursts): X/Y/polarity with 11-bit coordinates; Y-flip applied in the chip extractor (`flipy=true`).
- **Time high / time low** tokens: 11-bit timestamp fields assembled into microseconds; MSB wrap tracked in software.
- **Other** tokens: skipped (contribution counters available when trace flags are on).

jAER address packing: 11-bit X | 11-bit Y << 11 | polarity << 22.

### Timestamps

Like NRV, timestamps are assembled in software and emitted as **monotonic `int` µs relative to a session origin** (`resetTimestampOrigin()` on **`0`**). EVT3 uses separate TIME_HIGH / TIME_LOW updates rather than NRV-style ref/sub timestamp packets.

Packet-level timestamp statistics for live debugging appear in the **Info** filter overlay (`showPacketTimestampStats`), not in the USB driver.

Optional diagnostics:

- `-Djaer.prophesee.trace=true` — USB transfer FINER logs
- `-Djaer.prophesee.trace.timestamps=true` — EVT3 timestamp FINE logs (2 s throttle)

**Pipeline microbenchmarks** (compare EVK4 vs NRV under load): same flags as NRV (`-Djaer.usb.trace.pipeline=true`, `-Djaer.usb.trace.file=...`, `-Djaer.usb.trace.intervalMs=2000`). CSV rows use `driver=EVK4`; `usbReadNs` is ~0 on the async path (USB overlap is hidden behind parse). **Launch (Windows):** `scripts/run-jaer-usb-trace.bat evk4` writes `C:/temp/jaer-usb-pipeline-evk4.csv`; run `scripts/run-jaer-usb-trace.bat nrv` separately for the NRV file, then compare.

## Biasing (IMX636)

Prophesee biases are **8-bit idac_ctl values** written to sensor registers through the EVK4 board (`Imx636Init.applyBiases`). Defaults match neuromorphic-drivers `prophesee_evk4`.

| Field | Register | UI slider | Typical role |
|-------|----------|-----------|--------------|
| `diff` | `0x1014` | Diff | Global contrast / threshold |
| `diffOn` | `0x1010` | Diff ON | ON-event threshold |
| `diffOff` | `0x1018` | Diff OFF | OFF-event threshold |
| `pr` | `0x1000` | PR | Photoreceptor bias |
| `fo` | `0x1004` | FO | Follower / front-end |
| `refr` | `0x1020` | Refr | Refractory period |
| `hpf` | `0x100C` | HPF | High-pass / bandwidth |

Additional idac bytes (`inv`, `reqpuy`, `reqpux`, `sendreqpdy`, …) are read/written but not all have UI sliders yet.

Bias workflow:

1. On open, `Imx636Init` runs the ISSD bring-up sequence and reads chip defaults into `PropheseeBiases`.
2. `PropheseeConfig` loads saved values from the chip Preferences node (`PropheseeConfig.bias.*`).
3. Slider changes apply immediately over USB; **Revert** restores the last saved snapshot.
4. Export/import bias XML via the Biases frame (same mechanism as DVS128). XML with legacy package paths is rewritten on import.

Default preferences file (when present): `biasgenSettings/PropheseeIMX636HD/PropheseeIMX636HD_default.xml`.

## Device init

`Imx636Init` (port of `prophesee_evk4.rs`) handles:

- Serial number read
- Default bias readback
- ISSD configuration upload
- Start/stop streaming
- ROI / event rate configuration via board registers

Init uses `EdfReserved7004 = 0x0000C5FF` (external trigger enabled in default configuration).

## Preferences

| Node | Contents |
|------|----------|
| `/jaer/chips/PropheseeIMX636HD` | Bias values, display prefs |
| `/jaer/hardware/Prophesee` | AE buffer size |
| `/jaer/hardware` keys `Prophesee.AEReader.*` | FIFO size, buffer count |

Default USB reader tuning (Control menu): **128 KiB FIFO** (`131072` bytes), **16 buffers**, async bulk on `0x81`. Stored prefs migrate once to these values when `Prophesee.AEReader.prefsVersion` is bumped.

Legacy paths under `ch/unizh/ini/jaer/chip/prophesee` and the old hardware package node are migrated automatically.

## Comparison with NRV (same jAER tree)

| | NRV S5KRC1S | Prophesee EVK4 HD |
|---|-------------|-------------------|
| Resolution | 960×720 | 1280×720 |
| Wire format | 4-byte S5KRC1S packets | 2-byte EVT3 |
| USB read | Async multi-buffer | Async multi-buffer |
| Biasing | SDK `.txt` register scripts | idac_ctl bytes over EVK4 |
| Timestamp wire | Ref ms + sub-µs packets | EVT3 TIME_HIGH/LOW tokens |

## Code entry points

- Chip: `prophesee.chip.PropheseeIMX636HD`
- Bias UI: `prophesee.chip.PropheseeConfig`
- USB: `prophesee.usb.PropheseeHardwareInterface`
- Parser: `prophesee.usb.evt3.Evt3Parser`
- Reader: `prophesee.usb.PropheseeAEReader`
- Init: `prophesee.usb.evk4.Imx636Init`
