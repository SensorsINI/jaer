# jAER as a live camera server, and Python dataloaders

This is the user guide for **File → Remote** and for reading jAER recordings in Python.
In-app **Show Help** on each Remote dialog has filter-specific knobs and protocol details.

jAER does not load TensorFlow or PyTorch. A Python (or OpenCV / ROS) process consumes what jAER publishes.

---

## Pick a path

| You want | Open | Consumer |
|----------|------|----------|
| A **webcam-like** stream for OpenCV, YOLO, Zoom, Meet | [OpenCV live camera](#opencv-live-camera) | `cv2.VideoCapture` or a Linux v4l2 device |
| **Lowest-latency events or frames** into a local DNN | [DNN shared memory](#dnn-shared-memory) | Python `mmap` + localhost TCP |
| Frames on a **robot** or in **Foxglove** | [ROS2 and Foxglove](#ros2-and-foxglove) | `ros2 topic` or Foxglove Studio |
| **Train / evaluate offline** on recordings | [Python dataloaders](#python-dataloaders) | `aedat`, `h5py`, or CSV |

Same **File → Remote** sinks work on a **live USB camera** and on **File → Open** playback. Playback is a repeatable “camera” for debugging the consumer.

---

## Common steps (any Remote sink)

1. Select the **AEChip** that matches the camera (or the recording).
2. Open a live device, **or** File → Open a sample / your `.aedat4`.
3. **File → Remote** → the sink you want. That only **opens** the control window.
4. Press the **green enable** toggle at the top of that window. The chip-view overlay must show the sink as running.
5. Closing the window does **not** stop publishing. Use the same toggle (red) to stop.

Optional: put a denoiser **above** the Remote filter in the Filter panel so the consumer sees cleaned events.

---

## OpenCV live camera

**File → Remote → OpenCV camera output…**

jAER assembles DVS / Davis frames and serves **HTTP Motion JPEG**. Stock OpenCV treats it as a camera.

```python
import cv2

cap = cv2.VideoCapture("http://127.0.0.1:8090/video.mjpg", cv2.CAP_FFMPEG)
ok, frame = cap.read()
```

```cpp
cv::VideoCapture cap("http://127.0.0.1:8090/video.mjpg", cv::CAP_FFMPEG);
```

Browser preview: <http://127.0.0.1:8090/> · still: `/snapshot.jpg`.

**frameSource:** Auto / RenderedPixmap follows the chip view (including Davis frames). ApsFrames is intensity only. DvsEventCount is an event histogram (mid-gray = zero).

**Linux webcam (Cheese, Zoom, Google Meet):** HTTP MJPEG is not a webcam. Check **publishV4l2**, set **outputSize** to **VGA (640×480)**, leave **v4l2Mjpeg** on. Overlay must show `/dev/video10 open MJPEG`. Load the loopback device once with sudo (`modprobe` details and Cheese workaround: in-app Help, or `scripts/cheese-jaer.sh`).

---

## DNN shared memory

**File → Remote → DNN shared memory output…**

jAER writes a **memory-mapped file** and a localhost **TCP JSON-lines** control channel (`HELLO` on connect, `FRAME_READY` per buffer). Two payload modes share that path:

| `outputMode` | Payload | Typical consumer | TCP |
|--------------|---------|------------------|-----|
| **EventCountFrames** (These frames can be exposed using **ConstantDuration**, **ConstantCount**, or **AreaEventCount** accumulation in jAER))| 64×64 uint8 event-count image | [dextra-roshambo-python](https://github.com/SensorsINI/dextra-roshambo-python) | `127.0.0.1:14100` |
| **EventWindows** | packed `(t, x, y, p)` windows of a constant # of events | [rpg_e2vid](https://github.com/SensorsINI/rpg_e2vid) / FireNet | `127.0.0.1:14101` |

Default mmap paths (also shown as `mmapPath` in the dialog):

- Linux/macOS: `/tmp/jaer/jaer_dvs_frames.mmap` or `jaer_dvs_events.mmap`
- Windows: `%TEMP%\jaer\jaer_dvs_frames.mmap` or `jaer_dvs_events.mmap`

### EventCountFrames (Roshambo hello world)

1. `outputMode` = **EventCountFrames**. Defaults: 64×64, `dvsGrayScale=16`, `rectifyPolarities=true`.
2. Play a live camera, or [Davis346 Roshambo throws](https://drive.google.com/file/d/1hEI4HMODwAu6Pm9P4oDecePbfv--Lwbg/view?usp=drive_link) with chip **Davis346blue**.
3. Enable the filter. Overlay shows the mmap path.
4. In `dextra-roshambo-python`:

```bash
python consumer.py --jaer-mmap /tmp/jaer/jaer_dvs_frames.mmap --serial_port None --windowed
```

(`--jaer-tcp 127.0.0.1:14100` is implied. `--jaer-tcp None` polls mmap sequence numbers only.)

If the CNN image is upside-down, toggle **flipY** (jAER default is lower-left origin; OpenCV / training sets are often top-left).

### EventWindows (FireNet / E2VID)

1. `outputMode` = **EventWindows**. Leave `eventsPerWindow=0` to use `width × height × numEventsPerPixel` (0.35, same as E2VID).
2. Leave **flipY** on (Python / OpenCV / FireNet use upper-left `y`).
3. In `rpg_e2vid`:

```bash
uv run python live_reconstruction.py -c pretrained/E2VID_lightweight.pth.tar --auto_hdr --display --show_events
```

Byte layout of each mmap slot (double-buffered; `seq` is the publication fence): in-app **Show Help**.

---

## ROS2 and Foxglove

**File → Remote → ROS2 / Foxglove frame output…**

Assembled DVS frames (not the OpenGL pixmap) go to **Foxglove Studio** and/or **ROS2**. No ROS2 install is required on the jAER machine (IHMC jros2 / Fast-DDS).

**Foxglove**

1. Enable the filter; leave **publishFoxglove** checked.
2. Foxglove: Open connection → Foxglove WebSocket → `ws://localhost:8765`.
3. Image layout → topic `/jaer/event_count` (or time-surface / voxel topics).

**ROS2** (another machine or the same one with a ROS2 distro): enable **publishRos2**, then `ros2 topic hz /jaer/event_count`. Domain ID matches `ROS_DOMAIN_ID`.

Frame types: **EventCountHistogram**, **TimestampImages**, **VoxelGrid**. Details: in-app Help.

---

## Python dataloaders

jAER records **AEDAT-4** (`.aedat4`) by default — the same DV container [iniVation documents](https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-4.0.html). Format table and Save As options: [`README-file-formats.md`](README-file-formats.md).

**Y origin:** jAER’s chip view has `y=0` at the **bottom**. Files written by DV / iniVation software use OpenCV `y=0` at the **top**. Python image code almost always wants top-left; flip with `y = height - 1 - y` if the picture is upside-down. **File → Save As → DSEC HDF5** stores `y=0` at the top.

### Easiest numpy: File → Save As

Open the recording (not a live camera). **File → Save As…** (`Ctrl+Shift+S`).

**DSEC HDF5** (image coordinates, `p` 0=off / 1=on):

```python
import h5py

with h5py.File("clip.h5", "r") as f:
    t = f["events/t"][:]   # microseconds
    x = f["events/x"][:]
    y = f["events/y"][:]   # y=0 at top
    p = f["events/p"][:]   # 0=off, 1=on
```

**CSV / text** (RPG-style default is space-separated `t_sec x y p`, polarity 0/1):

```python
import numpy as np

t, x, y, p = np.loadtxt("clip.txt", unpack=True)
```

Optional IN/OUT markers clip the export. Optional **Apply EventFilters** writes the filtered stream.

### Read `.aedat4` directly: `pip install aedat`

[`aedat`](https://pypi.org/project/aedat/) 2.2.0 (Neuromorphic Systems) is a small AEDAT-4 decoder. Events are a structured numpy array. Field names are `t`, `x`, `y`, `p`; `on` is an alias for `p` (boolean ON=True).

Tested on `sampleData/`:

* Works: `Davis346redColor MISTLab RoboCup soccer ball approaching from air.aedat4` (DV-native; 885 529 events, 32 RGB frames, IMU), `PropheseeIMX636HD 2026 short heavily filtered.aedat4` (4.9 M events).
* **Fails** on most other jAER sample files with `RuntimeError: invalid digit found in string`. Those files put a `jAERConfigSnapshot` node *inside* `outInfo`; `aedat` treats every `outInfo` child name as a numeric stream id. Current jAER writes the snapshot as a *sibling* of `outInfo`. Re-export with **File → Save As… → AEDAT-4**, or use HDF5/CSV above. Existing sample zip files are unchanged until they are re-exported.

```python
import aedat
import numpy as np

decoder = aedat.Decoder("recording.aedat4")
print(decoder.id_to_stream())  # stream id → events / frame / imus

chunks = []
for packet in decoder:
    if "events" in packet:
        chunks.append(packet["events"])
    elif "frame" in packet:
        pixels = packet["frame"]["pixels"]  # uint8 (H, W) or (H, W, 3) RGB
events = np.concatenate(chunks) if chunks else np.array([])
# events["t"], events["x"], events["y"], events["p"]  # p also as events["on"]
```

Fixed-count windows for a PyTorch `IterableDataset`:

```python
import aedat
import numpy as np
from torch.utils.data import IterableDataset

class Aedat4Windows(IterableDataset):
    def __init__(self, path, events_per_window=8192):
        self.path = path
        self.n = events_per_window

    def __iter__(self):
        decoder = aedat.Decoder(self.path)
        leftover = None
        for packet in decoder:
            if "events" not in packet:
                continue
            ev = packet["events"]
            if leftover is not None:
                ev = np.concatenate([leftover, ev])
                leftover = None
            i = 0
            while i + self.n <= len(ev):
                yield ev[i : i + self.n]
                i += self.n
            leftover = ev[i:] if i < len(ev) else None

# for window in Aedat4Windows("recording.aedat4"):
#     t, x, y, p = window["t"], window["x"], window["y"], window["p"]
```

Alternatives: [dv-processing](https://dv-processing.inivation.com/) `dv.io.MonoCameraRecording` (iniVation; events, frames, IMU); [Tonic](https://tonic.readthedocs.io/) `tonic.io.read_aedat4` (loads the whole event stream).

If jAER offers to create a sibling `-rerecord.aedat4` on open (dependent-block LZ4), accept it for faster playback and for Python readers that struggle with that compression.

---

## Related

| Doc | What it is |
|-----|------------|
| This file | Live Remote sinks + Python reading |
| In-app **Show Help** on each File → Remote dialog | Knobs, mmap bytes, v4l2, Foxglove topics |
| [`README-file-formats.md`](README-file-formats.md) | Play / record / Save As formats |
| [`README.md`](../README.md) | Install, cameras, sample data |
| [jAER User Guide](https://docs.google.com/document/d/1fb7VA8tdoxuYqZfrPfT46_wiT1isQZwTHgX8O22dJ0Q/edit?usp=sharing) | Desktop UI |
| [3.4.0 release notes](../release-notes/jaer-3.4.0-release-notes.md) | Screenshots of the Remote dialogs |
