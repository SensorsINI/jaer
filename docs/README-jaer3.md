# jAER 3 — Event processing pipeline

This document summarizes how live sensor data and recorded files move through
**jAER 3** (`jaer3` / `master` after the PacketBundle refactor): from USB
capture, through typed packets and `EventFilter`s, to OpenGL display and optional
AEDAT-4 or AEDZ recording.

The central idea in jAER 3 is that one **timeslice** is a
[`PacketBundle`](../src/net/sf/jaer/event/PacketBundle.java): an ordered list of
homogeneous typed packets (polarity, APS frames, IMU, …) rather than one mixed
`ApsDvsEventPacket`.

---

## High-level data flow

```mermaid
flowchart LR
  subgraph USB["USB device (FX3 / NRV / …)"]
    EP[Bulk IN endpoint]
  end

  subgraph Capture["AEReader / USBTransferThread"]
    XFER[Overlapped USB transfers]
    PARSE[Translate / demux to PacketBundle]
    WBUF[PacketBundlePool write buffer]
  end

  subgraph View["AEViewer.ViewLoop"]
    ACQ[acquireAvailablePacketBundle]
    EXT[extractBundle legacy fallback]
    FILT[FilterChain.filterBundle]
    REC[recordPacket to AEDAT-4 / typed AEDZ / AEDAT-2]
    REN[Renderer + ChipCanvas OpenGL]
  end

  EP --> XFER --> PARSE --> WBUF
  WBUF -.swap.-> ACQ --> FILT
  ACQ -.-> EXT -.-> FILT
  FILT --> REC
  FILT --> REN
```

**Primary threads**

| Thread | Role |
|--------|------|
| `USBTransferThread` / `AEReader` | Submits multiple bulk IN transfers; callback parses bytes into the **write** side of a double buffer |
| `AEViewer.ViewLoop` | Game loop: acquire → extract/filter → record → render → pace FPS |
| AWT Event Dispatch (EDT) | UI, menus, bias controls (must not block on USB open/close) |
| JOGL / display | ChipCanvas paints; often driven by `paintFrame` / `repaint` from ViewLoop |

---

## Overlapped USB I/O and double buffering

**Authoritative live path** (DAVIS FX3, including SciDVS on the shared DAVIS
interface, and DVXplorer / Mini / Micro): USB decode fills a typed
[`PacketBundle`](../src/net/sf/jaer/event/PacketBundle.java) via
[`PacketBundlePool`](../src/net/sf/jaer/event/PacketBundlePool.java);
ViewLoop calls `acquireAvailablePacketBundle()` and **skips**
`extractBundle`. The published bundle is sealed, carries
`AcquisitionMetadata`, and has no `AEPacketRaw` sidecar. Prefs kill-switches
restore raw+extract for validation
(see [usb-live-acquisition-bench.md](usb-live-acquisition-bench.md)).

**Legacy live / file / network path:**
[`AEPacketRawPool`](../src/net/sf/jaer/aemonitor/AEPacketRawPool.java) still
holds packed address+timestamp AEs. [`AEPacketRaw`](../src/net/sf/jaer/aemonitor/AEPacketRaw.java)
and chip `extractPacket` / `extractBundle` remain for AEDAT-2, raw network or
queue transport, acquisition-thread filtering, sequencers, playback, and
unmigrated interfaces — not deleted.

```mermaid
sequenceDiagram
  participant USB as USBTransferThread
  participant Pool as PacketBundlePool / AEPacketRawPool
  participant VL as AEViewer.ViewLoop

  Note over USB,Pool: Multiple libusb transfers in flight
  loop Overlapped bulk IN
    USB->>USB: transfer complete callback
    USB->>Pool: writeBuffer demuxes the selected typed or raw route
  end

  VL->>Pool: synchronized swap()
  Note over Pool: swap read and write buffers, clear new write buffer
  VL->>Pool: acquireAvailablePacketBundle or AEPacketRaw
  VL->>VL: filter / log / render (extractBundle only if no HW bundle)
  Note over USB: Continues filling the other buffer
```

