# jAER sample recordings

These files are short event-camera recordings you can play in jAER without a camera plugged in. Use them to try playback, rendering, and filters.

## Get the files

In jAER: **Help > Sample data > Download jAER sample data**, or **Show jAER sample data folder and README** if recordings are already present.

Permanent link to the zip on the latest GitHub release: <https://github.com/SensorsINI/jaer/releases/latest/download/jaer-sample-data.zip>

That downloads about **996 MB**. You choose the folder: default is this `sampleData` directory next to `dist/` and `lib/` (or the installer `sampleData` folder). If that location is not writable (typical for `C:\Program Files\jAER`), the chooser offers `jaerSampleData` in your home directory. The chosen folder is added to the **File > Recent Files and Folders** list.

If this folder is already here with recordings, **Help > Sample data > Show jAER sample data folder and README** opens the folder and the [GitHub README](https://github.com/SensorsINI/jaer/tree/master/sampleData#readme).

You can also use **File > Open** and choose a file here.

## How to play one

1. **File > Open** (or drop a file onto the jAER window).
2. jAER usually selects the matching camera. If the picture looks wrong, set **Sensor** (AEChip) to the camera in the table below.
3. Play / pause with the player controls. Scrub the timeline; F1 shows you quick help on keyboard shortcuts.

These samples are **AEDAT-4** (`.aedat4`). To replay one as a live OpenCV / DNN / ROS camera, or to load it in Python, see [`docs/README-DNN-OpenCV-ROS.md`](../docs/README-DNN-OpenCV-ROS.md).

## What is in each file

Previews are 5-second loops of the rendered recording (240 px wide).

| Preview | File | Size | Camera to select | What you will see |
|---------|------|------|------------------|-------------------|
| <img src="previews/DAVIS240C%202016%20%20Tobi%20juggling.webp" width="240" alt="Tobi juggling"> | `DAVIS240C 2016  Tobi juggling.aedat4` | 6.4 MB | DAVIS240C | Tobi Delbruck juggling. DAVIS 240x180: events plus gray frames. Small file, good first try. |
| <img src="previews/DAVIS240C%20CapoCaccia%202016%20Hotel%20dei%20Pini%20bar-export.webp" width="240" alt="Hotel bar"> | `DAVIS240C CapoCaccia 2016 Hotel dei Pini bar-export.aedat4` | 12.5 MB | DAVIS240C | Hotel bar at [CapoCaccia Neuromorphic Workshop](https://capocaccia.cc): people moving, APS frames with a brightness histogram. Medium length. Useful for tracking and trying out Flextime playback modes; also advanced frame-event fusion. |
| <img src="previews/Davis240C%20DVSFLOW16%20Rotating%20Fan.webp" width="240" alt="Rotating fan"> | `Davis240C DVSFLOW16 Rotating Fan.aedat4` | 4.9 MB | DAVIS240C | Optical-flow test: rotating fan (DVSFLOW16). Events, APS frames, IMU. |
| <img src="previews/Davis240C%20DVSFLOW16%20Translating%20Boxes.webp" width="240" alt="Translating boxes"> | `Davis240C DVSFLOW16 Translating Boxes.aedat4` | 2.3 MB | DAVIS240C | Optical-flow test: translating boxes (DVSFLOW16). Events, APS frames, IMU. |
| <img src="previews/Davis346%20DAVIS24%202016%20Telluride%20mountain%20biking.webp" width="240" alt="Telluride mountain biking"> | `Davis346 DAVIS24 2016 Telluride mountain biking.aedat4` | 110.5 MB | Davis346blue | Telluride 2016 mountain biking; Tobi Delbruck following [Alex Zhu](https://alexzzhu.github.io/) through the forest. Try stabilizing and frame event fusion. Enjoy the flow. |
| <img src="previews/Davis346blue%202026%20Steadicam%20test%206mm%20lens.webp" width="240" alt="Steadicam"> | `Davis346blue 2026 Steadicam test 6mm lens.aedat4` | 140.8 MB | Davis346blue | Handheld / test [Steadicam](https://github.com/SensorsINI/jaer/blob/master/src/net/sf/jaer/eventprocessing/filter/Steadicam.java) IMU derotation by setting 6 mm lens on a DAVIS346 (346x260). Larger file. |
| <img src="previews/Davis346blue%20DAVIS24%202019%20fast%20spinning%20dot%20ramp%20up.webp" width="240" alt="Spinning dot"> | `Davis346blue DAVIS24 2019 fast spinning dot ramp up.aedat4` | 32.2 MB | Davis346blue | Dark spinning dot speeding up (DAVIS24 / 2019). Event rate climbs; useful for tracking and playback and frame-event fusion. Used in S.-C. Liu, et al., "[Event-Driven Sensing for Efficient Perception: Vision and Audition Algorithms](https://ieeexplore.ieee.org/document/8887562)," IEEE Signal Process. Mag., 2019. |
| <img src="previews/Davis346redColor%20MISTLab%20RoboCup%20soccer%20ball%20approaching%20from%20air.webp" width="240" alt="Soccer ball"> | `Davis346redColor MISTLab RoboCup soccer ball approaching from air.aedat4` | 14.6 MB | Davis346redColor | Color DAVIS346: a soccer ball coming toward the camera (RoboCup / MISTLab). |
| <img src="previews/DDD20%20rec1501953155%20San%20Marino%20drive%20clipped.webp" width="240" alt="San Marino drive"> | `DDD20 rec1501953155 San Marino drive clipped.aedat4` | 70.6 MB | Davis346blue (or Davis346red) | Clip from the [DDD20](https://sites.google.com/view/davis-driving-dataset-2017/datasets) driving set: road, other cars, IMU. Converted to AEDAT-4. |
| <img src="previews/DVS128%20DVS09%202006%20mouse%20behavior%20over%203%20days.webp" width="240" alt="Mouse behavior"> | `DVS128 DVS09 2006 mouse behavior over 3 days.aedat4` | 86.3 MB | DVS128 | Lab mouse over 3.5 days. Sparse events; useful for long recordings and playback with Flextime modes, behavior analysis. Try using the activity histogram on the playback slider. Also used in [SC Liu paper](https://ieeexplore.ieee.org/document/8887562) above. |
| <img src="previews/DVS128%20DVS09%202006%20Patrick%20Lichtstieiner%20juggling.webp" width="240" alt="Patrick juggling"> | `DVS128 DVS09 2006 Patrick Lichtstieiner juggling.aedat4` | 13.9 MB | DVS128 | Early DVS128 juggling clip (Patrick Lichtsteiner). Used in seminal DVS128 paper P. Lichtsteiner, et al., "[A 128x128 120 dB 15 us latency asynchronous temporal contrast vision sensor](http://dx.doi.org/10.1109/jssc.2007.914337)," IEEE JSSC, 2008. |
| <img src="previews/DVS128%202007%20robo%20goalie%20balls%20and%20arm.webp" width="240" alt="Goalie balls and arm"> | `DVS128 2007 robo goalie balls and arm.aedat4` | 20.5 MB | DVS128 | Early DVS128 RoboGoalie ([YT video](https://www.youtube.com/watch?v=IC5x7ftJ96w)) data. Balls coming at the goal and servo arm blocking them.  See [goalie paper](http://dx.doi.org/10.3389/fnins.2013.00223). Try multiobject/multizone tracking and velocity prediction. |
| <img src="previews/DVS640%20EssacSim%20Warehouse%20Quad%20walk%20env0_ep0.webp" width="240" alt="Warehouse quad"> | `DVS640 EssacSim Warehouse Quad walk env0_ep0.aedat4` | 98.2 MB | DVS640 | [EssacSim](https://github.com/spikelab-jhu/isaac-sim-event-camera-plugin) (somewhat optimistic) simulated events from a quadruped walking through a warehouse. See [paper](https://arxiv.org/abs/2608.08522). |
| <img src="previews/NRV%20DELTA01%202026%20humming%20birds%20squabbling.webp" width="240" alt="Hummingbirds"> | `NRV DELTA01 2026 humming birds squabbling.aedat4` | 10.4 MB | DELTA01 / NRV S5KRC1S | NRV 960x720 DVS: hummingbirds feeding and squabbling around their feeder. Try Flextime, slow motion, and tracking. |
| <img src="previews/PropheseeIMX636HD%20Metavision%20driving_sample%20street.webp" width="240" alt="IMX636 driving"> | `PropheseeIMX636HD Metavision driving_sample street.aedat4` | 370.8 MB | PropheseeIMX636HD | Prophesee / Metavision street driving sample. Largest file (~371 MB); HD traffic. |

More public datasets (not in this zip) are linked under **Help > Sample data**.

## Notes

PyPI [`aedat`](https://pypi.org/project/aedat/) **2.2.0** opens these files (events and IMU) but **cannot decode DAVIS APS frames**. The recordings use the valid DV tag `OPENCV_16U_C1` (10-bit ADC in 16-bit samples). That library only maps 8-bit Gray/BGR/BGRA and raises `RuntimeError: unknown frame format` on the first APS packet, which also stops further events. Color DV frames (8-bit RGB, e.g. the RoboCup file) work. This note will be updated when `aedat` reads 16-bit gray. Until then, use jAER, **File → Save As** HDF5/CSV, or [dv-processing](https://dv-processing.inivation.com/). Python dataloaders and File → Remote (OpenCV, DNN mmap, ROS2): [`docs/README-DNN-OpenCV-ROS.md`](../docs/README-DNN-OpenCV-ROS.md).
