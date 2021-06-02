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
package net.sf.jaer.stereopsis;

import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Duplicates the hardware interface to a single bias generator to control a stereo pair of chips each with it's own hardware interface.

 * @author tobi
 */
public class StereoBiasgenHardwareInterface extends StereoPairHardwareInterface implements BiasgenHardwareInterface {

    protected BiasgenHardwareInterface biasgenLeft = null,  biasgenRight = null;

    /** Creates a new instance of StereoBiassgenHardwareInterface.
     */
    public StereoBiasgenHardwareInterface(AEMonitorInterface aemonLeft, AEMonitorInterface aemonRight) {
        super(aemonLeft, aemonRight);
        if (aemonLeft instanceof BiasgenHardwareInterface) {
            biasgenLeft = (BiasgenHardwareInterface) aemonLeft;
        }
        if (aemonRight instanceof BiasgenHardwareInterface) {
            biasgenRight = (BiasgenHardwareInterface) aemonRight;
        }
    }

    /** Overrides the super method to set powerdown for both chips.
     * @param powerDown true to power OFF the biasgen, false to power on
     */
    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        if (biasgenLeft == null || biasgenRight == null) {
            log.warning("null biasgen interfaces, cannot send pot values");
            return;
        }
        biasgenLeft.setPowerDown(powerDown);
        biasgenRight.setPowerDown(powerDown);
    }

    /** sends the ipot values. */
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if (biasgenLeft == null || biasgenRight == null) {
            log.warning("null biasgen interfaces, cannot send pot values");
            return;
        }
        biasgenLeft.sendConfiguration(biasgen);
        biasgenRight.sendConfiguration(biasgen);
    }

    /** flashes the biases in non-volatile storage so they will be reloaded on reset or powerup */
    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if (biasgenLeft == null || biasgenRight == null) {
            log.warning("null biasgen interfaces, cannot send pot values");
            return;
        }
        biasgenLeft.flashConfiguration(biasgen);
        biasgenRight.flashConfiguration(biasgen);
    }

    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        return biasgenLeft.formatConfigurationBytes(biasgen);
    }
}
