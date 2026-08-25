# USB cameras, libusb, and the Interface menu

How jAER finds USB cameras, what must not run on the Swing EDT, how open/close
is sequenced, and **per-device libusb quirks**. Live USB is used on Windows
(WinUSB via usb4java), Linux, and macOS. Native `LibUsb.controlTransfer` /
`LibUsb.bulkTransfer` timeouts are often **not honored on Windows WinUSB**: a
500 ms timeout can block forever. Linux and macOS are more reliable.

Related: [usb-live-acquisition-bench.md](usb-live-acquisition-bench.md)
(FIFO / typed demux), [WIP-USBIO-purge.md](WIP-USBIO-purge.md) (Thesycon USBIO
is not registered), [src/prophesee/README.md](../src/prophesee/README.md),
[src/nrv/README.md](../src/nrv/README.md).

Headless source contracts (no hardware): after `ant compile`,

```text
java -cp build/classes;jars/* net.sf.jaer.hardwareinterface.usb.UsbEnumerationSafetyDemo
```

On Linux/macOS use `:` instead of `;` in the classpath.

Session USB logs: `%TEMP%\jaer\jAER-0.log` (`conf/Logging.properties`).

---

## Factories and enumeration

[`HardwareInterfaceFactory`](../src/net/sf/jaer/hardwareinterface/HardwareInterfaceFactory.java)
is a singleton. Registered factories (`HardwareInterfaceFactory.factories`):

| Factory | Typical cameras |
|---------|-----------------|
| `LibUsbHardwareInterfaceFactory` | DVS128 / other Cypress FX2 libusb |
| `LibUsb3HardwareInterfaceFactory` | DAVIS346, SciDVS (same PID), DVXplorer / Mini / Micro, Cochlea FX3 |
| `NRVHardwareInterfaceFactory` | NRV DELTA01 FX20/CX3 |
| `PropheseeHardwareInterfaceFactory` | EVK4 HD |
| `UDPInterfaceFactory`, `eDVS128_InterfaceFactory`, `SpiNNaker_InterfaceFactory`, `OpalKellyFX3Factory` | non-libusb or special |

Thesycon USBIO factories are **not** in this list.

`buildInterfaceList()` walks every factory. It is expensive. The Interface menu
uses `getCachedNumInterfacesAvailable()` on the EDT so a live camera is not
re-scanned. **Refresh** enumerates off the EDT.

`usbEnumerationDirty` plus [`LibUsbHotplug`](../src/net/sf/jaer/hardwareinterface/usb/LibUsbHotplug.java)
avoid a full `getDeviceList` on every poll when hotplug is supported.

FX2 and FX3 factories list devices by **VID/PID from `LibUsb.getDeviceDescriptor`
only**. They must not `LibUsb.open` during scan: opening while another handle is
live races on Windows (`LIBUSB_ERROR_ACCESS` / WinUSB hang on the EDT).

Hardware `toString()` must not open USB. Unopened labels use
[`UsbIds.unopenedLabel`](../src/net/sf/jaer/hardwareinterface/usb/UsbIds.java)
(VID/PID, bus/address).

---

## VID/PID mapping (source of truth)

**Runtime table:**
[`UsbHardwareRegistry`](../src/net/sf/jaer/hardwareinterface/usb/UsbHardwareRegistry.java)
is the single shared map `(VID, PID) → HardwareInterface` class. Lookups go
through `UsbHardwareRegistry.interfaceClassFor` /
`HardwareInterfaceFactory.interfaceClassForUsb`. WinUSB-driver hints also use
`UsbHardwareRegistry.isSupported`.

**Who fills it:** each libusb factory constructor, via `addDeviceToMap(vid, pid,
cls)`. That call also stores the pair in the factory’s private
`vidPidToClassMap` (used to **construct** the wrapper during scan) and
registers the pair with [`LibUsbHotplug`](../src/net/sf/jaer/hardwareinterface/usb/LibUsbHotplug.java).
The factories that write the registry:

