# jAER
Java tools for Address-Event Representation (AER) neuromorphic processing. 

**Permanent link:** http://jaerproject.org

**Welcome to the jAER Open Source Project
Real time sensory-motor processing for event-based sensors and systems**

Founded in 2007 to support event sensors and robot demonstrators developed by the [Sensors Group, Inst. of Neuroinformatics, UZH-ETH Zurich](https://sensors.ini.ch). 

#### What jAER feels like to use

![jAER demo](/images/using_jaer_2021-01-22_08-16-47_1.gif)

## Installation

You can find the latest releases at <https://github.com/SensorsINI/jaer/releases>. 

Starting with jAER 2.0, (unsigned) binary installers are now available thanks to the 
multi-platform installer builder [install4j](https://www.ej-technologies.com/products/install4j/overview.html). 

Go to [install4j jAER installers on dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0) to download installers. 
**Windows:** Click *More info*, *Run anyway* and *Install anyway* for unsigned app.
**MacOS:** See [opening unsigned dmg on MacOS](https://support.apple.com/guide/mac-help/open-a-mac-app-from-an-unidentified-developer-mh40616/mac). Right click, open with Archive Manager, and run the installer. Recommend to install to a user folder.
**Linux:** Run the installer with `sh <installer>.sh`. Then you can *jaer* from the installation directory or gnome menu.
See video [installing and updating jaer on YouTube](https://youtu.be/qQVt8_gwYVY).

* install4j installers install a bundled version of the [latest Java from Eclipse Adoptium](https://adoptium.net/) (see [Guide fo Java versions and features](https://www.marcobehler.com/guides/a-guide-to-java-versions-and-features)).
* Release install4j installers do NOT install git working copy, but using the new self-update feature introduced in jAER-1.8.1, 
you can [initialize the release to a git working copy and pull+build within jAER](https://youtu.be/qQVt8_gwYVY). 
* You will get the best experience running from lastest bug fixes. 


## Quick start sample data

* Download [DVS128 data files from the DVS09 dataset](https://docs.google.com/document/d/16b4H78f4vG_QvYDK2Tq0sNBA-y7UFnRbNnsGbD1jJOg/edit?usp=sharing) and
drop them onto the jAER window to play them with the *DVS128* *AEChip*.
* Download [DAVIS346 sample data files from the DAVIS24 dataset](https://sites.google.com/view/davis24-davis-sample-data/home) and
drop them onto the jAER window to play them with the *Davis346blue* *AEChip*.

## Device hardware support
Systems built with Sensors Group chips:
* inilabs DVS128 DVS event camera and inivation DAVIS240, and DAVIS346 HVS cameras
* inilabs DAS (CochleaAMS) cochleas

Event cameras from others:
* iniVation Samsung DVExplorer

## Citation
T. Delbruck, “Frame-free dynamic digital vision,” 
in International Symposium on Secure-Life Electronics, University of Tokyo, 
Mar. 2008, pp. 21–26. 
doi: 10.5167/uzh-17620. Available: http://dx.doi.org/10.5167/uzh-17620

### jAER applications
jAER originally targetted characterization of Sensors Group [event cameras and silicon cochleas](https://sensors.ini.ch/research/event-sensors), 
but has also been used to build many robots: 
[robogoalie](https://youtu.be/IC5x7ftJ96w?si=ajsJWWYJW-tSJ2MI) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/tobi/goalie/Goalie.java)), 
[audio localization by spike ITD](https://www.youtube.com/watch?v=-Klbmm4vgew) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/cochsoundloc/ITDFilter.java)), 
[speaker identification from spiking cochlea](https://www.youtube.com/watch?v=KFPi65WV-S8) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/speakerid/CochleaSVMTwoEars.java)), 
[laser goalie](https://www.youtube.com/watch?v=5c5W18nuPQk) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/tobi/goalie/LaserGoalie.java)), 
[pencil balancer](https://www.youtube.com/watch?v=yCOnDc5r7p8) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/pencilbalancer/PencilBalancer.java)), 
[bill (money) catcher](https://www.youtube.com/watch?v=XtOS7jZzMaU) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/tobi/billcatcher/BillCatcher.java)), 
[slot car racer](https://www.youtube.com/watch?v=CnGPGiZuFRI) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/virtualslotcar/SlotCarRacer.java)), 
[Dextra roshambo (rock-scissors-poaper)](https://www.youtube.com/watch?v=95GsOQbwNLU) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/npp/RoShamBoCNN.java)), 
[incremental learning of new roshambo hand symbols](https://www.youtube.com/watch?v=uVruhxYu5gc) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/npp/RoShamBoIncremental.java)).
jAER was also used to develop many event camera algorithms: 
[Feature extraction](https://www.youtube.com/watch?v=IEsMkIpCE1o) ([code](https://github.com/SensorsINI/jaer/blob/master/src/net/sf/jaer/eventprocessing/label/SimpleOrientationFilter.java)), 
[tracking](https://www.youtube.com/watch?v=5I6haFXVuD8) ([code](https://github.com/SensorsINI/jaer/blob/master/src/net/sf/jaer/eventprocessing/tracking/RectangularClusterTracker.java)), 
[optical flow methods](https://www.youtube.com/watch?v=Ji1MzE4QbM4) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/rbodo/opticalflow/AbstractMotionFlowIMU.java)),
[EDFLOW hardware optical flow](https://www.youtube.com/watch?v=8LedyiHMe_A) ([code](https://github.com/SensorsINI/jaer/blob/master/src/ch/unizh/ini/jaer/projects/minliu/PatchMatchFlow.java)), and 
[efficient and accurate event denoising](https://sites.google.com/view/dnd21/home?authuser=0) ([code](https://github.com/SensorsINI/jaer/blob/master/src/net/sf/jaer/eventprocessing/filter/NoiseTesterFilter.java)).

## Developing with jAER

To develop with jAER, see the [jAER User Guide gdoc](https://docs.google.com/document/d/1fb7VA8tdoxuYqZfrPfT46_wiT1isQZwTHgX8O22dJ0Q/edit?usp=sharing). 

## Support

Please use our GitHub bug tracker to report issues and bugs, or our Google Groups mailing list forum to ask questions.

* **USER GUIDE:** [jAER User Guide gdoc](https://docs.google.com/document/d/1fb7VA8tdoxuYqZfrPfT46_wiT1isQZwTHgX8O22dJ0Q/edit?usp=sharing)
* **VIDEO TUTORIALS:** https://www.youtube.com/playlist?list=PLVtZ8f-q0U5hD9KOM4OZ1lixhwupj9uOm
* **BUG TRACKER:** https://github.com/SensorsINI/jaer/issues/
* **USER FORUM:** https://groups.google.com/d/forum/jaer-users/

See also
* **DAVIS-USERS user forum:** https://groups.google.com/forum/#!forum/davis-users
* **inivation support pages:** https://inivation.com/support/

![DVS128 cameras](/images/dvs128cameras.jpg)

![Hotel bar scene with DAVIS140C](/images/HotelBarDavis.png)

