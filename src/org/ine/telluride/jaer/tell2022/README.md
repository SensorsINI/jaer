# The Gone Fishing Robot
Source code for the robot described in [The Gone Fishing Robot](https://docs.google.com/document/d/166z0sSwXT1iVAvEv8F_OlqergrwELB6Hduzkro6m2eI/edit).


[![Gone Fishing video](https://img.youtube.com/vi/lqrAT5GC2QI/0.jpg)](https://www.youtube.com/watch?v=lqrAT5GC2QI)

The [Gone Fishing](https://drive.google.com/drive/folders/1LObwQ2_Gv-K6hJYtwZ_4W9-Jv4nRmBH1) folder has raw data from the pond and everything else that is not source code.

To run the robot in jAER, add the GoingFishing EventFilter to the list of filters and then set up all the required enclosed filters. You need to mark the pond center and the fishing rod tip location. You also need to record a fishing rod dip motion over the fishing hole.

The [Gone Fishing Arduino Sketch project](https://github.com/SensorsINI/jaer/tree/master/src/org/ine/telluride/jaer/tell2022/GoingFishingFirmware) runs the pan tilt servo and reads the ADC for detecting catches. 
