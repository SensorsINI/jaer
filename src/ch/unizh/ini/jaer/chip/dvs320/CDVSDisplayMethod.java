/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.dvs320;

import java.awt.BorderLayout;
import java.util.prefs.Preferences;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JPanel;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.ChipCanvas;

/**
 * Displays data from CDVS chips.
 * @author Tobi
 */
public class CDVSDisplayMethod extends DVSWithIntensityDisplayMethod {
    private boolean displayLogIntensity;
    private boolean displayColorChangeEvents;
    private boolean displayLogIntensityChangeEvents;
    Preferences prefs=null;

    boolean registeredControlPanel=false;

    public CDVSDisplayMethod(ChipCanvas parent) {
        super(parent);
        prefs=parent.getChip().getPrefs();
        displayLogIntensity=prefs.getBoolean("displayLogIntensity", true);
        displayColorChangeEvents=prefs.getBoolean("displayColorChangeEvents", true);
        displayLogIntensityChangeEvents=prefs.getBoolean("displayLogIntensityChangeEvents", true);

    }

    @Override
    public void display(GLAutoDrawable drawable) {
        checkControlPanel();
         super.display(drawable);
       
    }

    private void checkControlPanel() {
        if(registeredControlPanel) return;
        try {
            AEChip chip = (AEChip) getChipCanvas().getChip();
            AEViewer viewer = chip.getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
            JPanel imagePanel = viewer.getImagePanel();
            imagePanel.add(new cDVSDisplayControlPanel(this), BorderLayout.SOUTH);
            registeredControlPanel=true;
        } catch (Exception e) {
            log.warning("could not register control panel");
        }
    }

    /**
     * @return the displayLogIntensity
     */
    public boolean isDisplayLogIntensity() {
        return displayLogIntensity;
    }

    /**
     * @param displayLogIntensity the displayLogIntensity to set
     */
    public void setDisplayLogIntensity(boolean displayLogIntensity) {
        this.displayLogIntensity = displayLogIntensity;
        prefs.putBoolean("displayLogIntensity", displayLogIntensity);
    }

    /**
     * @return the displayColorChangeEvents
     */
    public boolean isDisplayColorChangeEvents() {
        return displayColorChangeEvents;
    }

    /**
     * @param displayColorChangeEvents the displayColorChangeEvents to set
     */
    public void setDisplayColorChangeEvents(boolean displayColorChangeEvents) {
        this.displayColorChangeEvents = displayColorChangeEvents;
        prefs.putBoolean("displayColorChangeEvents", displayColorChangeEvents);
    }

    /**
     * @return the displayLogIntensityChangeEvents
     */
    public boolean isDisplayLogIntensityChangeEvents() {
        return displayLogIntensityChangeEvents;
    }

    /**
     * @param displayLogIntensityChangeEvents the displayLogIntensityChangeEvents to set
     */
    public void setDisplayLogIntensityChangeEvents(boolean displayLogIntensityChangeEvents) {
        this.displayLogIntensityChangeEvents = displayLogIntensityChangeEvents;
        prefs.putBoolean("displayLogIntensityChangeEvents", displayLogIntensityChangeEvents);
    }
}
