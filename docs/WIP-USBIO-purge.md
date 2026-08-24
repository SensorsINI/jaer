# WIP: USBIO / Thesycon purge

Status: **partial** — native DLLs removed and factories unregistered; Java sources and `UsbIoJava.jar` kept for libusb porting.

Thesycon USBIO was the original Windows USB stack for early jAER boards. Virtually all current cameras use **libusb / usb4java** (`cypressfx2libusb`, `cypressfx3libusb`, Prophesee, NRV, …). `UsbIoUtilities` already soft-fails if the native library is missing.

## Goals

1. Stop shipping / loading Thesycon native code (`usbiojava.dll`).
2. Stop enumerating USBIO devices in the Interface menu (unregister factories).
3. **Keep** Java sources that call `de.thesycon.usbio.*` so boards can later be ported to libusb.
4. **Keep** the purchased VID/PID block documentation (see below).
5. Ask users whether **USBAERmini2** (and related legacy boards) are still in use before deleting USBIO source trees.

## Done (this pass)

| Action | Detail |
|--------|--------|
| Delete natives | `jars/win32` / `jars/win64` (usbiojava.dll); `native/` (was JavaCAN `libjavacan-core.so`) |
| Unregister factories | Removed `SiLabs_USBIO_C8051F3xxFactory` and `USBIOHardwareInterfaceFactory` from `HardwareInterfaceFactory.factories` |
| Installer upgrade cleanup | `install4j/jaer.install4j` removes leftover `usbiojava.dll` and empty `jars/win32` / `jars/win64` |
| Keep compile jar | `jars/UsbIoJava.jar` remains on the classpath so USBIO sources still compile |
| Keep sources | All `cypressfx2/` USBIO, SiLabs USBIO, cochlea USBIO, servo, Toradex, etc. |

Direct callers of `ServoInterfaceFactory` / `CarServoInterfaceFactory` still exist; without the DLL they soft-fail via `UsbIoUtilities.isLibraryLoaded()`.

## Preserve: VID/PID block from Thesycon

jAER licensed a VID/PID range from Thesycon (assignment **16.6.2008**). This is **not** tied to the USBIO driver and must remain after any purge.

| Item | Value |
|------|--------|
| VID | `0x152A` (`USBInterface.VID_THESYCON`) |
| PID range | `0x8400`–`0x841F` (32 PIDs) |
| Constants | `src/net/sf/jaer/hardwareinterface/usb/USBInterface.java` |
| Original assignment PDF | `docs/USBIO_VID_PID_Assignments_Neuroinformatik.pdf` (git-tracked; filename is historical) |

Javadoc in `USBInterface` still mentions a missing `drivers/readme.txt` for live assignments; prefer the PDF + constants until a markdown registry is added.

**Note:** Some historical PIDs used with jAER boards sit outside `0x8400`–`0x841F` (e.g. Tmpdiff `0x8700`, USBAERmini2 `0x8801`, USB2AERmapper `0x8900`, servo `0x8750`). Document those in device classes; the purchased block above is the formal Thesycon range.

## Already on libusb (unaffected)

- DVS128 / Tmpdiff128 (dual-path; libusb factories remain)
- Cochlea AMS (`0x8405` via libusb)
- SiLabs PAER `0x8411` (libusb; Linux registration)
- All FX3: DAVIS, DVXplorer, CochleaFX3
- Prophesee, NRV, OpalKelly, UDP, eDVS, SpiNNaker

## USBIO-only (runtime unavailable after this pass)

These still have **source** for a future libusb port, but will not appear via `HardwareInterfaceFactory` and cannot open without the DLL:

| Device / PID | Source / notes |
|--------------|----------------|
| USB servo `0x8750` | `SiLabsC8051F320_USBIO_ServoController`; pantilt/goalie/slotcar consumers |
| Car servo `0x8751` | `SiLabsC8051F320_USBIO_CarServoController` |
| **USBAERmini2** `0x8801` | `CypressFX2MonitorSequencer` — **user inquiry pending** |
| USB2AERmapper `0x8900` | `CypressFX2Mapper` |
| AE sequencer `0x8410` | `SiLabsC8051F320_USBIO_AeSequencer` |
| CochleaAMS1c USBIO `0x8406` | chip package USBIO class (not in libusb FX2 map) |
| Stereo DID path | `CypressFX2UsbIoStereoBoard` |
| Toradex Oak accel | `ToradexOakG3AxisAccelerationSensor` |

## Source files with `de.thesycon` imports (keep for porting)

About 32 Java files under `src/` import Thesycon APIs, including:

- `hardwareinterface/usb/UsbIoUtilities.java`
- `hardwareinterface/usb/cypressfx2/*` (USBIO FX2 stack + `USBIOHardwareInterfaceFactory`)
- `hardwareinterface/usb/silabs/*USBIO*`
- Cochlea USBIO hardware interfaces
- Servo / RC-car / slotcar factories and controllers
- Soft PnP: `HardwareInterfaceFactory`, `HardwareInterfaceMenu`, `JAERTrayLauncher`, OpalKelly factory/monitor stubs

Parallel libusb packages to prefer / extend when porting:

- `net.sf.jaer.hardwareinterface.usb.cypressfx2libusb`
- `net.sf.jaer.hardwareinterface.usb.cypressfx3libusb`
- `SiLabsC8051F320_LibUsb` / `SiLabsC8051F320_LibUsb_PAER`

## Open questions

1. **Are users still using USBAERmini2?** If no, source can later be archived or deleted after any needed logic is ported.
2. Same for USB2AERmapper, AE sequencer, USBIO-only cochlea `0x8406`, and SiLabs servo boards.
3. Whether to add a markdown PID assignment table (replacing the missing `drivers/readme.txt`) next to the PDF.
4. Whether to eventually drop `UsbIoJava.jar` and rewrite remaining classes to remove `de.thesycon` compile dependency (larger change).

## Later work (not done yet)

- [ ] Collect feedback on USBAERmini2 / other USBIO-only boards
- [ ] Port still-needed boards to libusb (keep USBIO sources as reference until ports land)
- [ ] Optional: remove `UsbIoJava.jar` and Thesycon imports after ports / retirement
- [ ] Optional: rename PDF / add `docs/usb-vid-pid-assignments.md` (keep PDF)
- [ ] Optional: stop implementing `PnPNotifyInterface` on core UI once nothing needs Thesycon PnP

## Related packaging

- Classpath: `nbproject/project.properties` still references `UsbIoJava.jar`
- Eclipse launch configs now use `java.library.path=jars` (no `win32`/`win64` subdirs)
- Runtime already logs a soft warning from `UsbIoUtilities` when the native lib is absent
