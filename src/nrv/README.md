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
- jAER relative timestamps are signed 32-bit µs. After ~2147 s of session span they **big-wrap** through about −2147 s and continue (same convention as DAVIS/DVX). Press **`0`** to re-zero at the current device time (`resetTimestampOrigin()` — software only; no DAVIS-style hardware reset on NRV).
- Timestamp cadence in the USB stream is set by I2C **`TSTAMP_SUB_UNIT_VAL`** (`0x32B1:32B2`, LSB exposed in UI as `0x32B2`) and **`TSTAMP_REF_UNIT_VAL`** (`0x32B3:32B4`). Factory presets scale SUB with nominal output rate (e.g. 100 fps → `0x0B`, 1000 fps → `0x7D`); **lower SUB → more frequent sub-timestamp packets**.

Optional diagnostics: `-Djaer.nrv.trace.timestampOrder=true` logs the first non-monotonic timestamp per USB chunk. For timing-register experiments use `-Djaer.nrv.trace.timing=true` (throttled summary every 2 s by default; `-Djaer.nrv.trace.timing.intervalMs=1000` to change). With timing trace, each USB chunk also logs `NRV chunk ts span: … spanUs=…`. Live timing I2C writes trigger parser ref/full resync (column position and jAER time origin preserved).

**Parser bucket / event-rate trace (live USB):** `-Djaer.nrv.trace.parser=true` logs per–output-timestamp ms buckets and per–frame-end event counts (throttled INFO every 2 s). Use `scripts/run-jaer-nrv-parser-trace.bat` or add `-Djaer.nrv.trace.parser.file=C:/temp/jaer-nrv-parser.csv`. **Do not** enable per-bucket INFO (`tinyLog`/`burstLog` at INFO stalls USB — they log at FINE only). Optional `-Djaer.nrv.trace.parser.sampleLog=true` prints a few tiny/burst examples per summary interval. Post-run: `python scripts/analyze-nrv-parser-csv.py`. Compare with `scripts/analyze-nrv-recording-events.py` on text exports: if **live** ms buckets are steady but **recording** shows tiny 3 ms slices → timestamp quantization in the file; if **live** ms buckets are also tiny/bursty → sensor/USB stream.

**Pipeline microbenchmarks** (compare NRV vs EVK4 under load):

```text
-Djaer.usb.trace.pipeline=true
-Djaer.usb.trace.file=C:/temp/jaer-usb-pipeline-nrv.csv
-Djaer.usb.trace.intervalMs=2000
```

**Launch with trace (Windows):** use `scripts/run-jaer-usb-trace.bat nrv` (NRV) or `scripts/run-jaer-usb-trace.bat evk4` (Prophesee EVK4). Each camera writes a separate CSV under `C:/temp/` for later comparison. Or use `scripts/run-jaer-fast.bat` and append `-Djaer.usb.trace.pipeline=true` etc. **Do not** pass only trace flags via `ant -Drun.jvmargs=...` — that **replaces** the default `run.jvmargs` from `nbproject/project.properties` and drops required flags such as `-Djogl.disable.openglcore`, `-Djava.library.path=jars`, and `-Xmx10g`. Missing JOGL flags often shows `GLProfile[GL4bc]` and `makeCanvas` / `Unable to determine GraphicsConfiguration` on Windows.

If using `ant run`, include the full JVM argument set from `project.properties` **plus** trace flags in one `-Drun.jvmargs="..."` string.

Logs every 2 s (INFO): chunks/s, MB/s, keps, and average µs for `parse`, `commitLock`, `limitLock`, `byteCopy`, `arrayCopy`. CSV rows are per USB chunk with thread name. NRV and EVK4 both use async `USBTransferThread` (threads `NRVAEReaderThread` / `PropheseeAEReader`); `usbReadNs` is only non-zero on legacy sync paths.

### Frame / playback gap debugging

When playback at ~1 ms slice interval shows **2–3 stale/blank display frames** between updates at 1 kHz scan rate, the gap can originate at several layers. Use CSV trace to localize it.

**Launch (live recording + playback in one session):**

```text
scripts/run-jaer-nrv-frames-trace.bat
```

Or append to `run-jaer-fast.bat`:

```text
-Djaer.nrv.trace.frames=true
-Djaer.nrv.trace.playback=true
-Djaer.nrv.trace.frames.file=C:/temp/jaer-nrv-frames.csv
```

**CSV `kind` rows:**

| kind | Meaning |
|------|---------|
| `usb_frame_end` | Sensor frame-end packet (`0x0C`): `deltaRefMs`, events since last frame-end |
| `usb_commit` | Events committed from one USB parse chunk to the AE buffer |
| `viewer_packet` | Packet delivered to the ViewLoop after buffer swap |
| `playback_slice` | One `readPacketByTime(sliceUs)` during file playback |
| `playback_skip_render` | ViewLoop skipped render because slice had 0 events and “Render blank frames” is off |

