
package uk.ac.imperial.pseye;

import net.sf.jaer.chip.Chip;
import net.sf.jaer.biasgen.BiasgenFrame;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.biasgen.Biasgen;
import javax.swing.JPanel;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Bias gen for PSEyeModelChip
 * extends Biasgen but requires a lot of hacking to make work
 * @author mlk
 */
public class PSEyeBiasgen extends Biasgen {

    // constructor used to ensure only PSEye chip used
    public PSEyeBiasgen(Chip chip) {
        super(chip);
        // remove master bias as not used - HACK
        setMasterbias(null);
    }
    
    // ensure chip being set is a PSEyeModelChip
    @Override
    public void setChip(Chip chip) {
        if (chip instanceof PSEyeModelChip) 
            super.setChip(chip);
        else
            super.setChip(null);
    }
    
    /* Returns PSEyeModelChip associated with this biasgen.
     */
    @Override
    public PSEyeModelChip getChip() {
        return (PSEyeModelChip) super.getChip();
    }

    /* 
     * Almost verbatim copy from base class due
     * to need to override "new BiagenPanel"
     */
    @Override
    public JPanel buildControlPanel() {
        startBatchEdit();
        JPanel panel = null;
        
        if (getChip() instanceof PSEyeModelChip) {
            panel = new PSEyeBiasgenPanel(this);
        }
        try {
            endBatchEdit();
        } catch (HardwareInterfaceException e) {
            log.warning(e.toString());
        }
        return panel;
    }
    
    /* Loads preferences (preferred values) for the chip
     */
    @Override
    public void loadPreferences() {
        startBatchEdit();
        getChip().loadPreferences();
        try {
            endBatchEdit();
        } catch (HardwareInterfaceException e) {
            log.warning(e.toString());
        }
    }
    
    @Override
    public boolean isOpen() {
        if (getChip().getHardwareInterface() == null) {
            return false;
        }
        return getChip().getHardwareInterface().isOpen();
    }    
    
    @Override
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if (!isBatchEditOccurring()) {
            getChip().sendConfiguration();
        }
    }    
    
    @Override
    public void storePreferences() {
        log.info("storing preferences to preferences tree");
        getChip().storePreferences();
    }    
    
    @Override
    public void suspend() {}

    @Override
    public void resume() {}
    
    @Override
    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {}
}
