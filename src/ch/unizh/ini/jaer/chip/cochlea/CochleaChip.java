/*
 * CochleaChip.java
 *
 * Created on May 2, 2006, 1:45 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.cochlea;

import javax.swing.JComponent;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.DisplayMethod;

/**
 * Superclass for cochlea chips. These use cochlea rendering classes.
 * @author tobi
 */
abstract public class CochleaChip extends AEChip {
   JComponent helpMenuItem=null;
    public static String HELP_URL_COCHLEA = "http://aer-ear.ini.uzh.ch";

    /** Creates a new instance of CochleaChip */
    public CochleaChip() {
        DisplayMethod m;
        getCanvas().addDisplayMethod(m=new CochleaGramDisplayMethod(getCanvas()));
//        getCanvas().addDisplayMethod(new ShammaMapDisplayMethod(getCanvas())); // not used by anyone for several years - tobi
        getCanvas().addDisplayMethod(new RollingCochleaGramDisplayMethod(getCanvas()));
        addDefaultEventFilter(CochleaCrossCorrelator.class);
//        getCanvas().setDisplayMethod(m); // overrides the mechanism of storing a preferred method in Chip2D
    }
    
       @Override
    public void onDeregistration() {
        super.onRegistration();
        if(helpMenuItem==null) return;
        if(getAeViewer()!=null) getAeViewer().removeHelpItem(helpMenuItem);
    }

    @Override
    public void onRegistration() {
        super.onRegistration();
        if(getAeViewer()!=null) helpMenuItem=getAeViewer().addHelpURLItem(HELP_URL_COCHLEA, "AER-EAR wiki", "Opens user guide wiki for AER-EAR silicon cochleas");
        
    }
    

}
