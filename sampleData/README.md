# jAER sample recordings

These files are short event-camera recordings you can play in jAER without a camera plugged in. Use them to try playback, rendering, and filters.

## Get the files

In jAER: **Help > Sample data > Download jAER sample data**, or **Show jAER sample data folder and README** if recordings are already present.

That downloads about **927 MB**. You choose the folder: default is this `sampleData` directory next to `dist/` and `lib/` (or the installer `sampleData` folder). If that location is not writable (typical for `C:\Program Files\jAER`), the chooser offers `jaerSampleData` in your home directory. The chosen folder is added to **File** recent folders.

If this folder is already here with recordings, **Help > Sample data > Show jAER sample data folder and README** opens the folder and the [GitHub README](https://github.com/SensorsINI/jaer/tree/master/sampleData#readme).

You can also use **File > Open** and choose a file here.

## How to play one

1. **File > Open** (or drop a file onto the jAER window).
2. jAER usually selects the matching camera. If the picture looks wrong, set **Sensor** (AEChip) to the camera in the table below.
3. Play / pause with the player controls. Scrub the timeline; F1 shows you quick help on keyboard shortcuts.

These samples are **AEDAT-4** (`.aedat4`).

## What is in each file

| File                                                                           | Camera to select              | What you will see |
|--------------------------------------------------------------------------------|-------------------------------|-------------------|
| `DAVIS240C 2016  Tobi juggling.aedat4`                                         | DAVIS240C                     | Tobi Delbruck juggling. DAVIS 240x180: events plus gray frames. Small file, good first try. |
| `DAVIS240C CapoCaccia 2016 Hotel dei Pini bar-export.aedat4`                    | DAVIS240C                     | Hotel bar at [CapoCaccia Neuromorphic Workshop](https://capocaccia.cc): people moving, APS frames with a brightness histogram. Medium length. Useful for tracking and trying out Flextime playback modes; also advanced frame-event fusion. |
| `Davis346blue 2026 Steadicam test 6mm lens.aedat4`                              | Davis346blue                  | Handheld / test [Steadicam](https://github.com/SensorsINI/jaer/blob/master/src/net/sf/jaer/eventprocessing/filter/Steadicam.java) IMU derotation by setting 6 mm lens on a DAVIS346 (346x260). Larger file. |
| `Davis346blue DAVIS24 2019 fast spinning dot ramp up.aedat4`                    | Davis346blue                  | Dark spinning dot speeding up (DAVIS24 / 2019). Event rate climbs; useful for tracking and playback and frame-event fusion. Used in S.-C. Liu, et al., "[Event-Driven Sensing for Efficient Perception: Vision and Audition Algorithms](https://ieeexplore.ieee.org/document/8887562)," IEEE Signal Process. Mag., 2019. |
| `Davis346redColor MISTLab RoboCup soccer ball approaching from air.aedat4`      | Davis346redColor              | Color DAVIS346: a soccer ball coming toward the camera (RoboCup / MISTLab). |
| `DDD20 rec1501953155 San Marino drive clipped.aedat4`                           | Davis346blue (or Davis346red) | Clip from the [DDD20](https://sites.google.com/view/davis-driving-dataset-2017/datasets) driving set: road, other cars, IMU. Converted to AEDAT-4. |
| `DVS128 DVS09 2006 mouse behavior over 3 days.aedat4`                           | DVS128                        | Lab mouse over 3.5 days. Sparse events; useful for long recordings and playback with Flextime modes, behavior analysis. Try using the activity histogram on the playback slider. Also used in [SC Liu paper](https://ieeexplore.ieee.org/document/8887562) above. |
| `DVS128 DVS09 2006 Patrick Lichtstieiner juggling.aedat4`                       | DVS128                        | Early DVS128 juggling clip (Patrick Lichtsteiner). Used in seminal DVS128 paper P. Lichtsteiner, et al., "[A 128x128 120 dB 15 us latency asynchronous temporal contrast vision sensor](http://dx.doi.org/10.1109/jssc.2007.914337)," IEEE JSSC, 2008. |
| `DVS128 2007 robo goalie. balls and arm.aedat4`                       | DVS128                        | Early DVS128 RoboGoalie ([YT video](https://www.youtube.com/watch?v=IC5x7ftJ96w)) data. Balls coming at the goal and servo arm blocking them.  See [goalie paper](http://dx.doi.org/10.3389/fnins.2013.00223). Try multiobject/multizone tracking and velocity prediction. |
| `DVS640 EssacSim Warehouse Qud walk env0_ep0.aedat4`                            | DVS640                        | [EssacSim](https://github.com/spikelab-jhu/isaac-sim-event-camera-plugin) (somewhat optimistic) simulated events from a quadruped walking through a warehouse. See [paper](https://arxiv.org/abs/2608.08522). |
| `NRV DELTA01 2026 humming birds squabbling.aedat4`                              | DELTA01 / NRV S5KRC1S         | NRV 960x720 DVS: hummingbirds feeding and squabbling around their feeder. Try Flextime, slow motion, and tracking. |
| `PropheseeIMX636HD 2026 short heavily filtered.aedat4`                          | PropheseeIMX636HD             | Short HD (1280x720) clip that was heavily filtered. Lower event rate than the driving file. |
| `PropheseeIMX636HD Metavision driving_sample street.aedat4`                     | PropheseeIMX636HD             | Prophesee / Metavision street driving sample. Largest file (~371 MB); HD traffic. |

More public datasets (not in this zip) are linked under **Help > Sample data**.

## Bundle size

<!-- SAMPLE-DATA-CONTENTS -->

Download **927 MB**, about **927 MB** on disk.

| File | Size |
|------|------|
| `DAVIS240C 2016  Tobi juggling.aedat4` | 6.4 MB |
| `DAVIS240C CapoCaccia 2016 Hotel dei Pini bar-export.aedat4` | 12.5 MB |
| `Davis240C DVSFLOW16 Rotating Fan.aedat4` | 8.7 MB |
| `Davis240C DVSFLOW16 Translating Boxest.aedat4` | 3.6 MB |
| `Davis346blue 2026 Steadicam test 6mm lens.aedat4` | 140.8 MB |
| `Davis346blue DAVIS24 2019 fast spinning dot ramp up.aedat4` | 32.2 MB |
| `Davis346redColor MISTLab RoboCup soccer ball approaching from air.aedat4` | 14.6 MB |
| `DDD20 rec1501953155 San Marino drive clipped.aedat4` | 70.6 MB |
| `DVS128 2007 robo goalie. balls and arm.aedat4` | 20.5 MB |
| `DVS128 DVS09 2006 mouse behavior over 3 days.aedat4` | 86.3 MB |
| `DVS128 DVS09 2006 Patrick Lichtstieiner juggling.aedat4` | 13.9 MB |
| `DVS640 EssacSim Warehouse Qud walk env0_ep0.aedat4` | 98.2 MB |
| `NRV DELTA01 2026 humming birds squabbling.aedat4` | 10.4 MB |
| `PropheseeIMX636HD 2026 short heavily filtered.aedat4` | 37.5 MB |
| `PropheseeIMX636HD Metavision driving_sample street.aedat4` | 370.8 MB |

<!-- /SAMPLE-DATA-CONTENTS -->
