/*
 * CochleaChip.java
 *
 * Created on May 2, 2006, 1:45 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.chip.cochlea;

import ch.unizh.ini.caviar.chip.AEChip;

/**
 * Superclass for cochlea chips. These use cochlea rendering classes.
 * @author tobi
 */
abstract public class CochleaChip extends AEChip {
    
    /** Creates a new instance of CochleaChip */
    public CochleaChip() {
        getCanvas().addDisplayMethod(new CochleaGramDisplayMethod(getCanvas()));
        getCanvas().addDisplayMethod(new ShammaMapDisplayMethod(getCanvas()));
        getCanvas().addDisplayMethod(new RollingCochleaGramDisplayMethod(getCanvas()));
        addDefaultEventFilter(CochleaCrossCorrelator.class);
    }
}
