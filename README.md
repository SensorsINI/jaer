# jAER
Java tools for Address-Event Representation (AER) neuromorphic processing. 

**Permanent link:** http://jaerproject.org

[![Build Status](https://travis-ci.org/SensorsINI/jaer.svg?branch=master)](https://travis-ci.org/SensorsINI/jaer)

**Welcome to the jAER Open Source Project
Real time sensory-motor processing for event-based sensors and systems**

Founded in 2007 to support event sensors and robot demonstrators developed by the [Sensors Group, Inst. of Neuroinformatics, UZH-ETH Zurich](https://sensors.ini.uzh.ch). 
  ![jAER demo](/images/using_jaer_2021-01-22_08-16-47_1.gif)

## Download

You can find the latest releases at <https://github.com/SensorsINI/jaer/releases>. 

Releases do NOT include git information, but using the new self-update feature introduced in jAER-1.8.1, you can initialize the release to a git working copy and pull+build within jAER. 

You will get the best experience running from lastest bug fixes. 

## Building yourself

To build yourself, see the user guide for IDE setup. Or you can use [ant](https://ant.apache.org/manual/install.html). Install [JDK 1.8](https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html), clone the jAER repo with git and compile jAER using ant from command line as below, from root of jAER. The default target of build.xml for ant is to build the jaer.jar archive of comppiled classes:

    ant

On Linux, installing ant should be very easy. On Windows it is still a pain since you will need to download zip, extract it, put it somewhere, and then set some enviroment variables. But once set up, updating can be done from the Help/Update jAER... menu.

NOTE: jAER is not working with java > 1.8 now. Do not bother with java 9,10,11, etc. yet.  You need Oracle JDK1.8. See User Guide for more information.

Converting release into working git folder: Once you convert your release, you can use IDE or ant to rebuild jAER.

    cd jaer-dist
    git init
    git remote add origin https://github.com/SensorsINI/jaer.git
    git fetch --depth=1
    git checkout -t origin/master -f

## Support

Please use our GitHub bug tracker to report issues and bugs, or our Google Groups mailing list forum to ask questions.

* **USER GUIDE:** [jAER User Guide gdoc]( https://docs.google.com/document/d/1fb7VA8tdoxuYqZfrPfT46_wiT1isQZwTHgX8O22dJ0Q/edit?usp=sharing), or embedded as https://inivation.com/support/software/jaer/
* **VIDEO TUTORIALS:** https://www.youtube.com/playlist?list=PLVtZ8f-q0U5hD9KOM4OZ1lixhwupj9uOm
* **BUG TRACKER:** https://github.com/SensorsINI/jaer/issues/
* **USER FORUM:** https://groups.google.com/d/forum/jaer-users/

See also
* **DAVIS-USERS user forum:** https://groups.google.com/forum/#!forum/davis-users
* **inivation support pages:** https://inivation.com/support/

![DVS128 cameras](/images/dvs128cameras.jpg)

![Hotel bar scene with DAVIS140C](/images/HotelBarDavis.png)