INFO summaries every 2 s report USB frame-end Hz, avg Δref ms, playback empty-slice %, max consecutive empty slices, and **eventsPerSlice** (avg/min/max plus sparse &lt;100 and tiny &lt;10 counts).

**Suggested debug sequence (tomorrow):**

1. **Live @ 1000 Hz scan, scene with steady activity** — confirm `usb_frame_end` `deltaRefMs` ≈ 1.0 and `avgEventsPerFrame` > 0. If Δref ms is ~2–3, the sensor/host is not delivering 1 kHz frames (scan-rate block or USB backlog).
2. **Same session, log a .aedat** — compare `viewer_packet` event counts and timestamp span vs frame-end rate. Large viewer packets spanning many ms suggest host batching/coalescing before the logger sees events.
3. **Playback with slice = 1001 µs** — inspect `playback_slice`: consecutive rows with `field10` (numEvents)=0 explain blank display (View > Render blank frames off keeps the previous image). Check whether empty slices align with timestamp gaps in the file (`maxTs` of slice N vs `minTs` of slice N+1).
4. **Cross-check** — enable View > Render blank frames: if “gaps” become explicit black frames, the file/slicer is producing empty time windows; if gaps persist as frozen old frames, look at ARS or render skip (playback disables ARS by default).
5. **Optional** — combine with `-Djaer.usb.trace.pipeline=true` to see if USB overload correlates with missing frame-ends or post-overrun skip windows (`skipWindowActive=1` on `usb_frame_end`).

**Hypotheses to test:**

| Observation in CSV | Likely cause |
|--------------------|--------------|
| `deltaRefMs` often 2–3 at 1 kHz setting | Scan-rate registers or true sensor rate below UI nominal |
| Frame-ends OK but `playback_slice` empty | Timestamps in file do not cover every 1 ms window (logging batching or identical ts per frame) |
| `playback_skip_render` matches visible gaps | Expected: fix slice alignment or enable blank-frame render for diagnosis |
| `usb_frame_end` events=0 with skipWindowActive=1 | Post-overrun parser skip dropping group events |
| `viewer_packet` spanUs >> sliceUs | ViewLoop/logging sees multi-frame batches |

**Sub-timestamp decode (matches NRV SDK `PacketParser.cpp`):** dedicated ref/sub packets (`header==0x08`, `P=0`) update `fullTimeStampUs` using the 10-bit sub field as **microseconds within the ref ms** (`refMs×1000 + sub`). Column address packets (`0x04`) set `posX` only — the embedded 10-bit sub field is **not** applied (SDK behaviour). Many events in one column share the same output timestamp until the next ref/sub packet. Timing trace reports `maxChunkSpanUs` per interval.

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

Frame timing:

```
# FRM_MARGIN padding only (SDK note) — NOT the full sensor frame period:
frm_margin_padding_us = DTAG_FRM_MARGIN(0x321D:321E) × 2^12 × event_clock_period_us

# Full scan / frame-end rate is set by the whole Scan Rate Setting block
# (0x320C, 0x3210–0x321E). jAER’s scan-rate slider (100–2000 Hz) interpolates
# that block between factory anchors from S5KRC1S_100 / _1000 / _2000 presets.

sub_interval_us = (TSTAMP_REF_UNIT + 1) / TSTAMP_SUB_UNIT
```

`event_clock_period_us` is derived from OUTIF `0x3911` (factory `0x7C` → 1 µs).

**Why “1000 fps” ≠ 244 Hz:** Interpreting `DTAG_FRM_MARGIN × 4096 × 1 µs` as the *full* frame period gives ~244 Hz at margin=`0x0001` and ~122 Hz at the 1000-preset margin=`0x0002`. That cannot be the vendor’s 1000/2000 fps claim (NRV marketing: up to 2 kHz imaging). Those presets change **SELX / SENSE / AY / APS_RST / MODE / FRM_MARGIN together**. CX3 files named 100/300/600 share the *same* scan-rate registers and differ mainly in `TSTAMP_SUB`. Measure true rate from USB frame-end packets (`header==0x0C`).

**Scan rate vs SDK “frame period”:** In NRV’s `DVSDevice.cpp`, `PARA_TYPE_FRAME_PERIOD` is a *software* packaging parameter (e.g. “33 → collect 33 ms of events into one viewer frame”). It does **not** program the sensor.

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
| `0x320C`, `0x3210`–`0x321E` | Scan Rate Setting block | **Scan rate (100–2000 Hz)** | Interpolated from factory 100/1000/2000 anchors |
| `0x321D:321E` | `DTAG_FRM_MARGIN_r` MSB:LSB | *(part of scan-rate block)* | Padding term (×2^12×clk); not full period alone |
| `0x32B1:32B2` | `TSTAMP_SUB_UNIT_VAL` MSB:LSB | Sub-timestamp + auto with scan rate | Lower LSB → denser sub-timestamp packets |
| `0x32B3:32B4` | `TSTAMP_REF_UNIT_VAL` | *(from file)* | Sub-µs field span within each ref ms |

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
