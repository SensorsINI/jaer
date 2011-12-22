
package uk.ac.imperial.pseye;

import net.sf.jaer.chip.AEChip;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.Observer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import java.io.File;
import java.io.IOException;
import net.sf.jaer.eventio.AEFileInputStream;

/**
 * A private base class for the behavioural model of several AE retina models 
 * using the PS3-Eye camera.
 * Unlike the rest of jAER all hardware control should be done through
 * the chip's control panels as this handles setting validation and preferences (for 
 * camera and chip).
 * 
 * @author Tobi Delbruck and Mat Katz
 */
abstract class PSEyeModelChip extends AEChip implements PreferenceChangeListener {
    // camera values being used by chip
    public PSEyeCamera.Mode mode;
    public PSEyeCamera.Resolution resolution;
    public int frameRate;
    
    public int gain;
    public int exposure;
    public boolean autoGainEnabled;
    public boolean autoExposureEnabled;
    
    // supported suto gain and exposure
    // NB supported modes, resolutions, frame rates, 
    // exposure and gain ranges called direct from chip
    private static boolean supportsAutoGain = true;
    private static boolean supportsAutoExposure = true;

    PSEyeModelRenderer renderer = null;
    
    @Override
    public void preferenceChange(PreferenceChangeEvent evt) {
        getBiasgen().loadPreferences();
    }

    /** Observable events; This event is fired when the parameter is changed. */
    public static enum EVENT { 
        MODEL,
        MODE,
        RESOLUTION,
        FRAMERATE,
        GAIN,
        EXPOSURE,
        AUTO_GAIN,
        AUTO_EXPOSURE;
    }

    public PSEyeModelChip() {
        setEventExtractor(createEventExtractor());
        loadPreferences();
        
        setBiasgen(new PSEyeBiasgen(this));
        setRenderer((renderer = new PSEyeModelRenderer(this)));
        
        getPrefs().addPreferenceChangeListener(this);
    }

    /* needed to ensure listener deregistered on construction of
     * an equivalent instance and so prevent a memory leak 
     */
    @Override
    public void cleanup() {
        super.cleanup();
        getPrefs().removePreferenceChangeListener(this);
    }
    
    /* check to see if hardware interface set */
    protected boolean checkHardware() {
        return hardwareInterface != null && (hardwareInterface instanceof PSEyeHardwareInterface);
    }
    
    protected void loadPreferences() {
        try {
            // set mode, resolution, framerate
            String defaultMode = getSupportedModes().get(0).toString();
            setMode(PSEyeCamera.Mode.valueOf(getPrefs().get("mode", defaultMode)));
            
            String defaultResolution = getSupportedResolutions().get(0).toString();
            setResolution(PSEyeCamera.Resolution.valueOf(getPrefs().get("resolution", 
                    defaultResolution)));
            
            setFrameRate(getPrefs().getInt("frameRate", 60));
                
            // set exposure, gain, and auto gain/exposure settings
            setGain(getPrefs().getInt("gain", 30));
            setExposure(getPrefs().getInt("exposure", 0));
            setAutoGain(getPrefs().getBoolean("autoGainEnabled", true));
            setAutoExposure(getPrefs().getBoolean("autoExposureEnabled", true));      
        } catch (Exception ex) {
            log.warning(ex.toString());
        }
        // resize chip
        resolutionChange();
        
        if (getEventExtractor() != null && (getEventExtractor() instanceof PSEyeEventExtractor))
            ((PSEyeEventExtractor) getEventExtractor()).loadPreferences(getPrefs());
    }

    protected void storePreferences() {
        // use getter functions to store parameters
        getPrefs().put("mode", getMode().name());
        getPrefs().put("resolution", getResolution().name());
        getPrefs().putInt("frameRate", getFrameRate());
        getPrefs().putInt("gain", getGain());
        getPrefs().putInt("exposure", getExposure());
        getPrefs().putBoolean("autoGainEnabled", isAutoGain());
        getPrefs().putBoolean("autoExposureEnabled", isAutoExposure());
        
        if (getEventExtractor() != null && (getEventExtractor() instanceof PSEyeEventExtractor))
            ((PSEyeEventExtractor) getEventExtractor()).storePreferences(getPrefs());
    }

