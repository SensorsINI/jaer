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
    private VPot pot;
    private BiasgenFrame frame;
    
    /** Creates a new instance of IPotGUIControl */
    public VPotGUIControl(VPot pot, BiasgenFrame frame) {
        this.pot=pot;
        this.frame=frame;
        setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
        generalControls=new PotGUIControl(pot,frame);
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

    public BiasgenFrame getFrame() {
        return frame;
    }

    public void setFrame(BiasgenFrame frame) {
        this.frame = frame;
    }
    
}
