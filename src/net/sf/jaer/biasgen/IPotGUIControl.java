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

package net.sf.jaer.biasgen;

import javax.swing.BoxLayout;
import javax.swing.JPanel;

/**
 * A complete GUI conrol for an IPot.
 * @author tobi
 */
public class IPotGUIControl extends JPanel {
     
    private IPotSliderTextControl sliderTextControl=null;
    private PotGUIControl generalControls=null;
    private IPot pot;
    
    /** Creates a new instance of IPotGUIControl
     * @param pot the IPot to control
     */
    public IPotGUIControl(IPot pot) {
        this.pot=pot;
        setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
        getInsets().set(0, 0, 0, 0);
        generalControls=new PotGUIControl(pot);
        sliderTextControl=new IPotSliderTextControl(pot);
        generalControls.getSliderAndValuePanel().add(sliderTextControl);
        add(generalControls);
        revalidate();
    }

    public IPot getPot() {
        return pot;
    }

}