| Family | AEViewer live route | Pref (under `hardware/`) |
|--------|---------------------|--------------------------|
| Davis FX3 / SciDVS (same PID) | **Authoritative typed:** Polarity + Frame + IMU, sealed metadata, no raw sidecar | `DAViSFX3/usbTypedDemux` (RGB color stays legacy) |
| DVXplorer / Mini / Micro | **Authoritative typed:** Polarity + IMU, sealed metadata, no raw sidecar | `DVXplorerFX3/usbTypedDemux` |
| NRV | Legacy raw compatibility (its current typed helper still acquires raw) | `NRV/usbTypedDemux` |
| Prophesee EVK4 | Legacy raw compatibility (its current typed helper still acquires raw) | `Prophesee/usbTypedDemux` |
| DVS128 libusb FX2 | Legacy raw compatibility (its current typed helper still acquires raw) | `CypressFX2DVS128.usbTypedDemux` |

When demux is active on Davis, APS/IMU synthetic AEs are **not** dual-written
into `AEPacketRaw` by default (`DAViSFX3/dualWriteApsImuAe=false`) — the largest
live memory win under APS+DVS. DVXplorer (classic FX3 and Mini/Micro MIPI) skips
filling live `AEPacketRaw` entirely when demux is on (`DVXplorerFX3/usbTypedDemux`).

On the authoritative route, accepted counts by `PacketType`, timestamp epochs,
and exact or unquantified losses come from sealed `AcquisitionMetadata`; an
absent loss record is not replaced with an invented zero. Legacy raw interfaces
continue to report `DroppedDataInfo` / `overrunOccuredFlag` as before.

### Live route selection

AEViewer chooses one route before each live poll and never calls both acquisition
methods for one session:

| Selected route | Conditions |
|----------------|------------|
| Authoritative typed | Normal DAVIS/SciDVS or DVXplorer rendering and typed filtering; AEDAT-4; AEDZ when no legacy sink is active |
| Legacy raw | AEDAT-2; sequencing; active raw unicast or blocking-queue output; acquisition-thread filtering; RGB/unmigrated interfaces |

If one of these conditions changes while acquisition is enabled, ViewLoop first
calls `setEventAcquisitionEnabled(false)`, then lets Cypress select and start the
new route. There is no dual acquisition or typed-plus-raw publication.

---

## ViewLoop processing (one frame)

```mermaid
flowchart TD
  START([ViewLoop iteration]) --> MODE{PlayMode?}

  MODE -->|LIVE| ROUTE{Legacy sink or unmigrated interface?}
  ROUTE -->|no, DAVIS/SciDVS or DVXplorer| HW[Acquire sealed authoritative PacketBundle]
  ROUTE -->|yes| RAW[Acquire AEPacketRaw]
  MODE -->|SEQUENCING| RAW
  HW --> BUNDLE[cookedBundle = hwBundle; rawPacket = null]
  RAW --> EXT[extractBundle]

  MODE -->|PLAYBACK| PLAY[AEPlayer.getNextPacket]
  PLAY --> EXT2[extractBundle]
  EXT2 --> AEDAT4{AEDAT-4 stream?}
  AEDAT4 -->|yes| APPEND[appendTypedPackets FRME/IMUS]
  AEDAT4 -->|no| FILT
  APPEND --> FILT

  EXT --> FILT
  BUNDLE --> FILT

  FILT[FilterChain.filterBundle] --> STORE[chip.setLastBundle / setLastData]
  STORE --> LOG{Logging?}
  LOG -->|AEDAT-4| W4[Aedat4FileOutputStream.writeBundle]
  LOG -->|AEDZ| WZ[AEDZDvsWriterAdapter.writeBundle]
  LOG -->|AEDAT-2| W2[AEFileOutputStream.writePacket]
  LOG -->|off| REN
  W4 --> REN
  WZ --> REN
  W2 --> REN
  REN[renderBundle DavisRenderer / ChipCanvas] --> PACE[FrameRater / sleep]
  PACE --> START
```

