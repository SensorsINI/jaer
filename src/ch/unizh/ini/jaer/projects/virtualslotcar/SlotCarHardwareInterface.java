/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.silabs.SiLabsC8051F320_USBIO_ServoController;
/**
 * Encapsulates the physical hardware interface to the slot car. To use it create a SlotCarHardwareInterface and call setThrottle(float value).
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class SlotCarHardwareInterface implements ThrottleInterface {
    private SiLabsC8051F320_USBIO_ServoController hw;
    static Logger log=Logger.getLogger("SlotCarHardwareInterface");
    private float throttle=0;
    private static final int PORT=0;
    private boolean setupPortSuccessful=false;
    private final int WARNING_PRINT_INTERVAL=200;
    private int warningPrintCounter=0;

    public SlotCarHardwareInterface (){
        hw=new SiLabsC8051F320_USBIO_ServoController();  // creates the object/ registers plugnplay notificaiton to log.info plugins/removals, and starts command quueu
    }

    /**
     * @return the last speed that was set.
     */
    public float getThrottle (){
        return throttle;
    }

    /**  Set the speed and returns success.
     * @param speed the speed to set.
     * @return true if interface was open.
     */
    public boolean setThrottle (float speed){
        this.throttle = speed;
        if(!hw.isOpen()){
            try{
                hw.open();
                hw.setFullDutyCycleMode(true); // sets the servo outputs to do 0-100% duty cycle rather than usual 1-2ms pulses
                hw.setPortDOutRegisters((byte)0x00,(byte)0x00); // sets the servo output port to open drain
            } catch ( HardwareInterfaceException ex ){
                if(warningPrintCounter--==0){
                    log.warning(ex.toString());
                    warningPrintCounter=WARNING_PRINT_INTERVAL;
                }
            }
        }
        if(hw.isOpen()){
            hw.setServoValuePWM(PORT,(int)(speed*65535));
            return true;
        }
        return false;
    }

}
