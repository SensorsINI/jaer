# NRV DELTA01 camera support in jAER

jAER driver for the [NRV](https://nrv.kr/) **DELTA01** DVS camera (960×720). DELTA01 is NRV’s product name for the current camera module; the event sensor inside is Samsung **S5KRC1S** silicon (that identifier is still used in SDK settings files and jAER class names).

| Package | Role |
|---------|------|
| `nrv.chip` | AEChip, bias UI, user-facing controls |
| `nrv.usb` | LibUsb hardware interface, USB readout, settings parser |

Factory presets for biasing live in [`biasgenSettings/NRV/`](../../biasgenSettings/NRV/).

## Hardware

- **USB:** Cypress `VID 0x04B4`
  - `PID 0x00F0` — FX20 (FX20 I2C transport, 4-byte write payload)
  - `PID 0x00F1` — CX3 (CX3 I2C transport, 1–2 byte payload)
- **Events:** bulk IN endpoint `0x81`
- **I2C:** vendor requests `0xBA` (write) / `0xAB` (read); sensor slave `0x20`

## USB readout

NRV uses **pipelined async bulk transfer** (`USBTransferThread`): default 16 buffers × 128 KiB each. Parsing runs on the transfer callback **outside** the `AEPacketRawPool` lock; only a brief lock is taken to commit parsed events into the jAER packet buffer.

Wire format is **4 bytes per USB word** (port of NRV SDK `PacketParser::S5KRC1SDataProcess`):

- **Normal events** (`P=0`): column-address packets (`header 0x04`) set `posX`; individual events follow in later words.
- **Group events** (`P=1`, bit 7 set): two 8-pixel row groups per packet, bitmask expansion into up to 16 events per group.
- **Timestamp packets** (`P=0`, `pkt[0] & 0x7C == 0x08`): assemble absolute device time from a 22-bit reference millisecond field plus optional 10-bit sub-microsecond updates. Do **not** treat group-2 row-offset-2 packets as timestamps even if they look similar.

jAER address packing: 10-bit X | 10-bit Y << 10 | polarity << 20 (OFF = 1, ON = 0).

### Timestamps

NRV timestamps are **absolute on the device**, unlike DAVIS 15-bit relative timestamps with explicit wrap events in the stream.

| Layer | Representation |
|-------|----------------|
| Device | 22-bit ref ms + 10-bit sub-µs offset (`long` internally) |
| jAER output | `int` µs relative to session origin (`timestampOriginUs`), monotonic |

Notes learned in practice:

- Reference ms wraps about every **70 minutes**; internal math must use `long` (`refMs * 1000` overflows `int` after ~35 minutes).
- jAER relative timestamps wrap at signed 32-bit (~2147 s) unless the user re-zeros with **`0`** (`resetTimestampOrigin()` — software only; no DAVIS-style hardware reset on NRV).
- Timestamp cadence in the USB stream is set by I2C **`TSTAMP_SUB_UNIT_VAL`** (`0x32B1:32B2`, LSB exposed in UI as `0x32B2`) and **`TSTAMP_REF_UNIT_VAL`** (`0x32B3:32B4`). Factory presets scale SUB with nominal output rate (e.g. 100 fps → `0x0B`, 1000 fps → `0x7D`); **lower SUB → more frequent sub-timestamp packets**.

Optional diagnostics: `-Djaer.nrv.trace.timestampOrder=true` logs the first non-monotonic timestamp per USB chunk.

## Biasing and settings files

NRV does **not** use jAER Pot/IPot biasgen XML. Configuration is loaded from **NRV SDK text files**:

```
//@ DVS_VERSION S5KRC1S
//@ Description S5KRC1S_300
20:0166=0F   // slv:addr=value  (I2C slave 0x20, register 0x0166)
wait 10      // delay in ms
```

Files are parsed by `NRVSettingsParser` and applied over I2C when the device opens or when the user loads a file from the Biases frame.

### Preset naming

Settings files use the SDK sensor id **S5KRC1S** in filenames and the `//@ DVS_VERSION` header (not “DELTA01”). Under `biasgenSettings/NRV/` they are named by nominal rate and USB bridge, e.g. `S5KRC1S_1000_CX3.txt` (1000 fps, CX3). The default loaded on first run is `S5KRC1S_300_CX3.txt`.

### Registers exposed in the UI

Beyond the full register table (`NRVControlPanel`), the user panel maps sliders to these I2C registers (slave `0x20`):

| Register | Name | UI control | Effect |
|----------|------|------------|--------|
| `0x0166` | `REG_DIV_BCM_BOT_UNIT_AMP` | Brightness threshold | Lower → more events (temporal contrast) |
| `0x0167` | `REG_DIV_BCM_BOT_UNIT_ON` | ON/OFF balance (ON side) | Tweaked relative to file baseline |
| `0x0168` | `REG_DIV_BCM_BOT_UNIT_nOFF` | ON/OFF balance (OFF side) | Tweaked relative to file baseline |
| `0x32B2` | `TSTAMP_SUB_UNIT_VAL` LSB | Sub-timestamp interval | Lower → denser timestamp packets in USB stream |
| `0x321E` | `DTAG_FRM_MARGIN` LSB | Frame margin | Lower → shorter frame period / finer event timing |

Slider tweaks use `PotTweaker` ratios (up to 8×) around values captured when a settings file is loaded; direct register edits are available in the full table with undo support.

Settings must be **applied to hardware** (automatic on open when a file is loaded) before `NRVConfig.isInitialized()` returns true.

## Preferences

| Node | Contents |
|------|----------|
| `/jaer/chips/NRVS5KRC1S` | Chip + bias UI state, last settings file path |
| `/jaer/hardware/NRV` | AE buffer size |
| `/jaer/hardware` keys `NRV.AEReader.*` | FIFO size, buffer count |

Legacy package paths (`ch/unizh/ini/jaer/chip/nrv`, old hardware node) are migrated automatically; bias XML import rewrites old node names.

## Code entry points

jAER chip class `NRVS5KRC1S` names the sensor silicon; select **DELTA01** / NRV S5KRC1S in the AEChip menu.

- Chip: `nrv.chip.NRVS5KRC1S`
- Settings / bias: `nrv.chip.NRVConfig`
- USB + I2C: `nrv.usb.NRVHardwareInterface`
- Parser: `nrv.usb.S5KRC1SParser`
- Reader: `nrv.usb.NRVAEReader`