Relevant code: `AEViewer.ViewLoop.run()` in
[`AEViewer.java`](../src/net/sf/jaer/graphics/AEViewer.java).

---

## Typed packets and PacketBundle

```mermaid
classDiagram
  class PacketBundle {
    +List~TypedDataPacket~ packets
    +AcquisitionMetadata sourceContext
    +AEPacketRaw rawPacket (legacy bridge only)
    +seal()
    +getNumPolarityEvents()
    +getFirstFramePacket()
  }
  class TypedDataPacket {
    <<interface>>
    +getPacketType()
    +getSize()
  }
  class EventPacket {
    POLARITY / EAR / etc
  }
  class FramePacket {
    FRAME
  }
  class ImuPacket {
    IMU
  }
  PacketBundle --> TypedDataPacket
  TypedDataPacket <|.. EventPacket
  TypedDataPacket <|.. FramePacket
  TypedDataPacket <|.. ImuPacket
```

One ViewLoop timeslice may contain several packets in time order, e.g.
`POLARITY` → `IMU` → `POLARITY` → `FRAME`. Filters and renderers consume the
bundle as a unit. `FilterChain.filterBundle` copies the acquisition session,
sequence, source counts, timestamp epochs, and loss records; filtering does not
rewrite source accounting. A reset-only sealed bundle is retained even if it
contains no events, so its loss/epoch metadata remains reportable.

---

## EventFilters (`FilterChain`)

[`FilterChain.filterBundle`](../src/net/sf/jaer/eventprocessing/FilterChain.java)
walks each typed packet through every **enabled** filter. Each
[`EventFilter2D`](../src/net/sf/jaer/eventprocessing/EventFilter2D.java) declares
`accepts(PacketType)`:

- Polarity denoisers typically accept `POLARITY` only — frames and IMU pass
  through unchanged.
- Filters that want IMU or frames opt in via `accepts`.

```mermaid
flowchart LR
  IN[PacketBundle in] --> P1[Typed packet 1]
  IN --> P2[Typed packet 2]
  IN --> Pn[more packets]

  subgraph Chain["FilterChain (enabled filters in order)"]
    F1[Filter A processTyped]
    F2[Filter B processTyped]
    F3[Filter C processTyped]
  end

  P1 --> F1 --> F2 --> F3 --> OUT1[out packet 1]
  P2 --> F1 --> F2 --> F3 --> OUT2[out packet 2]

  OUT1 --> OUT[PacketBundle out]
  OUT2 --> OUT
```

Legacy path: `filterPacket(EventPacket)` still exists for older call sites and
`FILTER_INPUT` mode; live ViewLoop prefers `filterBundle`.

---

## Rendering / OpenGL

After filtering:

1. `chip.setLastBundle(cookedBundle)` / `setLastData(polarity packet)`.
2. `renderBundle` → chip renderer (`DavisRenderer`, etc.) updates event maps /
   frame textures from typed packets.
3. `ChipCanvas.paintFrame()` or `repaint()` draws via JOGL. See
   [active-vs-passive-rendering.md](active-vs-passive-rendering.md).

Rendering can be **skipped** adaptively under load (`packetLevelRenderSkipping`)
when no filters need every packet and AVI sync recording is not active; recording
of raw data can still occur on the skip path.

---

## Saving to AEDAT-4

When recording is enabled in LIVE (or playback with recording), ViewLoop calls
`recordPacket` each slice:

```mermaid
flowchart TD
  B[chip.getLastBundle] --> W[Aedat4FileOutputStream.writeBundle]
  W --> POL{PacketType?}
  POL -->|POLARITY| EVTS[FlatBuffers EVTS packet]
  POL -->|FRAME| FRME[FlatBuffers FRME packet]
  POL -->|IMU| IMUS[FlatBuffers IMUS packet]
  EVTS --> COMP[LZ4 / ZSTD / NONE per prefs]
  FRME --> COMP
  IMUS --> COMP
  COMP --> FILE[.aedat4 file + FileDataTable on close]
```