| Factory | Typical `(VID, PID)` |
|---------|----------------------|
| `LibUsb3HardwareInterfaceFactory` | `152a:841a` DAVIS/SciDVS, `152a:841b` DAVIS FX2 PID, `152a:8419` DVXplorer, `152a:841c` Cochlea FX3 |
| `LibUsbHardwareInterfaceFactory` | `152a:8400` DVS128, `152a:8700` Tmpdiff128, `152a:8406` CochleaAMS, `152a:8411` SiLabs PAER, … |
| `NRVHardwareInterfaceFactory` | `04b4:00f0` FX20, `04b4:00f1` CX3 |
| `PropheseeHardwareInterfaceFactory` | `04b4:00f5` EVK4 HD |

**Numeric constants** live on the HardwareInterface class (`DAViSFX3HardwareInterface.PID_FX3`,
`PropheseeHardwareInterface.PID_EVK4_HD`, …). Factory `addDeviceToMap` calls
must use those fields, not a second copy of the hex.

To add a live USB camera: put `VID`/`PID_*` on the HardwareInterface, call
`addDeviceToMap` in that device’s factory (this updates the registry), and
annotate matching AEChip classes with `@UsbDevices` using the **same**
constants.

**Not this map:** [`@UsbDevices`](../src/net/sf/jaer/UsbDevices.java) /
`@UsbDevice` on AEChip classes is a separate mapping for
[`LiveDeviceChipDetector`](../src/net/sf/jaer/hardwareinterface/usb/LiveDeviceChipDetector.java)
(which AEChip menu entries fit a plugged-in camera). Several chips can share
one PID (Davis346 red vs blue vs SciDVS). Chip auto-offer is described in
[README-jaer3.md](README-jaer3.md#usb-vidpid-and-aechip-auto-offer).

Same PID with a different `bcdDevice` (DVXplorer Mini/Micro type 4 vs classic
FX3 types 1–3) is **not** a second registry entry; the factory still constructs
`DVXplorerFX3HardwareInterface` and the subclass reads `bcdDevice` after
`getDeviceDescriptor` (no string descriptors).

Interface menu and Welcome overlay labels should name the family from this
VID/PID path (`UsbIds` / registered HI class), not USB string descriptors.

---

## Interface menu, EDT, Welcome overlay

- Skip the already-open camera with `UsbIds.samePhysicalDevice` (libusb bus/address),
  not product-string equality.
- Menu text: `interfaceMenuLabel` / `UsbIds.unopenedLabel`. Do not call
  `getStringDescriptors()` unless `isOpen()`.
- **None** closes asynchronously: `closeHardwareInterfaceWithTimeout` (3 s
  watcher, thread `jaer-hw-close`).
- Selection sets `hardwareSwitchInProgress` until bind + `WAITING`. ViewLoop
  must not `openAEMonitor` while the previous HI is nulled and the next chip
  is still being constructed. `interruptViewloop` runs **after** that flag
  clears, and open/close waits ignore it (do not unbind a 1 ms Davis open).
- Selection binds on the EDT (`ensureChipCompatibleWithLiveDevice`). SciDVS FPGA
  probe is **not** on the EDT.
- Several plugged cameras: no auto-bind (`openHardwareIfNonambiguous` requires a
  single device). Welcome lists Interface-menu labels.
- While WAITING with no open hardware, [`ChipCanvas`](../src/net/sf/jaer/graphics/ChipCanvas.java)
  blanks the chip pixmap (`shouldSkipChipDisplay` / `isWelcomeOverlayActive`) so
  leftover APS/DVS is not drawn under the overlay. Interface click / bind shows
  [`Welcome.opening`](../src/net/sf/jaer/Welcome.java) (title plus “Opening …”).

---

## Open, config, close, abandon

`AEViewer.openAEMonitor()` starts thread `jaer-aemon-open`. ViewLoop waits:

| Device | Wait |
|--------|------|
| Cypress FX2/FX3, DVX, NRV, … | 8 s (`HARDWARE_OPEN_WAIT_MS`) |
| Prophesee EVK4 | 45 s (`HARDWARE_OPEN_WAIT_PROPHESEE_MS`) — ISSD / Treuzell bulk |

On timeout or Interface change: `unbindAbandonedHardware`, set `nullInterface = true`.
Do **not** call `close()` on a hung synchronized `open()` (that waits on the same
monitor as the stuck native transfer). `nullInterface` also after
`LIBUSB_ERROR_ACCESS` so WAITING does not rebind a ghost device every ~3 s.

`sendConfiguration` / DVX `dvxConfig` runs on **the same opener thread** after
`open()` returns, **before** PlayMode LIVE. There is no parallel `jaer-send-biases`
thread.

`CypressFX3Biasgen.open()` does not send biases; that is the opener’s job.

FX3 `close()` must stop the AEReader and `setInEndpointEnabled(false)` **while
`isOpen()` is still true**, then set `isOpened = false`. Clearing `isOpened` first
made `setEventAcquisitionEnabled(false)` a no-op; leftover bulk URBs then hung
the next open (None → Davis). `isOpen()` is not synchronized (`volatile isOpened`)
so EDT paint does not wait on a hung USB monitor.

If the AEReader join times out, **do not** `LibUsb.releaseInterface` /
`LibUsb.close`: that crashed the JVM (`hs_err_pid34924`, `ntdll` AV on
`jaer-hw-close` with two `AEReaderThread`s still in native). Abandon the Java
handle. `CypressFX3-USB-recover` must not start a second `close()` from inside
the synchronized closer.

ViewLoop `openAEMonitor` **joins** the previous `jaer-hw-close` (20 s,
`HARDWARE_CLOSE_JOIN_MS`) before starting the next camera. Prophesee ISSD
stop/destroy often exceeds 3 s; a 3 s join then opened Davis while EVK4 close
was still in Treuzell, and `interruptViewloop` unbound the Davis wrapper.
`interruptViewloop` during that join or the following `open()` wait is ignored
unless ViewLoop is stopping. Interface switch must not open Prophesee/Davis
while the previous close is still in native USB.

FX2 libusb `close()` already stops acquisition before `isOpened = false`.

Remember-last Cypress serial: no `LibUsb.open` on ViewLoop.

---

## Peculiarities of particular USB interfaces (libusb)

Windows WinUSB: `LibUsb.getStringDescriptor` has **no timeout**. After a rapid
Interface switch, both that API and `controlTransfer(GET_DESCRIPTOR)` can hang
indefinitely. FX3 and Prophesee therefore **do not** read USB strings on open.
[`LibUsbStringDescriptors`](../src/net/sf/jaer/hardwareinterface/usb/LibUsbStringDescriptors.java)
exists for timed GET_DESCRIPTOR (500 ms); FX3/Prophesee skip strings entirely
instead, because the timeout is often ignored on WinUSB.

Vendor `controlTransfer` timeout in Cypress FX3 is 500 ms (`VENDOR_REQUEST_TIMEOUT_MS`).
On Windows it may never return.

Default `CypressFX3.shouldResetUsbDevice()` is **false**. USB reset after open
was a historical workaround for hanging string descriptors and can itself hang
after a rapid switch. `DVXplorerFX3HardwareInterface` still resets **non-CX3**
(classic FX3 DVXplorer, `bcdDevice` type 1–3); Mini/Micro (type 4) do not reset.

### Cypress FX3 DAVIS / SciDVS — VID:PID `152a:841a` (FX3), `152a:841b` (FX2 PID on some boards)

- Factory: `LibUsb3HardwareInterfaceFactory` → `DAViSFX3HardwareInterface`.
  SciDVS shares these PIDs; `SciDVSHardwareInterface` is not registered.
- Open: skip USB string descriptors; no USB reset (`shouldResetUsbDevice` false).
- After Interface switch, first SPI `controlTransfer` (`getRealClockValues` /
  `adjustHWParam` / `sendConfiguration`) can hang even with a 500 ms timeout.
  The 8 s opener abort unbinds Java-side; the native thread may stay in USB.
- Close: stop AEReader before `isOpened = false`. AEReader join is 3 s; then
  `disableINEndpoint` SPI can still hang if the reader did not stop.
- Do not select the same camera again while `jaer-hw-close` is still running:
  a new `LibUsb.open` can succeed in 1 ms, then the first SPI IN hangs.

### DVXplorer Mini / Micro — VID:PID `152a:8419`, `bcdDevice` type 4, firmware ≥ 10

Same factory PID as classic FX3 DVXplorer; distinguish with `bcdDevice` high
byte (`DEVICE_TYPE_CX3_MIPI = 4`) and firmware nibble.

- Skip vendor `VR_DATA_CLEANUP` (0xC6) on open: native `controlTransfer` did not
  return (timeout unused).
- Skip 8-byte SPI **IN** and non-run **OUT** (`spiConfigReceive` / `spiConfigSend`,
  req 0xBF): size/orientation and DVS_FLATTEN hang on WinUSB. Size defaults to
  **640×480**. After USB IN is queued, send **8-byte** `DVS_RUN` only (firmware
  10 stalls 4-byte `wLength` with `LIBUSB_ERROR_PIPE`; jAER 8:58:38). Do not
  spawn+join that send while holding the CypressFX3 monitor.
- Classic FX3 DVXplorer (types 1–3) still uses SPI IN/OUT and USB reset on close/open.

### Prophesee EVK4 HD — VID:PID `04b4:00f5`

- No `LibUsb.getStringDescriptor` on open (serial comes from ISSD).
- After claim, `Imx636Init` + `Evk4BoardCommand.bulkTransfer` (synchronous
  Treuzell). `requestOpenAbort()` cannot cancel an in-flight native
  `LibUsb.bulkTransfer`. ViewLoop waits **45 s** then abandons.
- Event path is async `USBTransferThread` on bulk IN `0x81`. Control path is
  sync bulk. Draining a streaming EVK4 to reconfigure FIFO can block for tens
  of seconds and stall 0x81 (`LIBUSB_ERROR_IO`); see
  [usb-live-acquisition-bench.md](usb-live-acquisition-bench.md).
- Linux `LIBUSB_ERROR_ACCESS`: udev permissions or another process; Windows:
  WinUSB (Zadig / Prophesee wdi-simple), not libusb-win32.

### NRV DELTA01 — VID:PID `04b4:00f0` (FX20), `04b4:00f1` (CX3)

- Still calls `LibUsb.getStringDescriptor` in a loop on open. Linux has been
  reliable; Windows can hang the same way FX3/Prophesee used to. Failures are
  logged; open continues if the catch fires, but a native hang never reaches
  the catch.
- Async bulk readout like Prophesee (`USBTransferThread`). I2C via vendor
  requests `0xBA` / `0xAB`.

### DVS128 Cypress FX2 libusb

- Factory: `LibUsbHardwareInterfaceFactory`. Scan is VID/PID only (no
  `LibUsb.open`).
- Open still uses `LibUsb.getStringDescriptor` (no timeout) in
  `CypressFX2.populateDescriptors()`. Linux OK in recent tests; Windows after
  a rapid switch is the same class of hang as FX3 strings used to be.
- Close stops acquisition while still open, then clears `isOpened`.

### Other libusb devices on the FX3 factory

- **Cochlea FX3** (`152a:841c`): same CypressFX3 open/close/string-skip path as
  DAVIS.
- UDP, eDVS128, SpiNNaker, OpalKelly: not usb4java bulk/control; they do not
  share these WinUSB timeout issues.

---

## Exceptions during enumeration and open

| Symptom | Typical cause | What jAER does |
|---------|---------------|----------------|
| `LIBUSB_ERROR_ACCESS` | Another handle, udev, wrong WinUSB driver | Fail open; `nullInterface` so WAITING does not retry every 3 s |
| `LIBUSB_ERROR_BUSY` / `NOT_SUPPORTED` | Driver or exclusive owner | Same; Windows hint in `libUsbOpenHint` |
| Open worker still alive after 8 s / 45 s | Native control/bulk never returned | Unbind; do not `close()` the hung instance |
| Interface menu / EDT freeze | USB or SciDVS probe on EDT, or `toString()`/`getStringDescriptors` while closed | Must not happen; scan and probe stay off EDT |
| Ghost CypressFX3 after failed open | Menu skip by `toString()` equality | Skip by `UsbIds.samePhysicalDevice` |

Unplug does not unblock a thread already inside a hung WinUSB transfer; kill the
JVM.
