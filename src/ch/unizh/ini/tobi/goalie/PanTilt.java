/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.tobi.goalie;

import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.caviar.hardwareinterface.ServoInterface;
import ch.unizh.ini.caviar.hardwareinterface.usb.ServoInterfaceFactory;
import ch.unizh.ini.caviar.hardwareinterface.usb.SiLabsC8051F320_USBIO_ServoController;
import java.util.logging.Logger;

/**
 * Encapsulates a pan tilt controller based on using ServoController.
 * @author tobi
 */
public class PanTilt {

    private static Logger log = Logger.getLogger("PanTilt");
    SiLabsC8051F320_USBIO_ServoController servo;
    private final int PAN = 1,  TILT = 2; // number of servo output on controller

    public PanTilt() {
        try {
            checkServos();
            setPanTilt(.5f, .5f);
        } catch (HardwareInterfaceException e) {
            log.warning(e.toString());
        }

    }

    private void checkServos() throws HardwareInterfaceException {
        if (servo == null) {
            try {
                servo = (SiLabsC8051F320_USBIO_ServoController) ServoInterfaceFactory.instance().getFirstAvailableInterface();
            } catch (ClassCastException cce) {
                throw new HardwareInterfaceException("Wrong type of interface: " + cce.getMessage());
            }
        }
        if (servo == null) {
            throw new HardwareInterfaceException("no servo controller found");
        }
        if (!servo.isOpen()) {
            servo.open();
        }
    }

    public void setPan(float f) throws HardwareInterfaceException {
        checkServos();
        servo.setServoValue(PAN, f);
    }

    public void setTilt(float f) throws HardwareInterfaceException {
        checkServos();
        servo.setServoValue(TILT, f);
    }

    public void setPanTilt(float pan, float tilt) throws HardwareInterfaceException {
        checkServos();
        float[] lastValues = servo.getLastServoValues();
        lastValues[PAN] = pan;
        lastValues[TILT] = tilt;
        servo.setAllServoValues(lastValues);
    }
}
