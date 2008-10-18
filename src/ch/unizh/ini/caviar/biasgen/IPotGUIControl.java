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

package ch.unizh.ini.caviar.biasgen;

import java.awt.*;
import javax.swing.*;

/**
 * A complete GUI conrol for an IPot.
 * @author tobi
 */
public class IPotGUIControl extends JPanel {
     
    private IPotSliderTextControl sliderTextControl=null;
    private PotGUIControl generalControls=null;
    private IPot pot;
    private BiasgenFrame frame;
    
    /** Creates a new instance of IPotGUIControl */
    public IPotGUIControl(IPot pot, BiasgenFrame frame) {
        this.pot=pot;
        this.frame=frame;
        setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
        getInsets().set(0, 0, 0, 0);
        generalControls=new PotGUIControl(pot,frame);
        sliderTextControl=new IPotSliderTextControl(pot,frame);
        generalControls.getSliderAndValuePanel().add(sliderTextControl);
        add(generalControls);
        revalidate();
    }

    public IPot getPot() {
        return pot;
    }

    public BiasgenFrame getFrame() {
        return frame;
    }

    
}
