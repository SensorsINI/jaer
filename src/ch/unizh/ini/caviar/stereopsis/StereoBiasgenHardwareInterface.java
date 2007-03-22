/*
 * StereoBiassgenHardwareInterface.java
 *
 * Created on March 19, 2006, 9:00 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *CopyaemonRight March 19, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.stereopsis;

import ch.unizh.ini.caviar.aemonitor.AEMonitorInterface;
import ch.unizh.ini.caviar.biasgen.Biasgen;
import ch.unizh.ini.caviar.biasgen.BiasgenHardwareInterface;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;

/**
 * Duplicates the hardware interface to a single bias generator to control a stereo pair of chips each with it's own hardware interface.
 
 * @author tobi
 */
public class StereoBiasgenHardwareInterface extends StereoHardwareInterface implements BiasgenHardwareInterface {
    
    protected BiasgenHardwareInterface biasgenLeft=null, biasgenRight=null;
    
    /** Creates a new instance of StereoBiassgenHardwareInterface */
    public StereoBiasgenHardwareInterface(AEMonitorInterface aemonLeft, AEMonitorInterface aemonRight) {
        super(aemonLeft,aemonRight);
        if(aemonLeft instanceof BiasgenHardwareInterface) biasgenLeft=(BiasgenHardwareInterface)aemonLeft;
        if(aemonRight instanceof BiasgenHardwareInterface) biasgenRight=(BiasgenHardwareInterface)aemonRight;
    }

        /** Overrides the super method to set powerdown for both chips.
         * @param powerDown true to power OFF the biasgen, false to power on
         */
        public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
            biasgenLeft.setPowerDown(powerDown);
            biasgenRight.setPowerDown(powerDown);
        }
        
        /** sends the ipot values. */
        public void sendPotValues(Biasgen biasgen) throws HardwareInterfaceException {
            biasgenLeft.sendPotValues(biasgen);
            biasgenRight.sendPotValues(biasgen);
        }
        
        /** flashes the biases in non-volatile storage so they will be reloaded on reset or powerup */
        public void flashPotValues(Biasgen biasgen) throws HardwareInterfaceException {
            biasgenLeft.flashPotValues(biasgen);
            biasgenRight.flashPotValues(biasgen);
        }
    
}
