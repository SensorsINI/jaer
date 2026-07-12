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

### Event threshold registers (EVTH)

ON/OFF event sensitivity is set by three threshold paths — reference, ON, and OFF — each with an MSB range bit in `0x0157` and a 6-bit LSB in its own register. NRV SDK `.txt` files often label these with legacy names (`REG_DIV_BCM_BOT_UNIT_*`, `CRGS Setting`); the silicon register names below are authoritative.

| Name | Address | Bit(s) | Reset | Description |
|------|---------|--------|-------|-------------|
| `EVTH_REF_MSB_r` | `0x0157` | [4] | `0x1` | Reference level — MSB (coarse range) |
| `EVTH_ON_MSB_r` | `0x0157` | [3] | `0x1` | ON event threshold — MSB |
| `EVTH_OFF_MSB_r` | `0x0157` | [2] | `0x1` | OFF event threshold — MSB |
| `EVTH_REF_LSB_r` | `0x0166` | [5:0] | `0x1F` | Reference level — lower 6 bits |
| `EVTH_ON_LSB_r` | `0x0167` | [5:0] | `0x3F` | ON event threshold — lower 6 bits |
| `EVTH_OFF_LSB_r` | `0x0168` | [5:0] | `0x0F` | OFF event threshold — lower 6 bits |

Factory presets set `0x0157=0x1F` (all three MSBs high) and typical LSBs `0x0166=0x0F`, `0x0167=0x07`, `0x0168=0x1F`.

Intermediate constants (MSB is 0 or 1):

```
K_REF = (EVTH_REF_MSB_r × 10 + (1 − EVTH_REF_MSB_r) × 2.5) / ((1 + EVTH_REF_LSB_r) × 176)
K_ON  = (EVTH_ON_MSB_r  × 50 + (1 − EVTH_ON_MSB_r)  × 12.5) / ((1 + EVTH_ON_LSB_r)  × 88)
K_OFF = (EVTH_OFF_MSB_r × 5  + (1 − EVTH_OFF_MSB_r) × 1.25) / ((1 + EVTH_OFF_LSB_r) × 880)
```

Intermediate **K** values are proportional **relative bias currents** from the on-chip bias generator (Id, Ion, Ioff); absolute Amps are not specified — only ratios matter for thresholds:

```
K_REF ∝ I_d     (reference / diff current)
K_ON  ∝ I_ON
K_OFF ∝ I_OFF
```

Register formulas map EVTH MSB/LSB bits to these relative K values (see table above).

Event thresholds follow the DVS change-amplifier model ([Nozaki & Delbruck, IEEE TED 2018](https://ieeexplore.ieee.org/document/7962235); see also [authors’ reply](https://doi.org/10.1109/TED.2018.2841205) on corrected formulas):

```
Θ_ON  = (κ_n C_2 / (κ_p² C_1)) × ln(I_ON / I_d)
Θ_OFF = (κ_n C_2 / (κ_p² C_1)) × ln(I_OFF / I_d)     (negative when I_OFF < I_d)
```

Assuming NRV **K** values are proportional to bias currents (`K_REF ∝ I_d`, `K_ON ∝ I_ON`, `K_OFF ∝ I_OFF`), jAER uses **gain ≈ 0.07** (same order as `κ_n C_2 / (κ_p² C_1)` in DAVIS) and **offset = 0**:

```
Θ_ON  = gain × ln(K_ON / K_REF)
Θ_OFF = gain × ln(K_OFF / K_REF)
```

NRV silicon documentation sometimes writes OFF as `gain × ln(K_REF/K_OFF)`; that equals **−Θ_OFF** (unsigned magnitude). The bias panel shows signed Θ like DAVIS.

UI shows e-folds and `100 × (e^Θ − 1)` percent intensity change. Sliders update live register K values.

Frame timing (from loaded registers):

```
frame_period_us = DTAG_FRM_MARGIN × 2^12 × event_clock_period_us
readout_Hz      = 1e6 / frame_period_us
sub_interval_us = (TSTAMP_REF_UNIT + 1) / TSTAMP_SUB_UNIT
```

`event_clock_period_us` is derived from OUTIF `0x3911` (factory `0x7C` → 1 µs).

Qualitative behavior (with MSBs fixed as in presets):

- **`EVTH_REF_LSB_r` (`0x0166`)** — from settings file / register table only; sets `K_REF` (not on sliders).
- **Event threshold slider** — from file baselines: lower `0x0167`, higher `0x0168` when sliding right (like DVS `diffOn↑` / `diffOff↓`) → both |Θ| up.
- **ON/OFF balance slider** — from file baselines: raise both LSBs when sliding right (like raising DVS `diff`/Id) → Θ_ON down, |Θ_OFF| up (more ON / fewer OFF).
- **Higher ON/OFF LSB** → smaller K → lower |threshold| for that polarity → more events of that polarity.

### Preset naming

Settings files use the SDK sensor id **S5KRC1S** in filenames and the `//@ DVS_VERSION` header (not “DELTA01”). Under `biasgenSettings/NRV/` they are named by nominal rate and USB bridge, e.g. `S5KRC1S_1000_CX3.txt` (1000 fps, CX3). The default loaded on first run is `S5KRC1S_300_CX3.txt`.

### Registers exposed in the UI

Beyond the full register table (`NRVControlPanel`), the user panel maps sliders to LSB registers. MSB bits in `0x0157` come from the loaded settings file only (editable in the full table, not on sliders).

| Register | EVTH / SDK name | UI control | Effect |
|----------|-----------------|------------|--------|
| `0x0157` [4:2] | `EVTH_*_MSB_r` / `CRGS Setting` | *(from file)* | Coarse ON/OFF/REF range select |
| `0x0166` | `EVTH_REF_LSB_r` / `REG_DIV_BCM_BOT_UNIT_AMP` | *(from file / table)* | `K_REF` — not on sliders |
| `0x0167` | `EVTH_ON_LSB_r` / `REG_DIV_BCM_BOT_UNIT_ON` | Event threshold + balance | `K_ON` |
| `0x0168` | `EVTH_OFF_LSB_r` / `REG_DIV_BCM_BOT_UNIT_nOFF` | Event threshold + balance | `K_OFF` |
| `0x32B2` | `TSTAMP_SUB_UNIT_VAL` LSB | Sub-timestamp interval | Lower → denser timestamp packets in USB stream |
| `0x321E` | `DTAG_FRM_MARGIN` LSB | Frame margin | Lower → shorter frame period / finer event timing |

Slider tweaks use `PotTweaker` ratios (up to 8×) around LSB values captured when a settings file is loaded; LSB writes are clamped to `0x01`–`0x3F`. Direct register edits are available in the full table with undo support.

**Implementation note:** `NRVConfig` computes K from current registers and displays Θ_ON/Θ_OFF. Sliders write only `0x0167`/`0x0168`; `K_REF` (`0x0166`) comes from the loaded settings file or full register table.

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
