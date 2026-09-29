# Adding a device to jAER

How to add a live camera (or a playback-only chip) and open a pull request
against [SensorsINI/jaer](https://github.com/SensorsINI/jaer). Follow this
when implementing device support.

A device PR includes the driver, a row in the main README hardware table, and
**test results from real hardware**: a log that shows the checklist below
running, with no exceptions and no warnings you can fix.

## Docs to read while integrating

These are the sources of truth for the pieces a new device has to meet.
Read the row that matches the code you are about to write.

| You are changing | Read |
|------------------|------|
| How events move from USB to the screen and into a recording | [README-jaer3.md](README-jaer3.md): [data flow](README-jaer3.md#high-level-data-flow), [PacketBundle](README-jaer3.md#typed-packets-and-packetbundle), [ViewLoop](README-jaer3.md#viewloop-processing-one-frame), [saving AEDAT-4](README-jaer3.md#saving-to-aedat-4) |
| VID/PID, which AEChip is offered, bias Save/Load | [README-jaer3.md](README-jaer3.md): [USB VID/PID and AEChip auto-offer](README-jaer3.md#usb-vidpid-and-aechip-auto-offer), [Hardware Configuration Save / Load](README-jaer3.md#hardware-configuration-save--load) |
| Enumeration, Interface menu, open/close, hot-plug, EDT | [README-usb.md](README-usb.md): [factories](README-usb.md#factories-and-enumeration), [VID/PID map](README-usb.md#vidpid-mapping-source-of-truth), [Interface menu and EDT](README-usb.md#interface-menu-edt-welcome-overlay), [open, config, close](README-usb.md#open-config-close-abandon), [per-device libusb quirks](README-usb.md#peculiarities-of-particular-usb-interfaces-libusb) |
| What AEDAT-4 recording and playback must contain | [README-file-formats.md](README-file-formats.md): [summary](README-file-formats.md#summary), [recording](README-file-formats.md#recording-formats-detail) |
| A second Prophesee or NRV sensor (subclass, or a new superclass) | [src/prophesee/README.md](../src/prophesee/README.md), [src/nrv/README.md](../src/nrv/README.md) |
| Factory bias files under `deviceSettings/` | [deviceSettings/readme.txt](../deviceSettings/readme.txt) |
| Compile, run, and the AEChip allowlist | [README.md — Developing with jAER](../README.md#developing-with-jaer) |
| A UVC / OpenCV frame source (frames, not DVS events) | [README-frame-cameras.md](README-frame-cameras.md) |
| USB throughput once events already display | [usb-live-acquisition-bench.md](usb-live-acquisition-bench.md) |

---

## 1. Fork and branch

1. Fork [SensorsINI/jaer](https://github.com/SensorsINI/jaer) on GitHub.
2. Clone your fork and add upstream:

```text
git clone https://github.com/<you>/jaer.git
cd jaer
git remote add upstream https://github.com/SensorsINI/jaer.git
git fetch upstream
git checkout master
git merge upstream/master
git checkout -b add-<vendor>-<product>
```

JDK **25** (`ant check-jdk`). Build with Ant, not Maven or Gradle.

---

## 2. Subclass an existing device

Add the smallest new type that reaches the existing pipeline
(`HardwareInterface` → `AEPacketRaw` → extractor → `PacketBundle` →
`ViewLoop`). Copying a whole driver into a new top-level package duplicates
USB close/hotplug behavior that already works.

| Situation | Where the new type goes |
|-----------|-------------------------|
| Same silicon, new board (different `bcdDevice`, shared VID/PID) | Subclass the existing `HardwareInterface`. One registry entry. `getInterface` chooses the subclass from the descriptor, without `LibUsb.open`. See DVXplorer FX3 vs Mini/Micro. |
| Another sensor from **Prophesee** or **NRV** | New classes under `src/prophesee` or `src/nrv`. When init, the parser, or the bias UI is shared, pull that into an abstract superclass in the same package and subclass it. |
| iniVation / inilabs DVS or DAVIS | Subclass `DavisBaseCamera`, `AETemporalConstastRetina`, or the existing FX2/FX3 interface. |
| Playback only (no USB) | Concrete `AEChip` with **no** `@UsbDevices` and **no** factory `addDeviceToMap`. `DVS640` is the pattern. |

`PropheseeIMX636HD` and `NRVS5KRC1S` are the manufacturer-package examples:
`AETemporalConstastRetina`, `@UsbDevices` using constants from the hardware
interface, extractor, and a config that implements `DVSTweaks`.

Mark the chip `@DevelopmentStatus(DevelopmentStatus.Status.Experimental)`
until the checklist below passes on hardware. Set `Stable` in the same PR
when the attached log shows that run.

---

## 3. Wire USB and the chip menu

Numeric VID/PID constants live on the `HardwareInterface`
(`PropheseeHardwareInterface.PID_EVK4_HD`). The factory calls
`addDeviceToMap` with those fields. That call fills
[`UsbHardwareRegistry`](../src/net/sf/jaer/hardwareinterface/usb/UsbHardwareRegistry.java)
and the factory’s private map. A second hex copy of the IDs will drift.

A **new vendor** gets its own `HardwareInterfaceFactory` and one new entry in
`HardwareInterfaceFactory.factories`. An existing vendor uses the factory
already in that list (`LibUsb3HardwareInterfaceFactory`,
`NRVHardwareInterfaceFactory`, `PropheseeHardwareInterfaceFactory`,
`LibUsbHardwareInterfaceFactory`).

Annotate the AEChip with the same constants so
[`LiveDeviceChipDetector`](../src/net/sf/jaer/hardwareinterface/usb/LiveDeviceChipDetector.java)
can offer it:

```java
@UsbDevices({
    @UsbDevice(vid = MyHardwareInterface.VID, pid = MyHardwareInterface.PID)
})
public class MyCamera extends AETemporalConstastRetina { ... }
```

Also:

- Scan uses `LibUsb.getDeviceDescriptor` only. `open()` during
  `buildInterfaceList` races other viewers on Windows.
- `toString()` / Interface menu labels use `UsbIds.unopenedLabel` until
  `isOpen()`.
- `open()`, config, and `close()` stay off the Swing EDT. See
  [README-usb.md](README-usb.md#interface-menu-edt-welcome-overlay).
- Chip size, `setEventExtractor`, and `setBiasgen` are set in the constructor.
- Shipped bias defaults go under `deviceSettings/<ChipSimpleName>/` as
  `/jaer/chips/<ChipSimpleName>` (see [Hardware Configuration](README-jaer3.md#hardware-configuration-save--load)).
  Call `setDefaultPreferencesFileForFamily` so the file is
  `deviceSettings/<family>/<ChipSimpleName>.xml`. First use imports that file
  when the chip is chosen from the **Sensor** menu with no camera open
  (`loadMissingDefaultBiases`) and when it first goes live. Playback-only
  chips use the same file and the same menu path.
- Add a row to the hardware table in the repo-root [README.md](../README.md)
  (product, manufacturer, resolution, interface, chip class, status).
- A short `src/<vendor>/README.md` for a new family (USB IDs, event format,
  timestamp notes, bias mapping).

`ant compile` writes the AEChip allowlist into `jAER.jar`. The new chip
appears in the AEChip menu only after that compile. IDE compile-on-save is
not enough.

After factory or close/hotplug changes, run the headless check (`;` on
Windows, `:` on Linux/macOS):

```text
ant compile
java -cp build/classes;jars/*;lib/* net.sf.jaer.hardwareinterface.usb.UsbEnumerationSafetyDemo
```

Extend that demo when the device adds a new close or hotplug contract.

---

## 4. Timestamps (relative µs in, Unix µs on disk)

File layout and what File → Show file info reports:
[README-jaer3.md — Saving to AEDAT-4](README-jaer3.md#saving-to-aedat-4),
[README-file-formats.md](README-file-formats.md#recording-formats-detail).

Live events use a **signed 32-bit microsecond** timestamp relative to a
session origin. Key **`0`** calls `resetTimestampOrigin()`. AEDAT-4 stores
**Unix microseconds**: `Aedat4FileOutputStream.toUnixUs` adds the recording
start (`System.currentTimeMillis() * 1000`) after
`TimestampUnwrapper` unfolds the 32-bit value. File → Show file info, seek,
and jog use those packet bounds.

The driver emits monotonic relative microseconds. It leaves Unix time to the
writer. A sensor clock that runs fast or slow versus the host is corrected
in the parser before events are committed (NRV CX3 host stretch in
[`src/nrv/README.md`](../src/nrv/README.md) is the example). After about
35.8 minutes the relative `int` wraps; unwrapping must keep file time
increasing for the whole recording.

---

## 5. User-friendly DVS bias panel

DVS cameras expose the shared panel
[`DVSUserControlPanel`](../src/ch/unizh/ini/jaer/chip/retina/DVSUserControlPanel.java):
brightness-change threshold, ON/OFF balance, pixel bandwidth, and max firing
rate. Sliders are −1…1 offsets around the last loaded or saved snapshot.

The bias object implements [`DVSTweaks`](../src/ch/unizh/ini/jaer/chip/retina/DVSTweaks.java)
and [`ChipControlPanel`](../src/net/sf/jaer/biasgen/ChipControlPanel.java).
Build the tab with `DVSUserControlPanel` or a thin subclass
(`PropheseeUserControlPanel`, `NRVUserControlPanel`) and select it with
`DVSUserControlPanel.selectUserFriendlyTab`. Map each slider onto the
sensor’s real threshold, balance, bandwidth, and refractory controls.
Document polarity (which direction raises the ON threshold) in the vendor
README. Raw register sliders can sit on a second tab.

The first time the class is used, that panel shows the shipped factory
biases, including when the chip was only added from the Sensor menu and no
camera is open.

---

## 6. Local test plan

USB pass/fail behavior is specified in
[README-usb.md](README-usb.md#open-config-close-abandon). Recording and
playback behavior is specified in
[README-file-formats.md](README-file-formats.md#recording-formats-detail).

Run `ant compile` then `ant run` (or `scripts/run-jaer-fast.bat` /
`scripts/run-jaer-fast.sh`) against the camera. Test on the OS you have and
name it in the PR. Console logging is INFO. The session log is
`%TEMP%\jaer\jAER-0.log` on Windows (`java.io.tmpdir/jaer/jAER-0.log`
elsewhere). USB open/close detail is `usb-open-trace.log` in that same
folder.

Do these in one session, in order, and keep the log.

| Step | What you do | What “working” means |
|------|-------------|----------------------|
| Enumerate | Plug in. Open **Interface**. | The camera is listed with family name and bus/address (`UsbIds.unopenedLabel`). The AEChip menu offers this chip. The UI stays responsive while the scan runs. |
| Open | Select the interface. | Status becomes live. Events match the scene. Log shows open without `LIBUSB_ERROR_*` or an exception. |
| Pause / resume | **Space** twice (pause, resume), then again. | The image freezes, then new events appear. A second cycle still works. Live **Space** is the same pause action as playback. |
| Close / open | **Interface → None** (`Ctrl+W`), then select the camera again. Repeat. | WAITING, then live events. The second open does not report the device still claimed. |
| Hot-plug | While live, unplug. Wait until WAITING. Plug back in. Open again. | Unplug does not freeze the window. Replug lists the device. Open shows events. |
| Factory biases, Sensor menu | Unplug the camera (or **Interface → None**). If this class was already opened on this computer, delete the Java preference node `/jaer/chips/<ChipSimpleName>` and restart. **Sensor** menu → the new class (add it with **Sensor → Customize** if it is not listed). Open **Hardware Configuration**. | The settings panel opens and shows the factory biases from `deviceSettings/`, not zeros and not the uninitialized-biases warning. The log contains `loading shipped default biases` and `importing initial preferences` for this class. A playback-only chip uses this same check. |
| Factory biases, live | Delete `/jaer/chips/<ChipSimpleName>` again so this open is also a first use. Plug in and open the camera. | An “Initial preferences loaded” dialog appears. Hardware Configuration opens on its own with the same factory values. The log contains `importing initial preferences` and `showing initial-preferences dialog`. |
| Biases | On the user-friendly tab, move threshold, balance, bandwidth, and max rate. **Save**. Restart jAER and open the camera. | Each slider changes the live stream in the documented direction. After restart the sliders are centered on the saved values. Skip the live half of this row for a playback-only chip; still confirm the panel shows the factory values and that Save/Load round-trips. |
| Record | Record at least one minute of AEDAT-4 (menu accelerator **L**). Wave a hand so events exist. Stop. | File → Show file info: start time is Unix time near the wall clock when recording started, duration matches a stopwatch, event counts are non-zero. |
| Play | **File → Open** that file. | Events play forward in the same orientation as live view. |
| IN / OUT / marker | While playing: **i** (IN), **o** (OUT), **m** (marker). Rewind (**R**). Play through the span. Reopen the file. | Playback honors IN and OUT. The marker is listed. Reopen restores the marks. |
| Jog | **Playback → Jog Forward N packets** (`Ctrl+Shift+.` ) and **Jog back N view slices** (`Ctrl+Shift+,`). Also step with **.** and **,**. | Forward jog shows later events; backward jog shows earlier events. The on-screen time moves the same direction. **Esc** cancels a long jog. No exception, no jump to the wrong end of the file. |

Fix exceptions and warnings that mean a failed open, a non-monotonic
timestamp, an EDT stall, or a bind/close retry loop. Mention any remaining
warning in the PR and why it is benign.

---

## 7. Pull request

Push the branch to your fork and open a PR to `SensorsINI/jaer` `master`.

Attach the trimmed `jAER-0.log` from the checklist session (and
`usb-open-trace.log` when open/close or hot-plug needed it). The log is the
evidence that enumeration, open, close, hot-plug, pause/resume, factory
biases (Sensor menu and live), AEDAT-4 record/play, IN/OUT/marker, and jog
ran. Put the checklist in the PR
body with pass/fail and the OS:

```text
## Device
- Product, VID:PID, chip class, hardware interface

## Test
- OS:
- Log: jAER-0.log attached (time range …)

- [ ] USB enumeration
- [ ] Open
- [ ] Pause / resume (Space)
- [ ] Close and open again
- [ ] Hot-plug
- [ ] Factory biases on first Sensor-menu selection (no camera)
- [ ] Factory biases on first live open
- [ ] User-friendly DVS bias panel
- [ ] AEDAT-4 record; File → Show file info Unix start and duration
- [ ] Playback
- [ ] IN / OUT / marker
- [ ] Jog forward and backward

## Notes
Warnings left in the log, and why.
```
