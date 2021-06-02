/*
 * ServoInterface.java
 *
 * Created on July 4, 2006, 3:39 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface;
/**
 * A hardware interface that can set a set of servos implements this interface.
 * @author tobi
 */
public interface ServoInterface extends HardwareInterface {
    /** Sets a servo value.
    @param servo the servo to set, 0 based.
    @param value the servo value, 0 to 1 value, value is clipped to 0,1 limits. 0 is most CW, 1 is most CCW when viewed looking
    down onto servo motor shaft, i.e. looking onto servo motor and not from behind it.
    @see #setAllServoValues
     */
    public void setServoValue(int servo, float value) throws HardwareInterfaceException;

    /** sends a servo value to disable the servo
    @param servo the servo number, 0 based
     */
    public void disableServo(int servo) throws HardwareInterfaceException;

    /** @return number of servos supported by this interface */
    public int getNumServos();

    /** disables all servos */
    public void disableAllServos() throws HardwareInterfaceException;

    /** sets all servos to values passed in array. This call sets all servo values in one method call and sends all values in a single interface transaction, reducing
    overhead and making changes synchronous.
    @param values array of values, 0-1 range. Values are clipped to 0-1 limits. 0 is most CW, 1 is most CCW when viewed looking down onto shaft of servo
    from in front of servo.
    @see #getNumServos
     */
    public void setAllServoValues(float[] values) throws HardwareInterfaceException;

    /** Returns last servo values sent.  */
    public float[] getLastServoValues();

    /** Returns last servo value sent (0 before sending a value) */
    public float getLastServoValue(int servo);

    /** sends a command to set the port 2 output (on the side of the original board) to portValue.
     * This port is presently set to open-drain mode on all bits.
     * @param portValue the bits to set
     */
    public void setPort2(int portValue);

    /** Reads the port 2 byte value.
     * This port is presently set to open-drain mode on all bits.
     * @returns portValue the bits of port 2; only the rightmost 8 bits are meaningful.
     */
    public int getPort2();

    /** Sets servo outputs to produce full duty cycle output rather than standard 1-2ms servo output.
     *
     * @param yes true to set full duty cycle mode.
     */
    public void setFullDutyCycleMode(boolean yes);

    /** Returns setting of full duty cycle mode.
     *
     * @return true if full duty cycle mode, false if standard servo mode.
     */
    public boolean isFullDutyCycleMode();

    /**
     * Attempts to set the PWM frequency. Most analog hobby servos accept from 50-100Hz
     * and digital hobby servos can accept up to 250Hz, although this information is usually not specified
     * in the product information.
     * The SiLabsUSB board can drive servos at 180, 90, 60 or 45 Hz. The frequency is based on the
     * overflow time for a 16 bit counter that is clocked by overflow of an automatically reloaded counter/timer that is clocked
     * by the system clock of 12 MHz. With a timer reload of 1 (requiring 2 cycles to overflow), the 16 bit counter is clocked
     * at 6 MHz, leading to a frequency of 91 Hz.
     * <p>
     * The default frequency is 91Hz.
     * @param freq the desired frequency in Hz. The actual value is returned.
     * @return the actual value or 0 if there is an error.
     */
    float setServoPWMFrequencyHz(float freq);
}