- Default recording format can be AEDAT-4; compression is chosen in preferences
  (`Aedat4Compression`).
- On close, the writer logs an estimate such as
  `compressed to XX% of raw (payload … → …)` comparing uncompressed FlatBuffer
  payloads to compressed sizes.
- AEDAT-2 path still writes reconstructed or raw `AEPacketRaw` via
  `AEFileOutputStream`.

### Live AEDZ DVS projection

Live `.aedz` recording stays on the authoritative typed route and writes the
selected filtered or unfiltered `PacketBundle` through
`AEDZDvsWriterAdapter.writeBundle`. It never obtains or reconstructs a whole
`AEPacketRaw`:

- unfiltered polarity events use the exact device address preserved in each
  typed event;
- filtered polarity events are adapted to `ApsDvsEvent` and passed to the
  active `TypedEventExtractor.reconstructRawAddressFromEvent` API, matching the
  existing filtered AEDAT-2 address reconstruction;
- timestamp-epoch changes force AEDZ chunk boundaries;
- frame, IMU, special, and other unsupported payload counts are reported when
  recording closes instead of being silently converted or discarded.

AEDZ is therefore a DVS projection, not a lossless full-sensor format. Use
AEDAT-4 for completed frames and IMU samples. If a raw-only sink is active at
the same time, the legacy route wins and AEViewer reports that AEDZ skipped the
bundle rather than dual-acquiring or inventing a lossy conversion.

**Playback** of AEDAT-4 uses a **sparse packet index** (file offsets + time
bounds + event counts), not a full per-event RAM dump. Polarity is decoded
on demand for the current timeslice; FRME/IMUS are injected via
`appendTypedPackets`.

---

## Sources of input (PlayMode)

| Mode | Input path |
|------|------------|
| `LIVE` | One classified route: authoritative DAVIS/SciDVS or DVXplorer `acquireAvailablePacketBundle`, otherwise raw acquire + extract |
| `PLAYBACK` | `AEPlayer` → `AEFileInputStream` / `Aedat4FileInputStream` |
| `FILTER_INPUT` | Feedback from filters generating events |
| `REMOTE` / sockets | Network AE streams |
| `SEQUENCING` | Hardware sequencer |

File open pauses live acquisition and suppresses USB reopen on the EDT so
ViewLoop does not fight the open dialog (`beginFilePlaybackOpen` /
`suppressHardwareOpen`).

---

## Mental model (ASCII)

```
  Sensor ──USB bulk──► [XFER][XFER][XFER]  (USBTransferThread)
                              │
                              ▼
              PacketBundlePool or AEPacketRawPool write[]
                              │  swap()
              PacketBundlePool or AEPacketRawPool read[]  ◄── ViewLoop
                              │
                     PacketBundle (typed)
                              │
                     FilterChain.filterBundle
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
      Renderer    AEDAT-4 / AEDZ log    Raw network/AVI/…
           OpenGL
```

---

## USB VID/PID and AEChip auto-offer

