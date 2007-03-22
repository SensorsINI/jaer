/*
 * IPotControl.java
 *
 * Created on September 20, 2005, 9:21 PM
 */

package ch.unizh.ini.caviar.biasgen;

import ch.unizh.ini.caviar.hardwareinterface.*;

/**
 * Interfaces via USB to the IPots on a chip.
 * @author tobi
 */
public interface BiasgenHardwareInterface extends HardwareInterface {
    
    /** sends the powerdown vendor request to power down the masterbias.
     *  Toggles the powerDown pin correctly to ensure on-chip biasgen is powered up. Chip may have been plugged in without being
     * powered up. If powerdown is true, simply sets powerdown high. If powerdown is false, powerdown is toggled high and then low, to make
     * sure a nagative transistion occurs. This transistion is necessary to ensure the startup circuit starts up the masterbias again.
     * @param powerDown true to power OFF the biasgen, false to power on
     */
    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException ;
    
    /** sends the ipot values. */
    public void sendPotValues(Biasgen biasgen) throws HardwareInterfaceException ;
    
    /** flashes the biases in non-volatile storage so they will be reloaded on reset or powerup */
    public void flashPotValues(Biasgen biasgen) throws HardwareInterfaceException ;
        
}
