/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.hardware.pantilt;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;
/**
 *
 * @author tobi
 */
public interface PanTiltInterface {

    /**
     * A method can set this flag to tell other objects that the servo is "owned"
     */
    public void acquire();

    public float getJitterAmplitude();

    public float getJitterFreqHz();

    /**
     * Returns the last value set, even if the servo interface is not functional. The servo could still be moving to this location.
     *
     * @return a float[] array with the 0 component being the pan value, and the 1 component being the tilt
     *
     */
    public float[] getPanTilt();

    /**
     * A method can check this to see if it can use the servo
     */
    public boolean isLockOwned();

    /**
     * Releases the "acquired" flag
     */
    public void release();

    /**
     * Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt during jittering
     *
     * @param jitterAmplitude the amplitude
     */
    public void setJitterAmplitude(float jitterAmplitude);

    /**
     * The frequency of the jitter
     *
     * @param jitterFreqHz in Hz
     */
    public void setJitterFreqHz(float jitterFreqHz);

    /**
     * Simultaneously sets pan and tilt values. The directions here depend on the servo polarities, which could vary.
     * These values apply to HiTec digital servos.
     *
     * @param pan the pan value from 0 to 1 inclusive, 0.5f is the center position. 1 is full right.
     * @param tilt the tilt value from 0 to 1. 1 is full down.
     * @throws ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException
     */
    public void setPanTilt(float pan, float tilt) throws HardwareInterfaceException;

    /**
     * Starts the servo jittering around its set position at a frequency of 50 Hz with an amplitude of 0.02f
     * @see #setJitterAmplitude(float)
     */
    public void startJitter();

    /**
     * Stops the jittering
     */
    public void stopJitter();

}
