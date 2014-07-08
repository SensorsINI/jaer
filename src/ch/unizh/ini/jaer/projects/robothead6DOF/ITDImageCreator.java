/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.robothead6DOF;

import java.io.IOException;
import java.util.ArrayList;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import org.ine.telluride.jaer.tell2011.head6axis.Head6DOF_ServoController;

/**
 *
 * @author philipp
 */
public class ITDImageCreator extends EventFilter2D {

    static public Head6DOF_ServoController headControl = null;
    static public ImageCreator imageCreator = null;
    static public ITDFilter_robothead6DOF itdFilter = null;
    FilterChain filterChain = null;
    java.util.Timer jitterTimer;
    public float jitterAmplitude = .01f;
    public float jitterFreqHz = 10f;
    public boolean jitterEnabled = false;
    public float[] startPosition = new float[2];
    boolean imageCreatorAlive = false;
    boolean iTDFilterAlive = false;
    boolean headControlAlive = false;
    boolean connectedToRobotHead = false;
    boolean iTDPanTiltActive = false;
    boolean jitteringActive = false;
    boolean filtersReady = false;

    public ITDImageCreator(AEChip chip) {
        super(chip);
        final String conn = "Connect", check = "Check filters", action = "Perform actions";
        setPropertyTooltip(check, "CheckFilters", "checks if all necessary filters are active and tries to connect to robothead");
        setPropertyTooltip(action, "ToggleITDMovement", "toggles between activated and deactivated ITD controlled head pan");
        setPropertyTooltip(action, "ToggleJittering", "toggles between activated and deactivated Jittering of the eyes");
        setPropertyTooltip(action, "DisconnectFromRobotHead", "disconnects from robothead");
        filterChain = new FilterChain(chip);
        itdFilter = new ITDFilter_robothead6DOF(chip);
        headControl = itdFilter.headControl;
        filterChain.add(itdFilter);
        setEnclosedFilterChain(filterChain);
        this.chip = chip;
    }

    public void doCheckFilters() {
        if (isFiltersReady() != true) {
            setiTDFilterAlive(true);
            if (isITDFilterAlive() == true) {
                setHeadControlAlive(true);
                if (isHeadControlAlive() == true) {
                    setConnectedToRobotHead(true);
                    if (isConnectedToRobotHead() == true) {
                        setImageCreatorAlive(true);
                        if (isImageCreatorAlive() == true) {
                            setFiltersReady(true);
                            log.info("all required filters are active");
                        }
                    }
                }
            }
        } else {
            log.info("filters are already ready");
        }
    }

    public void doToggleITDMovement() {
        if(isFiltersReady() == true){
            if(isITDPanTiltActive() == false){
                setITDPanTiltActive(true);
            } else {
                setITDPanTiltActive(false);
            }
        } else {
            log.info("Filters not ready; please check filters");
        }

    }

    public void doDisconnectFromRobotHead() {
        headControl.doDisconnect();
        setFiltersReady(false);
    }

