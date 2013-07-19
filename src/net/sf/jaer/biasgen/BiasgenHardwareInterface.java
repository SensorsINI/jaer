/*
 * IPotControl.java
 *
 * Created on September 20, 2005, 9:21 PM
 */

package net.sf.jaer.biasgen;

import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Interfaces via USB to the on- or off-chip biases and other configuration information on a chip or system.
 * @author tobi
 */
public interface BiasgenHardwareInterface extends HardwareInterface {
    
    /** sends the powerdown vendor request to power down the chip.
     * <p>
     *  Typically this method toggles the powerDown pin correctly to ensure on-chip biasgen is powered up. Chip may have been plugged in without being
     * powered up. If powerdown is true, simply sets powerdown high. If powerdown is false, powerdown is toggled high and then low, to make
     * sure a nagative transistion occurs. This transistion is necessary to ensure the startup circuit starts up the masterbias again.
     * @param powerDown true to power OFF the biasgen, false to power on
     */
    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException ;
    
    /** Sends the configuration values to the hardware. The configuration can include bias values (both for on- and off-chip sources), configuration bits, etc.
     @param biasgen the object that holds the configuration.
     */
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException ;
    
    /** flashes the configuration in non-volatile storage so they will be reloaded on reset or powerup.
     @param biasgen holds the configuration values
     */
    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException ;
    
    /** Formats and returns the bytes that should be sent to set a particular configuration.
     * 
     * @param biasgen the source of the configuration.
     * @return the array to be sent.
     */
    public byte[] formatConfigurationBytes(Biasgen biasgen);
        
}
