# Frame cameras (OpenCV / webcam)

jAER can open **standard UVC webcams** (and other devices OpenCV `VideoCapture` sees) as a frames-only AEChip. Live data is RGB [`FramePacket`](../src/net/sf/jaer/event/FramePacket.java)s, recorded as AEDAT-4 **FRME**.

Related: [README-jaer3.md](README-jaer3.md) (ViewLoop / PacketBundle), [README-usb.md](README-usb.md) (Interface menu / EDT — USB scan must not open cameras).

## Interface menu

[`OpenCvCameraFactory`](../src/net/sf/jaer/hardwareinterface/opencv/OpenCvCameraFactory.java) is registered next to the libusb factories. It keeps a **cached** device list:

- Never open `VideoCapture` on the Swing EDT.
- Do **not** probe indices on every USB WAITING poll. `getNumInterfacesAvailable()` returns the last snapshot only.
- Probe off-EDT at factory construction (background) and on **Interface → Refresh**.
- Indices `0..7`, backend `CAP_DSHOW` (Windows), `CAP_AVFOUNDATION` (macOS), `CAP_V4L2` (Linux).
- If OpenCV natives fail to load (`OpenCVNativeLoader`), the list is empty.

Label example: `OpenCV: 0 DSHOW 640x480`.

Selecting a camera switches the Sensor (AEChip) to [`OpenCvFrameCamera`](../src/net/sf/jaer/chip/opencv/OpenCvFrameCamera.java) if needed. A leftover Davis346 is **not** bound to a webcam by accident.

v1 is **local device indices** only (no RTSP/file URL).

## Live capture

[`OpenCvCameraHardwareInterface`](../src/net/sf/jaer/hardwareinterface/opencv/OpenCvCameraHardwareInterface.java) runs a capture thread. Each `VideoCapture.read` becomes one RGB `FramePacket` (Y flipped: OpenCV top-left → jAER bottom-left). Timestamps are microseconds since the last `resetTimestamps()` (`System.nanoTime()`). ViewLoop uses `acquireAvailablePacketBundle()`.

## Recording and sync

- RGB frames are written as DV `OPENCV_8U_C3` (BGR in the file).
- AEDZ cannot store frames; the first record on this chip offers AEDAT-4 (same as Davis).
- Muxed AEDAT-4 with an event camera: **File → Synchronize**, then record. `JAERViewer` zeros timestamps on all recording viewers at muxed start so USB counters and the webcam clock share t=0 for that file. Alignment is **software** (about one frame period), not a hardware trigger.

Playback of muxed files assigns EVTS cameras as before and FRME-only cameras (or OpenCV EVTS+FRME with the same `source`) to `OpenCvFrameCamera`.

Playback slicing:

- **Frame/IMU-only** (no EVTS): CountDuration (or Real time). ConstantCount / AreaEventCount are disabled — there are no events to count. Each ViewLoop iteration advances a **time playhead** by the timeslice (same as event cameras), holding the last frame at that time. A 20 ms slice at ~50 FPS is about real time; a larger slice plays the file faster.
- **Muxed with an event camera** (File → Synchronize): event cameras own the slice. Each frame viewer shows the **last frame at or before** that event time, not its own FlexTime index. ViewLoop still renders when the polarity packet is empty.

## Headless checks

## Headless checks

After `ant compile`:

```text
java -cp build/classes;jars/*;lib/* net.sf.jaer.hardwareinterface.opencv.OpenCvCameraFactoryDemo
java -cp build/classes;jars/*;lib/* net.sf.jaer.eventio.aedat4.Aedat4RgbFrameRoundtripDemo
java -cp build/classes;jars/*;lib/* net.sf.jaer.eventio.aedat4.Aedat4PlaybackAssignmentDemo
```

On Linux/macOS use `:` in the classpath.