Libusb factories register `(VID, PID) → HardwareInterface` classes in
[`UsbHardwareRegistry`](../src/net/sf/jaer/hardwareinterface/usb/UsbHardwareRegistry.java)
(the shared lookup; see [README-usb.md](README-usb.md#vidpid-mapping-source-of-truth)
for which factory writes each pair).
AEChip classes may optionally declare the same IDs with `@UsbDevices` /
`@UsbDevice` (inherited), for example on `DavisBaseCamera` or a unique camera
class such as `NRVS5KRC1S`.

When a single USB device is available and the viewer has no open interface
(startup or hot-plug),
[`LiveDeviceChipDetector`](../src/net/sf/jaer/hardwareinterface/usb/LiveDeviceChipDetector.java)
matches that device against the AEChip menu. AEViewer then:

- if a **Remember this selection** mapping exists for `{vid:pid[#serial]}` and is
  still a valid match, applies that AEChip silently;
- binds without prompting if the current chip is the **sole** VID/PID match;
- switches automatically when exactly one menu chip matches but differs from current;
- offers a chooser (OK / Remember this selection / Cancel) when several chips share
  the PID (typical for Davis FX3) — USB cannot distinguish e.g. Davis346 red vs blue:
  - **OK** uses the choice now and as the dialog default next time (still prompts);
  - **Remember this selection** also auto-opens that AEChip when the device is found;
- choosing an AEChip from the **AEChip menu** clears Remember mappings so a different
  camera is not forced to the old variant;
- without Remember, prompts at most once per device key per session.

Prefs keys:
- `AEViewer.liveChipOffer.chip.<deviceKey>` → Remember auto-open FQCN
- `AEViewer.liveChipOffer.default.<deviceKey>` → OK dialog default FQCN

To support a new camera chip, register its VID/PID in the appropriate
hardware-interface factory (which also updates the registry) and annotate the
AEChip:

```java
@UsbDevices({
    @UsbDevice(vid = MyHardwareInterface.VID, pid = MyHardwareInterface.PID)
})
public class MyCamera extends AETemporalConstastRetina { ... }
```

Generic playback-only chips (e.g. `DVS640`) omit `@UsbDevices` and are ignored
by live matching.

---

## Hardware Configuration Save / Load

Biasgen **Hardware Configuration → Save / Save settings as…** exports the chip
preferences node `/jaer/chips/<ChipSimpleName>` (see
[`Biasgen.exportPreferences`](../src/net/sf/jaer/biasgen/Biasgen.java)).

First-use UX flags on that node are **not** written into the XML:

- `defaultPreferencesWereLoaded`
- `firstHardwareUseHandled`

Those keys stay local so a saved/shared settings file does not suppress the
first-use load / Hardware Configuration offer for other users. Remembered live
chip mappings (`AEViewer.liveChipOffer.chip.*`) live under `/jaer/AEViewer` and
are outside Biasgen export entirely.

Shipped defaults under [`deviceSettings/`](../deviceSettings/) use the same
`/jaer/chips/<Chip>` tree (not legacy Java package paths).

**Load** warns if the XML preference node does not match the current AEChip
(prefs import follows the path in the file, so a mismatch usually does not
update the active chip). The user can cancel or load anyway.

---

## Key classes (quick index)

| Area | Classes |
|------|---------|
| Loop | `AEViewer.ViewLoop`, `AEPlayer` |
| USB | `CypressFX3`, `DAViSFX3HardwareInterface`, `NRVHardwareInterface`, `NRVAEReader`, `USBTransferThread` |
| USB match | `UsbHardwareRegistry`, `LiveDeviceChipDetector`, `@UsbDevices` |
| Buffers | `PacketBundlePool`; `AEPacketRawPool` / `AEPacketRaw` at legacy boundaries |
| Typed data | `PacketBundle`, `AcquisitionMetadata`, `PacketType`, `FramePacket`, `ImuPacket`, `EventPacket` |
| Extract | `EventExtractor2D.extractBundle`, `DavisUsbPacketBundleBuilder` |
| Filters | `FilterChain`, `EventFilter2D` |
| Render | `AEChipRenderer`, `DavisRenderer`, `ChipCanvas` |
| Files | `Aedat4FileOutputStream`, `Aedat4FileInputStream`, `AEDZDvsWriterAdapter`, `AEDZOutputStream`, `AEFileOutputStream` |

---

## Related docs

- [README-usb.md](README-usb.md) — USB enumeration, Interface menu, EDT rules, per-camera libusb quirks.
- [CDAVIS_GPU_DEMOSAIC.md](CDAVIS_GPU_DEMOSAIC.md) — GPU demosaic / color display path (orthogonal to PacketBundle).
- [README-cursor-jaer-rules-setup.md](README-cursor-jaer-rules-setup.md) — Cursor Agent loads this file via `AGENTS.md` and `.cursor/rules/jaer3-architecture.mdc`.
