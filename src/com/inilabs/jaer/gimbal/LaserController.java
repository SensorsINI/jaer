package com.inilabs.jaer.gimbal;

/**
 * Encapsulates control of the a laser pointer on/off state controlled via a SiLabsC8051F320_USBIO_ServoController
 * controller board. Port 2 on the board is used in open drain mode to sink the laser supply current to turn
 * it on. The laser ground supply should be connected to multiple port 2 pins (at least 4) to reduce each pin's current
 * to less than 100mA. One green laser pointer tested sinks up to 230mA at 3V supply and seems to run
 * OK using the USB VBUS (5 volts).
 * @author tobi
 */

public class LaserController implements LaserControlInterface {

private boolean laserEnabled = false;

public void LaserController()  {

}

    public void setLaserEnabled(boolean yes) {
    laserEnabled = yes;
};
 
    /** Hack to control laser pointer power through pin 2 opendrain pulldown pins (multiple to share laser
     * current of about 200mA
     * @param yes to turn on laser */
      public  void setLaserOn(boolean yes){
//        if(servo!=null){
//            servo.setPort2(yes? 0:0xff);
//        }
      }

}

