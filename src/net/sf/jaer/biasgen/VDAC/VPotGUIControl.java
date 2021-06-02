/*
 * IPotGUIControl.java
 *
 * Created on December 6, 2006, 12:27 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright December 6, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.biasgen.VDAC;

import java.awt.Component;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import net.sf.jaer.biasgen.PotGUIControl;

/**
 * A complete GUI conrol for an VPot.
 * @author tobi
 */
public class VPotGUIControl extends JPanel {
     
    VPotSliderTextControl sliderTextControl=null;
    PotGUIControl generalControls=null;
    private VPot pot;
    
    /** Creates a new instance of IPotGUIControl */
    public VPotGUIControl(VPot pot) {
        this.pot=pot;
        setLayout(new BoxLayout(this,BoxLayout.X_AXIS)); 
        setAlignmentX(Component.LEFT_ALIGNMENT);
        generalControls=new PotGUIControl(pot);
        sliderTextControl=new VPotSliderTextControl(pot);
        generalControls.getSliderAndValuePanel().add(sliderTextControl);
        add(generalControls);
        revalidate();
    }

    public VPot getPot() {
        return pot;
    }

    public void setPot(VPot pot) {
        this.pot = pot;
    }

}
