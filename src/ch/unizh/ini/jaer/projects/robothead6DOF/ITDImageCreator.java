/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.robothead6DOF;

import java.io.IOException;
import java.util.ArrayList;
import java.util.TimerTask;
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

    protected static final Logger log = Logger.getLogger("ITDImageCreator");
    static public Head6DOF_ServoController headControl = null;
    static public ImageCreator imageCreator = null;
    static public ImageCreatorSlave imageCreatorSlave = null;
    static public ITDFilter_robothead6DOF itdFilter = null;
    FilterChain filterChain = null;
    java.util.Timer jitterTimer;
    public float jitterAmplitude = .0133f;
    public float jitterFreqHz = 3.5f;
    public boolean jitterEnabled = false;
    public float[] startPosition = new float[2];
    long time;
    long timeold;
    java.util.Timer savingTimer;
    boolean imageCreatorAlive = false;
    boolean iTDFilterAlive = false;
    boolean headControlAlive = false;
    boolean connectedToRobotHead = false;
    boolean iTDPanTiltActive = false;
    boolean jitteringActive = false;
    boolean filtersReady = false;
    boolean invert = false;
    boolean captureImage = false;
    boolean savingActive = false;
    long startTimeTest;

    private String numOfCameras = String.valueOf(getString("ITDImageCreator.numOfCameras", "2"));  // number of connected DVS cameras
    private String resetTime = String.valueOf(getString("ITDImageCreator.resetTime", "2000"));  // time (in ms) before image is resetted

    public ITDImageCreator(AEChip chip) {
        super(chip);
        final String conn = "Connect", check = "Check filters", jitt = "Jitter Options";
        setPropertyTooltip(jitt, "jitterAmplitude", "the amplitude of the jitter; higher value results in larger circle");
        setPropertyTooltip(jitt, "jitterFreqHz", "defines the jitter frequency");
        setPropertyTooltip(check, "connectedToRobotHead", "indicates if the robot head is connected");
        setPropertyTooltip(check, "filtersReady", "checks if all necessary filters are active and tries to connect to robothead");
        setPropertyTooltip(check, "headControlAlive", "indicates if the enclosed robot head control is active");
        setPropertyTooltip(check, "imageCreatorAlive", "indicates if this filter is connected to an ImageCreator filter");
        setPropertyTooltip(check, "numOfCameras", "defines the number of connected DVS128 cameras; enter value before checking filters");
        setPropertyTooltip(check, "resetTime", "defines the time before the image resets");
        setPropertyTooltip("ToggleITDMovement", "toggles between activated and deactivated ITD controlled head pan");
        setPropertyTooltip("ToggleJittering", "toggles between activated and deactivated jitter of the eyes");
        setPropertyTooltip("ResetImage", "resets the image frame of the ImageCreator and fills it with the initial gray value again");
        setPropertyTooltip("ToggleCaptureImage", "toggles between activate and deactivated image capturing by the ImageCreator filter");
        setPropertyTooltip("CheckFilters", "checks and enables all necessary filters");
        setPropertyTooltip("DisconnectFromRobotHead", "disconnects the filter from the robot head");
        setPropertyTooltip("ToggleSaveImages", "starts saving the histogram images");
        filterChain = new FilterChain(chip);
        itdFilter = new ITDFilter_robothead6DOF(chip);
        headControl = itdFilter.headControl;
        filterChain.add(itdFilter);
        setEnclosedFilterChain(filterChain);
        timeold = System.currentTimeMillis();
        this.chip = chip;
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        out = filterChain.filterPacket(in);
        if (out == null) {
            return null;
        }
        if (isFiltersReady() == true && Integer.parseInt(numOfCameras) >= 1) {
            time = System.currentTimeMillis();
            if (time - timeold > Long.parseLong(getResetTime())) {
                try {
                    imageCreator.doReset(); //resets the image to receive a new image
                    if (Integer.parseInt(numOfCameras) == 2) {
                        imageCreatorSlave.doReset();
                    }
                    timeold = time;
                } catch (HardwareInterfaceException | IOException ex) {
                    log.severe(ex.toString());
                }
            }
        }
        return out;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    public void doCheckFilters() {      //checks if all necessary filters are ready
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
        if (isFiltersReady() == true) {
            if (isITDPanTiltActive() == false) {
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

    public void doToggleCaptureImage() {
        if (isCaptureImage() != true) {
            setCaptureImage(true);
        } else {
            setCaptureImage(false);
        }
    }

    public void doToggleSaveImages() {
        if (isSavingActive() != true) {
            setSavingActive(true);
        } else {
            setSavingActive(false);
        }
    }

    public void doResetImage() {
        try {
            imageCreator.doReset();
            imageCreatorSlave.doReset();
        } catch (HardwareInterfaceException | IOException ex) {
            log.severe(ex.toString());
        }
    }

    public void setMoving(boolean moving) {
        if (isImageCreatorAlive() == true) {
            if (moving == true) {
                imageCreator.setGettingImage(false);
                if (imageCreatorSlave != null) {
                    imageCreatorSlave.gettingImage = false;
                }
            } else {
                imageCreator.setGettingImage(true);
                if (imageCreatorSlave != null) {
                    imageCreatorSlave.gettingImage = true;
                }
            }
        }
    }

    public static ImageCreator findExistingImageCreator(AEViewer myViewer) {
        ArrayList<AEViewer> viewers = myViewer.getJaerViewer().getViewers();
        for (AEViewer v : viewers) {
            if (imageCreator == null) {
                AEChip c = v.getChip();
                FilterChain fc = c.getFilterChain();
                //Check for ImageCreator Filter:
                imageCreator = (ImageCreator) fc.findFilter(ImageCreator.class);
            } else {
                return imageCreator;
            }
        }
        return imageCreator;
    }

    public static ImageCreatorSlave findExistingImageCreatorSlave(AEViewer myViewer) {
        ArrayList<AEViewer> viewers = myViewer.getJaerViewer().getViewers();
        for (AEViewer v : viewers) {
            if (imageCreatorSlave == null) {
                AEChip c = v.getChip();
                FilterChain fc = c.getFilterChain();
                //Check for ImageCreator Filter:
                imageCreatorSlave = (ImageCreatorSlave) fc.findFilter(ImageCreatorSlave.class);
            } else {
                return imageCreatorSlave;
            }
        }
        return imageCreatorSlave;
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for numOfCameras">
    /**
     * @return the numOfCameras
     */
    public String getNumOfCameras() {
        return numOfCameras;
    }

    /**
     * @param numOfCameras the numOfCameras to set
     */
    public void setNumOfCameras(String numOfCameras) {
        this.numOfCameras = numOfCameras;
        putString("ITDImageCreator.numOfCameras", numOfCameras);
        log.info(this.numOfCameras);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for resetTime">
    /**
     * @return the resetTime
     */
    public String getResetTime() {
        return resetTime;
    }

    /**
     * @param resetTime the numOfCameras to set
     */
    public void setResetTime(String resetTime) {
        this.resetTime = resetTime;
        putString("ITDImageCreator.resetTime", resetTime);
        log.info(this.resetTime);
    }
    // </editor-fold>

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
        if (Integer.parseInt(numOfCameras) == 0) {
            setFiltersReady(true);
            log.info("all required filters are active");
        }
        if (Integer.parseInt(numOfCameras) == 1) {
            imageCreator = findExistingImageCreator(chip.getAeViewer());
            if (imageCreator != null && imageCreator.isFilterEnabled()) {
                //              imageCreator.setStandAlone(false);
                this.imageCreatorAlive = true;
            } else {
                log.info("can not find ImageCreator thread; please activate ImageCreator thread for DVS");
                this.imageCreatorAlive = false;
            }
        }
        if (Integer.parseInt(numOfCameras) == 2) {
            imageCreator = findExistingImageCreator(chip.getAeViewer());
            imageCreatorSlave = findExistingImageCreatorSlave(chip.getAeViewer());
            if (imageCreator != null && imageCreator.isFilterEnabled() && imageCreatorSlave != null && imageCreatorSlave.isFilterEnabled()) {
                //imageCreator.setStandAlone(false);
                imageCreatorSlave.doConnectToMaster();
                this.imageCreatorAlive = true;
            } else {
                log.info("can not find ImageCreator or ImageCreatorSlave thread; please activate ImageCreator or ImageCreatorSlave thread for DVS");
                this.imageCreatorAlive = false;
            }
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
            imageCreator.setStandAlone(false);
            itdFilter.doConnectToPanTiltThread(this);
            this.iTDPanTiltActive = true;
        } else if (!iTDPanTiltActive && old) {
            itdFilter.doDisconnectFromPanTiltThread();
            imageCreator.setStandAlone(true);
            this.iTDPanTiltActive = false;
        }
        getSupport().firePropertyChange("iTDPanTiltActive", old, this.iTDPanTiltActive);
    } // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="is/setter for CaptureImage">
    /**
     * @return the captureImage
     */
    boolean isCaptureImage() {
        return captureImage;
    }

    /**
     * @param captureImage the captureImage to set
     */
    void setCaptureImage(boolean captureImage) {
        boolean old = this.captureImage;
        if (!old && captureImage) {
            imageCreator.doStartCaptureImage();
            imageCreator.setGrayValueScaling(0.05f);
            this.captureImage = true;
        } else if (!captureImage && old) {
            imageCreator.doStopCaptureImage();
            this.captureImage = false;
        }
        getSupport().firePropertyChange("captureImage", old, this.captureImage);
    } // </editor-fold>

    private class JittererTask extends TimerTask {

        long startTime = System.currentTimeMillis();

        JittererTask() {
            super();
        }

        @Override
        public void run() {
            if (System.currentTimeMillis() - startTimeTest < 960) {
                long t = System.currentTimeMillis() - startTime;
                double phase = Math.PI * 2 * (double) t / 1000 * jitterFreqHz;
                float dx = (float) (jitterAmplitude * Math.sin(phase));
                float dy = (float) (jitterAmplitude * Math.cos(phase));
                try {
                    headControl.setEyeGazeDirection(startPosition[0] + dx, startPosition[1] + dy);
                    if (isImageCreatorAlive() == true) {
                        if (headControl.gazeDirection.getEyeDirection().getX() + dx - headControl.gazeDirection.getEyeDirection().getX() < 0 || headControl.gazeDirection.getEyeDirection().getY() + dy - headControl.gazeDirection.getEyeDirection().getY() < 0) {
                            imageCreator.setInvert(true);
                            imageCreator.setxOffset((float) headControl.getGazeDirection().getHeadDirection().getX(), (float) headControl.getGazeDirection().getEyeDirection().getX());
                            imageCreator.setyOffset((float) headControl.getGazeDirection().getHeadDirection().getY(), (float) headControl.getGazeDirection().getEyeDirection().getY());
                        } else {
                            imageCreator.setInvert(false);
                            imageCreator.setxOffset((float) headControl.getGazeDirection().getHeadDirection().getX(), (float) headControl.getGazeDirection().getEyeDirection().getX());
                            imageCreator.setyOffset((float) headControl.getGazeDirection().getHeadDirection().getY(), (float) headControl.getGazeDirection().getEyeDirection().getY());
                        }
                    }
                } catch (HardwareInterfaceException | IOException ex) {
                    log.severe(ex.toString());
                }
            } else {
                doToggleJittering();
                headControl.doCenterGaze();
                imageCreator.setGettingImage(false);
            }
        }
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterFreqHz--">
    public float getJitterFreqHz() {
        return jitterFreqHz;
    }

    /**
     * Sets the frequency of the jitter.
     *
     * @param jitterFreqHz in Hz.
     */
    public void setJitterFreqHz(float jitterFreqHz) {
        this.jitterFreqHz = jitterFreqHz;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterAmplitude--">
    public float getJitterAmplitude() {
        return jitterAmplitude;
    }

    /**
     * Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt
     * during jittering
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
        if (jitteringActive == true) {
            imageCreator.setStandAlone(false);
            jitterTimer = new java.util.Timer();
            // Repeat the JitterTask without delay and with 20ms between executions
            startPosition[0] = (float) headControl.gazeDirection.getEyeDirection().getX();
            startPosition[1] = (float) headControl.gazeDirection.getEyeDirection().getY();
            startTimeTest = System.currentTimeMillis();
            jitterTimer.scheduleAtFixedRate(new JittererTask(), 0, 20);
        } else {
            jitterTimer.cancel();
            jitterTimer = null;
            imageCreator.setStandAlone(true);
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

    // <editor-fold defaultstate="collapsed" desc="is/setter for SavingActive">
    /**
     * @return the savingActive
     */
    boolean isSavingActive() {
        return savingActive;
    }

    /**
     * @param jitteringActive the jitteringActive to set
     */
    void setSavingActive(boolean savingActive) {
        if (savingActive == true) {
            if (isFiltersReady() == true && Integer.parseInt(numOfCameras) >= 1) {
                imageCreator.doToggleSavingImage();
                if (Integer.parseInt(numOfCameras) == 2) {
                    imageCreatorSlave.doToggleSavingImage();
                }
            }
        } else {
            if (isFiltersReady() == true && Integer.parseInt(numOfCameras) >= 1) {
                imageCreator.doToggleSavingImage();
                if (Integer.parseInt(numOfCameras) == 2) {
                    imageCreatorSlave.doToggleSavingImage();
                }
            }
        }
        this.savingActive = savingActive;
    }// </editor-fold>
}
