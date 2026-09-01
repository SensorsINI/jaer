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

Prophesee biases are **8-bit idac_ctl values** written to sensor registers through the EVK4 board (`Imx636Init.applyBiases`). Defaults match neuromorphic-drivers `prophesee_evk4`. Metavision Studio exposes the same registers as **offsets from factory trim**; jAER stores absolute bytes and centers the user-friendly sliders on the **last saved/loaded** snapshot (like DVS/DAVIS/NRV).

Physical mapping from digital value to contrast threshold % or filter Hz is **not** claimed. Friendly sliders are abstract −1…1 additive offsets using [Metavision IMX636 ranges](https://docs.prophesee.ai/stable/hw/manuals/biases.html).

### User-friendly controls

| Slider (center = saved prefs) | Maps to | IMX636 polarity |
|-------------------------------|---------|-----------------|
| Brightness change threshold | `diffOn` and `diffOff` together | Both **increase** to raise threshold (unlike DAVIS analog `diffOn`↑/`diffOff`↓) |
| ON/OFF balance (right = more ON) | same | Decrease `diffOn`, increase `diffOff` |
| Pixel low-pass (faster = shorter τ_LP) | `fo` (`bias_fo`) | Increase `fo` to widen bandwidth (−35…+55 from factory) |
| Pixel high-pass (right = reject slow/DC) | `hpf` (`bias_hpf`) | Increase `hpf` to raise the high-pass cutoff (0…+120; factory is typically 0, so left of center is a no-op until a positive `hpf` is saved) |

`bias_pr` and `bias_diff` are **not** on the friendly tab (Prophesee: leave at default). Refractory (`bias_refr`) remains on the raw tab only.

Applied ON/OFF from both tweaks (then clamped to `[0,255]` and factory±range, expanded so slider 0 is identity):

```
diffOn  = saved.diffOn  + offset(threshold, 140, 85) - offset(balance, 140, 85)
diffOff = saved.diffOff + offset(threshold, 190, 35) + offset(balance, 190, 35)
```

### Raw idac sliders

| Field | Register | UI slider | Typical role |
|-------|----------|-----------|--------------|
| `diff` | `0x1014` | Diff | Global contrast (leave default on IMX636) |
| `diffOn` | `0x1010` | Diff ON | ON-event threshold |
| `diffOff` | `0x1018` | Diff OFF | OFF-event threshold |
| `pr` | `0x1000` | PR | Photoreceptor bias (leave default) |
| `fo` | `0x1004` | FO | Low-pass / follower |
| `refr` | `0x1020` | Refr | Refractory period |
| `hpf` | `0x100C` | HPF | High-pass |

Additional idac bytes (`inv`, `reqpuy`, `reqpux`, `sendreqpdy`, …) are read/written but not all have UI sliders yet.

Bias workflow:

1. On open, `Imx636Init` runs the ISSD bring-up sequence and reads chip defaults into `PropheseeBiases` (used as factory for range clamping).
2. `PropheseeConfig` loads saved values from the chip Preferences node (`PropheseeConfig.bias.*`); those become the friendly-slider center.
3. Friendly or raw slider changes apply immediately over USB; **Revert** restores the last saved snapshot and re-centers tweaks.
4. Export/import bias XML via the Biases frame (same mechanism as DVS128). XML with legacy package paths is rewritten on import.

Default preferences file (when present): `deviceSettings/PropheseeIMX636HD/PropheseeIMX636HD.xml`.

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

## File playback (Metavision RAW)

Open Prophesee / Metavision native **`.raw` EVT3** recordings (File → Open, or drag-drop).
jAER parses the ASCII `%` header, indexes CD events, and decodes with the same `Evt3Parser` as live USB.
A sparse seek index is cached under `${java.io.tmpdir}/jaer/aeidx/` (`*.metavisionrawidx`, keyed by name/size/mtime) so reopen is fast.

- Supported: RAW EVT3 (EVK4 IMX636 / Gen4.1 HD samples such as `laser.raw`)
- Not yet: RAW EVT2, HDF5, DAT

## Code entry points

- Chip: `prophesee.chip.PropheseeIMX636HD`
- Bias UI: `prophesee.chip.PropheseeConfig`
- USB: `prophesee.usb.PropheseeHardwareInterface`
- Parser: `prophesee.usb.evt3.Evt3Parser`
- Reader: `prophesee.usb.PropheseeAEReader`
- Init: `prophesee.usb.evk4.Imx636Init`
- RAW file: `prophesee.eventio.MetavisionRawFileInputStream`
