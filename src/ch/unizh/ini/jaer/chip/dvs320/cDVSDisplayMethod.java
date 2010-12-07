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
public class cDVSDisplayMethod extends DVSWithIntensityDisplayMethod {

    private cDVSTest20 cDVSChip = null;
    boolean registeredControlPanel = false;
    private cDVSDisplayControlPanel controlPanel = null;

    public cDVSDisplayMethod(cDVSTest20 chip) {
        super(chip.getCanvas());
        this.cDVSChip = chip;
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        checkControlPanel();
        super.display(drawable);

    }

    private void checkControlPanel() {
        if (registeredControlPanel) {
            return;
        }
        registerControlPanel();
    }

    public void registerControlPanel() {
        try {
            AEChip chip = (AEChip) getChipCanvas().getChip();
            AEViewer viewer = chip.getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
            JPanel imagePanel = viewer.getImagePanel();
            imagePanel.add((controlPanel = new cDVSDisplayControlPanel(cDVSChip)), BorderLayout.SOUTH);
            registeredControlPanel = true;
        } catch (Exception e) {
            log.warning("could not register control panel: " + e);
        }
    }

    void unregisterControlPanel() {
        try {
            AEChip chip = (AEChip) getChipCanvas().getChip();
            AEViewer viewer = chip.getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
            JPanel imagePanel = viewer.getImagePanel();
            imagePanel.remove(controlPanel);
            registeredControlPanel = false;
        } catch (Exception e) {
            log.warning("could not unregister control panel: " + e);
        }
    }

    public void setDisplayLogIntensityChangeEvents(boolean displayLogIntensityChangeEvents) {
        cDVSChip.setDisplayLogIntensityChangeEvents(displayLogIntensityChangeEvents);
    }

    public void setDisplayLogIntensity(boolean displayLogIntensity) {
        cDVSChip.setDisplayLogIntensity(displayLogIntensity);
    }

    public void setDisplayColorChangeEvents(boolean displayColorChangeEvents) {
        cDVSChip.setDisplayColorChangeEvents(displayColorChangeEvents);
    }

    public boolean isDisplayLogIntensityChangeEvents() {
        return cDVSChip.isDisplayLogIntensityChangeEvents();
    }

    public boolean isDisplayLogIntensity() {
        return cDVSChip.isDisplayLogIntensity();
    }

    public boolean isDisplayColorChangeEvents() {
        return cDVSChip.isDisplayColorChangeEvents();
    }
}
