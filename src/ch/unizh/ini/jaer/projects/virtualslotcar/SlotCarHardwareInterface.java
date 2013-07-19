/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.util.logging.Logger;

import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Encapsulates the physical hardware interface to the slot car. To use it create a SlotCarHardwareInterface and call setThrottle(float value).
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class SlotCarHardwareInterface implements HardwareInterface, ThrottleBrakeInterface {

    private SiLabsC8051F320_USBIO_SlotCarController hw;
    static final Logger log = Logger.getLogger("SlotCarHardwareInterface");
    private ThrottleBrake throttle = new ThrottleBrake();
    private boolean brakeEnabled = false;
    private static final int THROTTLE_CHANNEL = 0;
    private final int WARNING_PRINT_INTERVAL = 10000; // only prints missing servo warning once this many tries
    private int warningPrintCounter = 0;

    public SlotCarHardwareInterface() {
        hw = new SiLabsC8051F320_USBIO_SlotCarController();  // creates the object/ registers plugnplay notificaiton to log.info plugins/removals, and starts command quueu
    }

    /**
     * @return the last speed that was copyFrom.
     */
    @Override
    public ThrottleBrake getThrottle() {
        return throttle;
    }

    /**  Set the speed and returns success.
     * @param throttle the speed to copyFrom.
     */
    @Override
    public void setThrottle(ThrottleBrake throttle) {
        this.throttle = throttle;
        if (!hw.isOpen()) {
            try {
                hw.open();
                hw.setFullDutyCycleMode(true); // sets the servo outputs to do 0-100% duty cycle rather than usual 1-2ms pulses
                hw.setPCAClockSource(SiLabsC8051F320_USBIO_SlotCarController.PCA_ClockSource.Timer0Overflow);
                hw.setPWMFreqHz(1000);
                // we don't need open drain with garrick's 3.3V PNP inverter before the MOSFET
                //hw.setPortDOutRegisters((byte)0x00,(byte)0x00); // sets the servo output port to open drain
            } catch (HardwareInterfaceException ex) {
                if (warningPrintCounter-- == 0) {
                    log.warning(ex.toString());
                    warningPrintCounter = WARNING_PRINT_INTERVAL;
                }
            }
        }
        if (hw.isOpen()) {
            if (throttle.brake) {
                hw.setBrake(THROTTLE_CHANNEL); // TODO handle different brake channels
            } else {
                // the PWM output must be low to turn on MOSFET so if speed=0 then write 255
                // compute the sqrt of speed to account for the MOSFET nonlinearity
                float t = (float) Math.pow(throttle.throttle,1);
                hw.setPWMValue(THROTTLE_CHANNEL, (int) ((1f - t) * 255));
            }
        }
    }

    public String getTypeName() {
        return hw.getTypeName();
    }

    public void close() {
//        throttle.throttle=0;
//        throttle.brake=false;
//        setThrottle(throttle);
        hw.close();
    }

    public void open() throws HardwareInterfaceException {
        hw.open();
    }

    public boolean isOpen() {
        return hw.isOpen();
    }
}