    /* load all hardware parameters from chip 
     * cannot use chip setters due to notify and setChange
     * which should not fire unless chip changed, camera
     * notifiers will fire though - used by raw panel.
     * Assumes hardware interface not already open (otherwise get multiple resets).
     */
    @Override
    public void setHardwareInterface(HardwareInterface hardwareInterface) {
        if (hardwareInterface != null && (hardwareInterface instanceof PSEyeHardwareInterface)) {
            super.setHardwareInterface(hardwareInterface);
            PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
            
            try {
                // set mode, resolution, framerate
                mode = pseye.setMode(mode);
                resolution = pseye.setResolution(resolution);
                frameRate = pseye.setFrameRate(frameRate);
                
                // set exposure, gain, and auto gain/exposure settings
                gain = pseye.setGain(gain);
                exposure = pseye.setExposure(exposure);
                autoGainEnabled = pseye.setAutoGain(autoGainEnabled);
                autoExposureEnabled = pseye.setAutoExposure(autoExposureEnabled);      
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
        }
        else if (hardwareInterface == null) {
            if (checkHardware()) {
                PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
                pseye.close();
            }
            super.setHardwareInterface(null);
        }
        else
            log.warning("tried to set HardwareInterface to not a PSEyeHardwareInterface: " + hardwareInterface);
    }

    // should this iinclude mode, resolution and frame rate?
    public void sendConfiguration() {
        if (checkHardware()) {
            setGain(gain);
            setExposure(exposure);
            setAutoExposure(autoExposureEnabled);
            setAutoGain(autoGainEnabled);
        }
    }

    abstract protected PSEyeEventExtractor createEventExtractor();
    
    /* Get the value of gain */
    public int getGain() {
        // check hardware interface exists and is PSEye camera
        if (checkHardware()) {
            PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
            gain = pseye.getGain();
        }
        return gain;
    }

    /* Set the value of gain */
    synchronized public void setGain(int gain) {
        if (gain < getMinGain()) {
            gain = getMinGain();
        } else if (gain > getMaxGain()) {
            gain = getMaxGain();
        }
        if (this.gain != gain) {
            // check hardware interface exists and is PSEye camera
            if (checkHardware()) {
                PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
                try {
                    this.gain = pseye.setGain(gain);
                } catch (Exception ex) {
                   log.warning(ex.toString());
                }
            }
            else
                this.gain = gain;
            
            setChanged();
            notifyObservers(EVENT.GAIN);
        }
    }

    /* Get the value of exposure */
    public int getExposure() {
        // check hardware interface exists and is PSEye camera
        if (checkHardware()) {
            PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
            exposure = pseye.getExposure();
        }
        return exposure;
    }

    /* Set the value of exposure */
    synchronized public void setExposure(int exposure) {
        if (exposure < getMinExposure()) {
            exposure = getMinExposure();
        } else if (exposure > getMaxExposure()) {
            exposure = getMaxExposure();
        }
        if (this.exposure != exposure) {
            // check hardware interface exists and is PSEye camera
            if (checkHardware()) {
                PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
                try {
                    this.exposure = pseye.setExposure(exposure);
                } catch (Exception ex) {
                   log.warning(ex.toString());
                }
            }
            else
                this.exposure = exposure;
            
            setChanged();            
            notifyObservers(EVENT.EXPOSURE);
        }
    }

    public boolean isAutoGain() {
        if (!getSupportsAutoGain()) 
            return false;
            
        // check hardware interface exists and is PSEye camera
        if (checkHardware()) {
            PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
            autoGainEnabled = pseye.isAutoGain();
        }
        return autoGainEnabled; 
    }

    /**
     * @param autoGainEnabled the autoGainEnabled to set
     */
    public void setAutoGain(boolean autoGainEnabled) {
        autoGainEnabled = getSupportsAutoGain() && autoGainEnabled;
        if (this.autoGainEnabled != autoGainEnabled) {
            // check hardware interface exists and is PSEye camera
            if (checkHardware()) {
                PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
                try {
                    this.autoGainEnabled = pseye.setAutoGain(autoGainEnabled);
                } catch (Exception ex) {
                   log.warning(ex.toString());
                }
            }
            else
                this.autoGainEnabled = autoGainEnabled;

            setChanged();
            notifyObservers(EVENT.AUTO_GAIN);
        }
    }

    /**
     * @return the autoExposureEnabled
     */
    public boolean isAutoExposure() {
        if (!getSupportsAutoExposure()) 
            return false;
            
        // check hardware interface exists and is PSEye camera
        if (checkHardware()) {
            PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
            autoExposureEnabled = pseye.isAutoExposure();
        }
        return autoExposureEnabled;            
    }

    /**
     * @param autoExposureEnabled the autoExposureEnabled to set
     */
    public void setAutoExposure(boolean autoExposureEnabled) {
        autoExposureEnabled = getSupportsAutoExposure() && autoExposureEnabled;
        if (this.autoExposureEnabled != autoExposureEnabled) {
            // check hardware interface exists and is PSEye camera
            if (checkHardware()) {
                PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
                try {
                    this.autoExposureEnabled = pseye.setAutoExposure(autoExposureEnabled);
                } catch (Exception ex) {
                   log.warning(ex.toString());
                }
            }
            else
                this.autoExposureEnabled = autoExposureEnabled;

            setChanged();
            notifyObservers(EVENT.AUTO_EXPOSURE);
        }
    }

    /** Returns cameras mode of operation, if the hardware interface is open, else returns null.
     * 
     * @return camera mode or null
     */
    public PSEyeCamera.Mode getMode() {
        // check hardware interface exists and is PSEye camera
        if (checkHardware()) {
            PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
            mode = pseye.getMode();
        }
        return mode;
    }

    /** Sets the camera mode. Maybe need to stop and start camera to activate new mode. 
     * 
     * @param mode desired new mode.
     */
    synchronized public void setMode(PSEyeCamera.Mode mode) throws HardwareInterfaceException {
        if (!getSupportedModes().contains(mode)) {
            return;
        }
        
        if (mode != getMode()) {
            // check hardware interface exists and is PSEye camera
            if (checkHardware()) {
                PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
                this.mode = pseye.setMode(mode);
            }
            else
                this.mode = mode; 
            
            setChanged();            
            notifyObservers(EVENT.MODE);
        }
    }
    
    public PSEyeCamera.Resolution getResolution() {
        // check hardware interface exists and is PSEye camera
        if (checkHardware()) {
            PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
            resolution = pseye.getResolution();
        }
        return resolution;
    }

    public void resolutionChange() {
        switch (resolution) {
            case VGA:
                setSizeX(640);
                setSizeY(480);
                break;
            case QVGA:
                setSizeX(320);
                setSizeY(240);
                break;
        }
        if (getEventExtractor() != null && (getEventExtractor() instanceof PSEyeEventExtractor))
            ((PSEyeEventExtractor) getEventExtractor()).reset();
    }
    
    synchronized public void setResolution(PSEyeCamera.Resolution resolution) throws HardwareInterfaceException {
        if (!getSupportedResolutions().contains(resolution)) {
            return;
        }
        
        if (resolution != getResolution()) {
            int fr;
            // check hardware interface exists and is PSEye camera
            if (checkHardware()) {
                PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
                this.resolution = pseye.setResolution(resolution);
                // get framerate as may have changed due to support
                fr = pseye.getFrameRate();
            }
            else {
                this.resolution = resolution;
                // get framerate as may have changed due to support
                fr = getClosestFrameRate(frameRate);
            }

            // check for change in frameRate
            if (fr != frameRate) {
                frameRate = fr;
                setChanged();
                notifyObservers(EVENT.FRAMERATE);
            }
            
            resolutionChange();    
            setChanged();
            notifyObservers(EVENT.RESOLUTION);
        }
        
        this.frameRate = getFrameRate();
    }
    
    public int getFrameRate() {
        // check hardware interface exists and is PSEye camera
        if (checkHardware()) {
            PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
            frameRate = pseye.getFrameRate();
        }
        return frameRate;
    }   
    
    /* Get the nearest supported frame rate above that passed */
    private int getClosestFrameRate(int frameRate) {
        Integer[] frameRates = getSupportedFrameRates().get(resolution).toArray(new Integer[0]);
        Arrays.sort(frameRates);
        int index = Arrays.binarySearch(frameRates, frameRate);
        if (index >= 0) {
            return frameRates[index];
        }
        else {
            index = -(index + 1);
            return index < frameRates.length ? frameRates[index] : frameRates[index - 1];
        } 
    }
    
    synchronized public void setFrameRate(int frameRate) throws HardwareInterfaceException {
        frameRate = getClosestFrameRate(frameRate);
        
        if (frameRate != getFrameRate()) {
            // check hardware interface exists and is PSEye camera
            if (checkHardware()) {
                PSEyeCamera pseye = (PSEyeCamera) getHardwareInterface();
                this.frameRate = pseye.setFrameRate(frameRate);;
            }
            else
                this.frameRate = frameRate;

            setChanged();
            notifyObservers(EVENT.FRAMERATE);
        }
    }    

    /* 
     * Getter functions to wrap supported parameters 
     */
    public ArrayList<PSEyeCamera.Mode> getSupportedModes() {
        return PSEyeCamera.supportedModes;
    }

    public ArrayList<PSEyeCamera.Resolution> getSupportedResolutions() {
        return PSEyeCamera.supportedResolutions;
    } 

    public EnumMap<PSEyeCamera.Resolution, List<Integer>> getSupportedFrameRates() {
        return PSEyeCamera.supportedFrameRates;
    }
    
    public int getMinGain() {
        return PSEyeConstants.MIN_GAIN;
    }
    
    public int getMaxGain() {
        return PSEyeConstants.MAX_GAIN;
    }
    
    public int getMinExposure() {
        return PSEyeConstants.MIN_EXPOSURE;
    }
    
    public int getMaxExposure() {
        return PSEyeConstants.MAX_EXPOSURE;
    }
    
    public boolean getSupportsAutoGain () {
        return supportsAutoGain;
    }
    
    public boolean getSupportsAutoExposure () {
        return supportsAutoExposure;
    }

    @Override
    public AEFileInputStream constuctFileInputStream(File file) throws IOException {
        return new PSEyeCameraFileInputStream(file);
    }    
}
