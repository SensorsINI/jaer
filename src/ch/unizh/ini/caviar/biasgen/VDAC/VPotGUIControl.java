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

package ch.unizh.ini.caviar.biasgen.VDAC;

import ch.unizh.ini.caviar.biasgen.*;
import java.awt.*;
import javax.swing.*;

/**
 * A complete GUI conrol for an VPot.
 * @author tobi
 */
public class VPotGUIControl extends JPanel {
     
    VPotSliderTextControl sliderTextControl=null;
    PotGUIControl generalControls=null;
    
    /** Creates a new instance of IPotGUIControl */
    public VPotGUIControl(VPot pot, BiasgenFrame frame) {
        setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
        generalControls=new PotGUIControl(pot,frame);
        sliderTextControl=new VPotSliderTextControl(pot,frame);
        generalControls.getSliderAndValuePanel().add(sliderTextControl);
        add(generalControls);
        revalidate();
    }
    
}
