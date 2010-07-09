/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JOptionPane;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.TobiLogger;

/**
 * Controls slot car tracked from eye of god view.
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class SlotCarRacer extends EventFilter2D implements FrameAnnotater {

    public static String getDescription() {
        return "Slot car racer project, Telluride 2010";
    }
    private boolean showTrackEnabled = prefs().getBoolean("SlotCarRacer.showTrack", true);
    private boolean virtualCarEnabled = prefs().getBoolean("SlotCarRacer.virtualCar", false);
    private TobiLogger tobiLogger;
    private SlotCarHardwareInterface hw;
    private SlotcarFrame slotCarFrame;
    private CarTracker carTracker;
    private FilterChain filterChain;
    private boolean overrideThrottle = prefs().getBoolean("SlotCarRacer.overrideThrottle", true);
    private float overriddenThrottleSetting = prefs().getFloat("SlotCarRacer.overriddenThrottleSetting", 0);
//    private SlotCarController controller = null;
    private SlotcarTrack trackModel;
    private TextRenderer renderer;
    private AbstractSlotCarController throttleController;
    private float maxThrottle = prefs().getFloat("SlotCarRacer.maxThrottle", 1);

    public enum ControllerToUse {

        SimpleSpeedController, CurvatureBasedController
    };
    private ControllerToUse controllerToUse = ControllerToUse.valueOf(prefs().get("SlotCarRacer.controllerToUse", ControllerToUse.SimpleSpeedController.toString()));

    public SlotCarRacer(AEChip chip) {
        super(chip);
        hw = new SlotCarHardwareInterface();
        slotCarFrame = new SlotcarFrame();


        filterChain = new FilterChain(chip);

        carTracker = new CarTracker(chip);
        filterChain.add(carTracker);

        throttleController = new SimpleSpeedController(chip);
        filterChain.add(throttleController);                    // TODO when controller is changed the filter chain is not changed yet

        setEnclosedFilterChain(filterChain);

        tobiLogger = new TobiLogger("SlotCarRacer", "racer data " + throttleController.getLogContentsHeader());

        // tooltips for properties
        String con = "Controller", dis = "Display", ov = "Override", vir = "Virtual car", log = "Logging";
        setPropertyTooltip(con, "desiredSpeed", "Desired speed from speed controller");
        setPropertyTooltip(ov, "overrideThrottle", "Select to override the controller throttle setting");
        setPropertyTooltip(con, "maxThrottle", "Absolute limit on throttle for safety");
        setPropertyTooltip(ov, "overriddenThrottleSetting", "Manual overidden throttle setting");
        setPropertyTooltip(vir, "virtualCarEnabled", "Enable display of virtual car on virtual track");
        setPropertyTooltip(log, "logRacerDataEnabled", "enables logging of racer data");
        setPropertyTooltip(con, "controllerToUse", "Which controller to use to control car throttle");

    }

    public void doLearnTrack() {
        JOptionPane.showMessageDialog(chip.getAeViewer(), "I should do something");
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        out=getEnclosedFilterChain().filterPacket(in);
        setThrottle();
        return out;
    }
    private float lastThrottle = 0;

    synchronized private void setThrottle() {

        if (isOverrideThrottle()) {
            lastThrottle = getOverriddenThrottleSetting();
        } else {
            lastThrottle = throttleController.computeControl(carTracker, trackModel);
        }
        lastThrottle = lastThrottle > maxThrottle ? maxThrottle : lastThrottle;
        hw.setThrottle(lastThrottle);

        if (isLogRacerDataEnabled()) {
            logRacerData(throttleController.logControllerState());
        }

    }

    @Override
    public void resetFilter() {
        if (hw.isOpen()) {
            hw.close();
        }
    }

    @Override
    public void initFilter() {
    }

    public synchronized void annotate(GLAutoDrawable drawable) { // TODO may not want to synchronize here since this will block filtering durring annotation
        carTracker.annotate(drawable);
        throttleController.annotate(drawable);
        if (renderer == null) {
            renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 24), true, true);
        }
        renderer.begin3DRendering();
        String s = "Throttle:" + lastThrottle;
        final float scale = .25f;
        renderer.draw3D(s, 0, 2, 0, scale);
//        Rectangle2D bounds=renderer.getBounds(s);
        renderer.end3DRendering();
//        GL gl=drawable.getGL();
//        gl.glRectf((float)bounds.getMaxX()*scale, 2,(float) (chip.getSizeX()-scale*bounds.getWidth())*lastThrottle, 4);
    }

    /**
     * @return the overrideThrottle
     */
    public boolean isOverrideThrottle() {
        return overrideThrottle;
    }

    /**
     * @param overrideThrottle the overrideThrottle to set
     */
    public void setOverrideThrottle(boolean overrideThrottle) {
        this.overrideThrottle = overrideThrottle;
        prefs().putBoolean("SlotCarRacer.overrideThrottle", overrideThrottle);
    }

    /**
     * @return the overriddenThrottleSetting
     */
    public float getOverriddenThrottleSetting() {
        return overriddenThrottleSetting;
    }

    /**
     * @param overriddenThrottleSetting the overriddenThrottleSetting to set
     */
    public void setOverriddenThrottleSetting(float overriddenThrottleSetting) {
        this.overriddenThrottleSetting = overriddenThrottleSetting;
        prefs().putFloat("SlotCarRacer.overriddenThrottleSetting", overriddenThrottleSetting);
    }

    // for GUI slider
    public float getMaxOverriddenThrottleSetting() {
        return 1;
    }

    public float getMinOverriddenThrottleSetting() {
        return 0;
    }

    /**
     * @return the virtualCarEnabled
     */
    public boolean isVirtualCarEnabled() {
        return virtualCarEnabled;
    }

    /**
     * @param virtualCarEnabled the virtualCarEnabled to set
     */
    public void setVirtualCarEnabled(boolean virtualCarEnabled) {
        this.virtualCarEnabled = virtualCarEnabled;
        prefs().putBoolean("SlotCarRacer.virtualCarEnabled", virtualCarEnabled);

    }

    public synchronized void setLogRacerDataEnabled(boolean logDataEnabled) {
        tobiLogger.setEnabled(logDataEnabled);
    }

    public synchronized void logRacerData(String s) {
        tobiLogger.log(s);
    }

    public boolean isLogRacerDataEnabled() {
        if (tobiLogger == null) {
            return false;
        }
        return tobiLogger.isEnabled();
    }

    /**
     * @return the maxThrottle
     */
    public float getMaxThrottle() {
        return maxThrottle;
    }

    /**
     * @param maxThrottle the maxThrottle to set
     */
    public void setMaxThrottle(float maxThrottle) {
        if (maxThrottle > 1) {
            maxThrottle = 1;
        } else if (maxThrottle < 0) {
            maxThrottle = 0;
        }
        this.maxThrottle = maxThrottle;
        prefs().putFloat("SlotCarRacer.maxThrottle", maxThrottle);
    }

    public float getMaxMaxThrottle() {
        return 1;
    }

    public float getMinMaxThrottle() {
        return 0;
    }

    /**
     * @return the controllerToUse
     */
    public ControllerToUse getControllerToUse() {
        return controllerToUse;
    }

    /**
     * @param controllerToUse the controllerToUse to set
     */
    synchronized public void setControllerToUse(ControllerToUse controllerToUse) {
        this.controllerToUse = controllerToUse;
        try {
            switch (controllerToUse) {
                case SimpleSpeedController:
                    if (!(throttleController instanceof SimpleSpeedController)) {
                        filterChain.remove(throttleController);
                        throttleController = new SimpleSpeedController(chip);
                        throttleController.setFilterEnabled(true);
                        filterChain.add(throttleController);
                        chip.getAeViewer().getFilterFrame().rebuildContents();
                    }
                    break;
                case CurvatureBasedController:
                    if (!(throttleController instanceof CurvatureBasedController)) {
                        filterChain.remove(throttleController);
                        throttleController = new CurvatureBasedController(chip);
                        throttleController.setFilterEnabled(true);
                        filterChain.add(throttleController);
                        chip.getAeViewer().getFilterFrame().rebuildContents();
                    }
                    break;
            }
            prefs().put("SlotCarRacer.controllerToUse", controllerToUse.toString());
        } catch (Exception e) {
            log.warning(e.toString());
        }
    }
}
