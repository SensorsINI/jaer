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

package ch.unizh.ini.jaer.config.dac;

import javax.swing.BoxLayout;
import javax.swing.JPanel;

/**
 * A complete GUI conrol for an VPot.
 * @author tobi
 */
public class DACchannelControl extends JPanel {
     
    DACchannelTextControl sliderTextControl=null;
    DACguiControl generalControls=null;
    private DACchannel dacchannel;
    
    /** Creates a new instance of IPotGUIControl */
    public DACchannelControl(DACchannel dacchannel) {
        this.dacchannel=dacchannel;
        setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
        generalControls=new DACguiControl(dacchannel);
        sliderTextControl=new DACchannelTextControl(dacchannel);
        generalControls.getSliderAndValuePanel().add(sliderTextControl);
        add(generalControls);
        revalidate();
    }

    public DACchannel getDACchannel() {
        return dacchannel;
    }

    public void setDACchannel(DACchannel dacchannel) {
        this.dacchannel = dacchannel;
    }

}
