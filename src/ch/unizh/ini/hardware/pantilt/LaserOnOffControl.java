/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.hardware.pantilt;

import ch.unizh.ini.caviar.hardwareinterface.usb.SiLabsC8051F320_USBIO_ServoController;
import java.util.logging.Logger;

/**
 * Encapsulates control of the a laser pointer on/off state controlled via a SiLabsC8051F320_USBIO_ServoController
 * controller board. Port 2 on the board is used in open drain mode to sink the laser supply current to turn
 * it on. The laser ground supply should be connected to multiple port 2 pins (at least 4) to reduce each pin's current
 * to less than 100mA. One green laser pointer tested sinks up to 230mA at 3V supply and seems to run
 * OK using the USB VBUS (5 volts).
 * @author tobi
 */
public class LaserOnOffControl {
    static Logger log=Logger.getLogger("LaserOnOffControl");
    SiLabsC8051F320_USBIO_ServoController controller;
    public LaserOnOffControl(SiLabsC8051F320_USBIO_ServoController controller){
        this.controller=controller;
    }
    
    public void setLaserEnabled(boolean yes){
        if(controller!=null){
           controller.setPort2(yes? 0:0xff);
        }
    }
}