    /**
     * Starts the servo jittering around its set position at an update frequency
     * of 50 Hz with an amplitude set by 'jitterAmplitude'
     *
     * @see #setJitterAmplitude
     */
    public void doToggleJittering() {
        if (isJitteringActive() != true) {
            setJitteringActive(true);
        } else {
            setJitteringActive(false);
        }
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    public static ImageCreator findExistingImageCreator(AEViewer myViewer) {
        ArrayList<AEViewer> viewers = myViewer.getJaerViewer().getViewers();
        for (AEViewer v : viewers) {
            AEChip c = v.getChip();
            FilterChain fc = c.getFilterChain();
            //Check for ImageCreator Filter:
            imageCreator = (ImageCreator) fc.findFilter(ImageCreator.class);
        }
        return imageCreator;
    }

    // <editor-fold defaultstate="collapsed" desc="is/setter for imageCreatorAlive">
    /**
     * @return the imageCreatorAlive
     */
    public boolean isImageCreatorAlive() {
        return imageCreatorAlive;
    }

    /**
     * @param imageCreatorAlive the imageCreatorAlive to set
     */
    public void setImageCreatorAlive(boolean imageCreatorAlive) {
        boolean old = this.imageCreatorAlive;
        imageCreator = findExistingImageCreator(chip.getAeViewer());
        if (imageCreator != null && imageCreator.isFilterEnabled()) {
            this.imageCreatorAlive = true;
        } else {
            log.info("can not find ImageCreator thread; please activate ImageCreator thread for DVS");
            this.imageCreatorAlive = false;
        }
        getSupport().firePropertyChange("imageCreatorAlive", old, this.imageCreatorAlive);
    } // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="is/setter for iTDFilterAlive">
    /**
     * @return the iTDFilterAlive
     */
    public boolean isITDFilterAlive() {
        return iTDFilterAlive;
    }

    /**
     * @param iTDFilterAlive the iTDFilterAlive to set
     */
    public void setiTDFilterAlive(boolean iTDFilterAlive) {
        boolean old = this.iTDFilterAlive;
        if (itdFilter.isFilterEnabled()) {
            this.iTDFilterAlive = true;
        } else {
            this.iTDFilterAlive = false;
            log.info("ITDFilter not enabled; please activate enclosed ITDFilter");
        }
        getSupport().firePropertyChange("iTDFilterAlive", old, this.iTDFilterAlive);
    } // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="is/setter for headControlAlive">
    /**
     * @return the headControlAlive
     */
    public boolean isHeadControlAlive() {
        return headControlAlive;
    }

    /**
     * @param headControlAlive the headControlAlive to set
     */
    public void setHeadControlAlive(boolean headControlAlive) {
        boolean old = this.headControlAlive;
        if (headControl.isFilterEnabled()) {
            this.headControlAlive = true;
        } else {
            this.headControlAlive = false;
            log.info("Head6DOF_ServoController not enabled; please activate enclosed Head6DOF_ServoController in ITDFilter");
        }
        getSupport().firePropertyChange("headControlAlive", old, this.headControlAlive);
    } // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="is/setter for connectedToRobotHead">
    /**
     * @return the connectedToRobotHead
     */
    public boolean isConnectedToRobotHead() {
        return connectedToRobotHead;
    }

    /**
     * @param connectedToRobotHead the connectedToRobotHead to set
     */
    public void setConnectedToRobotHead(boolean connectedToRobotHead) {
        boolean old = this.connectedToRobotHead;
        if (headControl.isConnected()) {
            this.connectedToRobotHead = true;
        } else {
            headControl.doConnect();
            if (headControl.isConnected()) {
                this.connectedToRobotHead = true;
            }
        }
        getSupport().firePropertyChange("connectedToRobotHead", old, this.connectedToRobotHead);
    } // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="is/setter for ITDPanTiltActive">
    /**
     * @return the iTDPanTiltActive
     */
    boolean isITDPanTiltActive() {
        return iTDPanTiltActive;
    }

    /**
     * @param iTDPanTiltActive the iTDPanTiltActive to set
     */
    void setITDPanTiltActive(boolean iTDPanTiltActive) {
        boolean old = this.iTDPanTiltActive;
        if (!old && iTDPanTiltActive) {
            itdFilter.doConnectToPanTiltThread();
        } else if (!iTDPanTiltActive && old) {
            itdFilter.doDisconnectFromPanTiltThread();
        }
        getSupport().firePropertyChange("iTDPanTiltActive", old, this.iTDPanTiltActive);
    } // </editor-fold>
        
    private class JittererTask extends TimerTask {
        long startTime=System.currentTimeMillis();

        JittererTask() {
            super();
        }

        @Override
        public void run() {
            long t=System.currentTimeMillis()-startTime;
            double phase=Math.PI*2*(double)t/1000*jitterFreqHz;
            float dx=(float)(jitterAmplitude*Math.sin(phase));
            float dy=(float)(jitterAmplitude*Math.cos(phase));
            try {
                headControl.setEyeGazeDirection(headControl.gazeDirection.getGazeDirection().x + dx, headControl.gazeDirection.getGazeDirection().y + dy);
            } catch (HardwareInterfaceException | IOException ex) {
                Logger.getLogger(ITDImageCreator.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
        
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterFreqHz--">
    public float getJitterFreqHz() {
        return jitterFreqHz;
    }

    /** Sets the frequency of the jitter.
     * @param jitterFreqHz in Hz. */
    public void setJitterFreqHz(float jitterFreqHz) {
        this.jitterFreqHz = jitterFreqHz;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterAmplitude--">
    public float getJitterAmplitude() {
        return jitterAmplitude;
    }

    /** Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt during jittering
     * 
     * @param jitterAmplitude the amplitude
     */
    public void setJitterAmplitude(float jitterAmplitude) {
        this.jitterAmplitude = jitterAmplitude;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="is/setter for JitteringAcitve">
    /**
     * @return the jitteringActive
     */
    boolean isJitteringActive() {
        return jitteringActive;
    }

    /**
     * @param jitteringActive the jitteringActive to set
     */
    void setJitteringActive(boolean jitteringActive) {
        if (jitteringActive == true){
            jitterTimer = new java.util.Timer();
            // Repeat the JitterTask without delay and with 20ms between executions
            startPosition[0] = headControl.gazeDirection.getGazeDirection().x;
            startPosition[1] = headControl.gazeDirection.getGazeDirection().y;
            jitterTimer.scheduleAtFixedRate(new JittererTask(), 0, 20);
        } else {
            jitterTimer.cancel();
            jitterTimer = null;
        }
        this.jitteringActive = jitteringActive;
    }// </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="is/setter for filtersReady">
    /**
     * @return the filtersReady
     */
    public boolean isFiltersReady() {
        return filtersReady;
    }

    /**
     * @param filtersReady the filtersReady to set
     */
    public void setFiltersReady(boolean filtersReady) {
        boolean old = this.filtersReady;
        this.filtersReady = filtersReady;
        getSupport().firePropertyChange("filtersReady", old, this.filtersReady);
    } // </editor-fold>
}
