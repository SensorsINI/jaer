/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.eye;

import ch.unizh.ini.jaer.chip.dollbrain.ColorEvent;
import ch.unizh.ini.jaer.chip.dvs320.cDVSEvent;
import ch.unizh.ini.jaer.projects.thresholdlearner.TemporalContrastEvent;
import cl.eye.CLCamera.CameraMode;
import cl.eye.CLCamera.InvalidParameterException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import javax.swing.JPanel;
import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import org.jdesktop.beansbinding.Validator;

/**
 * A behavioral model of an AE retina using the code laboratories interface to a PS eye camera.
 * 
 * @author tobi
 */
@Description("AE retina using the PS eye camera")
public class PSEyeCLModelRetina extends AEChip {

    // table lookup for log from int 8 bit pixel value
    private static int[] logMapping = null;
    private int[] lastBrightnessValues = null, lastHueValues = null;
    // camera values
    private int gain = getPrefs().getInt("gain", 30);
    private int exposure = getPrefs().getInt("exposure", 511);
    private boolean autoGainEnabled = getPrefs().getBoolean("autoGainEnabled", true);
    private boolean autoExposureEnabled = getPrefs().getBoolean("autoExposureEnabled", true);
    /* added into CLCamera.cameraMode
    private int frameRate = getPrefs().getInt("frameRate", 120);
     */
    //
    //
    // for brightness change
    private int brightnessChangeThreshold = getPrefs().getInt("brightnessChangeThreshold", 10);
    // whether we use log intensity change or linear change
    private boolean logIntensityMode = getPrefs().getBoolean("logIntensityMode", false);
    // for hue change
    private int hueChangeThreshold = getPrefs().getInt("hueChangeThreshold", 20);
    /** Observable events; This event is fired when the parameter is changed. */
    public static final String EVENT_HUE_CHANGE_THRESHOLD = "hueChangeThreshold",
            EVENT_BRIGHTNESS_CHANGE_THRESHOLD = "brightnessChangeThreshold",
            EVENT_LOG_INTENSITY_MODE = "logIntensityMode",
            EVENT_LINEAR_INTERPOLATE_TIMESTAMP = "linearInterpolateTimeStamp",
            EVENT_GAIN = "gain",
            EVENT_EXPOSURE = "exposure",
            EVENT_AUTO_GAIN = "autoGain",
            EVENT_AUTOEXPOSURE = "autoExposure",
            EVENT_CAMERA_MODE = "cameraMode";
    //      
    //        
    private boolean initialized = false; // used to avoid writing events for all pixels of first frame of data
    private boolean linearInterpolateTimeStamp = getPrefs().getBoolean("linearInterpolateTimeStamp", false);
    private int lastEventTimeStamp;
//    private PolarityEvent tempEvent = new PolarityEvent();
    private BasicEvent tempEvent = new BasicEvent();
    private ArrayList<Integer> discreteEventCount = new ArrayList<Integer>();
    private boolean colorMode = false;
    //
    // validators for int values, used in bindings to GUI
    private ByteValueValidator gainValidator = new ByteValueValidator(CLCamera.CLEYE_MAX_GAIN);
    private ByteValueValidator exposureValidator = new ByteValueValidator(CLCamera.CLEYE_MAX_EXPOSURE);

    public PSEyeCLModelRetina() {
        setSizeX(320);
        setSizeY(240);
        lastBrightnessValues = new int[sizeX * sizeY];
        lastHueValues = new int[sizeX * sizeY];
        setEventExtractor(new EventExtractor(this));
        setEventClass(PolarityEvent.class);
        setBiasgen(new Controls(this));
    }

    @Override
    public void setHardwareInterface(HardwareInterface hardwareInterface) {
        super.setHardwareInterface(hardwareInterface);
        if (hardwareInterface != null && (hardwareInterface instanceof CLRetinaHardwareInterface)) {
            try {
                CLRetinaHardwareInterface hw = (CLRetinaHardwareInterface) hardwareInterface;
                colorMode = (hw.getCameraMode().color == CLCamera.CLEYE_COLOR_PROCESSED); // sets whether input is color or not
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
        }
    }

    public void sendConfiguration() {
        HardwareInterface hardwareInterface = getHardwareInterface();
        if ((hardwareInterface != null) && hardwareInterface.isOpen() && (hardwareInterface instanceof CLRetinaHardwareInterface)) {
            try {
                CLRetinaHardwareInterface hw = (CLRetinaHardwareInterface) hardwareInterface;
                hw.setGain(gain);
                hw.setExposure(exposure);
                hw.setAutoExposure(autoExposureEnabled);
                hw.setAutoGain(autoGainEnabled);
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
        }
    }

    /**
     * @return the hueChangeThreshold
     */
    public int getHueChangeThreshold() {
        return hueChangeThreshold;
    }

    /**
     * @param hueChangeThreshold the hueChangeThreshold to set
     */
    public void setHueChangeThreshold(int hueChangeThreshold) {
        if (hueChangeThreshold < 1) {
            hueChangeThreshold = 1;
        } else if (hueChangeThreshold > 255) {
            hueChangeThreshold = 255;
        }
        if (this.hueChangeThreshold != hueChangeThreshold) {
            setChanged();
        }
        this.hueChangeThreshold = hueChangeThreshold;
        getPrefs().putInt("hueChangeThreshold", hueChangeThreshold);
        notifyObservers(EVENT_HUE_CHANGE_THRESHOLD);
    }

    /**
     * @return the logIntensityMode
     */
    public boolean isLogIntensityMode() {
        return logIntensityMode;
    }

    /**
     * @param logIntensityMode the logIntensityMode to set
     */
    public void setLogIntensityMode(boolean logIntensityMode) {
        if (this.logIntensityMode != logIntensityMode) {
            setChanged();
        }
        this.logIntensityMode = logIntensityMode;
        getPrefs().putBoolean("logIntensityMode", logIntensityMode);
        notifyObservers(EVENT_LOG_INTENSITY_MODE);
    }

    public class Controls extends Biasgen {

        public Controls(Chip chip) {
            super(chip);
        }

        @Override
        public JPanel buildControlPanel() {
            return new CLCameraControlPanel(PSEyeCLModelRetina.this);
        }

        @Override
        public void open() throws HardwareInterfaceException {
            super.open();
            PSEyeCLModelRetina.this.sendConfiguration();

        }
    }

    public class EventExtractor extends TypedEventExtractor<TemporalContrastEvent> {

        public EventExtractor(AEChip aechip) {
            super(aechip);
        }

        // TODO logged events cannot be read in here since they are not complete frames anymore!
        /** Extracts events from the raw camera frame data that is supplied in the input packet.
         * Events are extracted according to the camera operating mode.
         * <p>
         * <ul>
         * <li> If the mode is mono, only {@link PolarityEvent} are output. 
         * <li>If the mode is color, then {@link cDVSEvent} are output, reporting both intensity and color change.
         * </ul>
         * @param in in raw input packet from the CLCamera holding intensity/color pixel RGB values
         * @param out the output event packet holding cooked events
         */
        @Override
        public synchronized void extractPacket(AEPacketRaw in, EventPacket out) {
            out.allocate(chip.getNumPixels());
            int[] pixVals = in.getAddresses(); // pixel RGB values stored here by hardware interface
            int ts = in.getTimestamps()[0]; // timestamps stored here, currently only first timestamp meaningful TODO multiple frames stored here
            int eventTimeDelta = ts - lastEventTimeStamp;
            OutputEventIterator itr = out.outputIterator();
            if (linearInterpolateTimeStamp) {
                discreteEventCount.clear();
            }
            int sx = getSizeX(), sy = getSizeY(), i = 0, j = 0;
            int pixval = 0, n = 0, diff = 0, lastval = 0;
            float pixR = 0, pixB = 0;
            colorMode=getCameraMode().color==CLCamera.CLEYE_COLOR_PROCESSED;
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++) {
                    if (colorMode) { // set by setHardwareInterface
                        if (out.getEventClass() != cDVSEvent.class) {
                            out.setEventClass(cDVSEvent.class);
                        }
                        final int RMASK = 0xff0000;
                        final int GMASK = 0x00ff00;
                        final int BMASK = 0x0000ff; // colors packed like this into each int when in color mode
                        pixR = (float) ((pixVals[i] >> 16) & 0xff);
                        pixB = (float) (pixVals[i] & 0xff);
                        pixval = 255;
                        if (pixR > 0 || pixB > 0) { // compute mean wavelength value from RGB
                            // Here we define the "mean color" as the magnitude of the difference between red and blue, scaled to 0-1.
                            // Then if the color difference changes, we get events. If the difference becomes larger, we get "bigger color difference"
                            // events, if the difference becomes smaller, we get "smaller color difference" events.
                            // Not exactly mean wavelength.
                            //  1-2(rb)/(r^2+b^2) =(b-r)^2/(r^2+b^2) which is somehow a measure of mean wavelength.
                            // This is the magnitude of the difference between the red and blue value scaled to a value between 0 and 1
                            pixval = (int) (255 - 510 * (pixR * pixB) / (pixR * pixR + pixB * pixB));
                        }
                    } else {
                        if (out.getEventClass() != PolarityEvent.class) {
                            out.setEventClass(PolarityEvent.class);
                        }
                        pixval = pixVals[i] & 0xff; // get gray value 0-255
                    }
                    if (!initialized) {
                        lastBrightnessValues[i] += pixval; // update stored gray level for first frame

                    } else {
                        lastval = lastBrightnessValues[i];
                        diff = pixval - lastval;
                        if (diff > brightnessChangeThreshold) { // if our gray level is sufficiently higher than the stored gray level
                            n = diff / brightnessChangeThreshold;
                            for (j = 0; j < n; j++) { // use down iterator as ensures latest timestamp as last event
                                BasicEvent e = (BasicEvent) itr.nextOutput();
                                e.x = (short) x;
                                e.y = (short) (sy - y - 1); // flip y according to jAER with 0,0 at LL
                                if (!colorMode) {
                                    PolarityEvent pe = (PolarityEvent) e;
                                    pe.type = 1;  // ON type
                                    pe.setPolarity(PolarityEvent.Polarity.On);
                                } else {
                                    cDVSEvent ce = (cDVSEvent) e;
                                    ce.eventType = cDVSEvent.EventType.Bluer;
                                }
                                if (linearInterpolateTimeStamp) {
                                    e.timestamp = ts - j * eventTimeDelta / n;
                                    orderedLastSwap(out, j);
                                } else {
                                    e.timestamp = ts;
                                }

                            }
                            lastBrightnessValues[i] += brightnessChangeThreshold * n; // update stored gray level by events
                        } else if (diff < -brightnessChangeThreshold) {
                            n = -diff / brightnessChangeThreshold;
                            for (j = 0; j < n; j++) {
                                BasicEvent e = (BasicEvent) itr.nextOutput();
                                e.x = (short) x;
                                e.y = (short) (sy - y - 1);
                                if (!colorMode) {
                                    PolarityEvent pe = (PolarityEvent) e;
                                    pe.type = 0;
                                    pe.setPolarity(PolarityEvent.Polarity.Off);
                                } else {
                                    cDVSEvent ce = (cDVSEvent) e;
                                    ce.eventType = cDVSEvent.EventType.Redder;
                                }
                                if (linearInterpolateTimeStamp) {
                                    e.timestamp = ts - j * eventTimeDelta / n;
                                    orderedLastSwap(out, j);
                                } else {
                                    e.timestamp = ts;
                                }

                            }
                            lastBrightnessValues[i] -= brightnessChangeThreshold * n;
                        }
                    }

                    i++;
                }
            }
            initialized = true;
            lastEventTimeStamp = ts;
        }
    }

    /*
     * Used to reorder event packet using interpolated time step.
     * Much faster then using Arrays.sort on event packet
     */
    private void orderedLastSwap(EventPacket out, int timeStep) {
        while (discreteEventCount.size() <= timeStep) {
            discreteEventCount.add(0);
        }
        discreteEventCount.set(timeStep, discreteEventCount.get(timeStep) + 1);
        if (timeStep > 0) {
            int previousStepCount = 0;
            for (int i = 0; i < timeStep; i++) {
                previousStepCount += (int) discreteEventCount.get(i);
            }
            int size = out.getSize() - 1;
            swap(out, size, size - previousStepCount);
        }
    }

    /*
     * Exchange positions of two events in packet
     */
    private void swap(EventPacket out, int index1, int index2) {
        BasicEvent[] elementData = out.getElementData();
        tempEvent = elementData[index1];
        elementData[index1] = elementData[index2];
        elementData[index2] = tempEvent;

        /*  Old code - written as unsure about Java value/reference management 
         * (i.e. didn't want to create large local copies of element data).
        PolarityEvent e1 = (PolarityEvent) out.getEvent(index1);
        PolarityEvent e2 = (PolarityEvent) out.getEvent(index2);
        tempEvent.copyFrom(e1);
        e1.copyFrom(e2);
        e2.copyFrom(tempEvent);
         */
    }

    /**
     * Get the value of gain
     *
     * @return the value of gain
     */
    public int getGain() {
        return gain;
    }

    /**
     * Set the value of gain
     *
     * @param gain new value of gain
     */
    public void setGain(int gain) {
        if (gain < 1) {
            gain = 1;
        } else if (gain > CLCamera.CLEYE_MAX_GAIN) {
            gain = CLCamera.CLEYE_MAX_GAIN;
        }
        if (this.gain != gain) {
            setChanged();
        }
        this.gain = gain;
        getPrefs().putInt("gain", gain);
        sendConfiguration();
        notifyObservers(EVENT_GAIN);
    }

    /**
     * @return the exposure
     */
    public int getExposure() {
        return exposure;
    }

    /**
     * @param exposure the exposure to set
     */
    public void setExposure(int exposure) {
        if (exposure < 1) {
            exposure = 1;
        } else if (exposure > CLCamera.CLEYE_MAX_EXPOSURE) {
            exposure = CLCamera.CLEYE_MAX_EXPOSURE;
        }
        if (this.exposure != exposure) {
            setChanged();
        }
        this.exposure = exposure;
        getPrefs().putInt("exposure", exposure);
        sendConfiguration();
        notifyObservers(EVENT_EXPOSURE);
    }

    /**
     * @return the frameRate
     */
    /* removed by mlk as not runtime changable
    public int getFrameRate() {
    return frameRate;
    }
     */
    /**
     * @param frameRate the frameRate to set
     */
    /* removed by mlk as not runtime changable
    public void setFrameRate(int frameRate) {
    this.frameRate = frameRate;
    getPrefs().putInt("frameRate", frameRate);
    HardwareInterface hardwareInterface = getHardwareInterface();
    if (hardwareInterface != null && (hardwareInterface instanceof CLRetinaHardwareInterface)) {
    try {
    CLRetinaHardwareInterface hw = (CLRetinaHardwareInterface) hardwareInterface;
    hw.setFrameRateHz(frameRate);
    } catch (Exception ex) {
    log.warning(ex.toString());
    }
    }
    }
     */
    /**
     * @return the autoGainEnabled
     */
    public boolean isAutoGainEnabled() {
        return autoGainEnabled;
    }

    /**
     * @param autoGainEnabled the autoGainEnabled to set
     */
    public void setAutoGainEnabled(boolean autoGainEnabled) {
        if (this.autoGainEnabled != autoGainEnabled) {
            setChanged();
        }
        this.autoGainEnabled = autoGainEnabled;
        getPrefs().putBoolean("autoGainEnabled", autoGainEnabled);
        sendConfiguration();
        notifyObservers(EVENT_AUTO_GAIN);
    }

    /**
     * @return the autoExposureEnabled
     */
    public boolean isAutoExposureEnabled() {
        return autoExposureEnabled;
    }

    /**
     * @param autoExposureEnabled the autoExposureEnabled to set
     */
    public void setAutoExposureEnabled(boolean autoExposureEnabled) {
        if (this.autoExposureEnabled != autoExposureEnabled) {
            setChanged();
        }
        this.autoExposureEnabled = autoExposureEnabled;
        getPrefs().putBoolean("autoExposureEnabled", autoExposureEnabled);
        sendConfiguration();
        notifyObservers(EVENT_AUTOEXPOSURE);
    }

    /**
     * @return the brightnessChangeThreshold
     */
    public int getBrightnessChangeThreshold() {
        return brightnessChangeThreshold;
    }

    /**
     * @param brightnessChangeThreshold the brightnessChangeThreshold to set
     */
    public void setBrightnessChangeThreshold(int brightnessChangeThreshold) {
        if (brightnessChangeThreshold < 1) {
            brightnessChangeThreshold = 1;
        } else if (brightnessChangeThreshold > 255) {
            brightnessChangeThreshold = 255;
        }
        if (this.brightnessChangeThreshold != brightnessChangeThreshold) {
            setChanged();
        }
        this.brightnessChangeThreshold = brightnessChangeThreshold;
        getPrefs().putInt("brightnessChangeThreshold", brightnessChangeThreshold);
        notifyObservers(EVENT_BRIGHTNESS_CHANGE_THRESHOLD);
    }

    /**
     * @return whether using linear interpolation of TimeStamps
     */
    public boolean isLinearInterpolateTimeStamp() {
        return linearInterpolateTimeStamp;
    }

    /**
     * @param linearInterpolateTimeStamp 
     */
    public void setLinearInterpolateTimeStamp(boolean linearInterpolateTimeStamp) {
        if (this.linearInterpolateTimeStamp != linearInterpolateTimeStamp) {
            setChanged();
        }
        this.linearInterpolateTimeStamp = linearInterpolateTimeStamp;
        getPrefs().putBoolean("linearInterpolateTimeStamp", linearInterpolateTimeStamp);
        notifyObservers(EVENT_LINEAR_INTERPOLATE_TIMESTAMP);
    }

    /** Returns cameras mode of operation, if the hardware interface is open, else returns null.
     * 
     * @return camera mode or null
     */
    public CameraMode getCameraMode() {
        if (getHardwareInterface() == null || !(getHardwareInterface() instanceof CLCamera)) {
            return null;
        } else {
            CLCamera cl = (CLCamera) getHardwareInterface();
            return cl.getCameraMode();
        }
    }

    /** Sets the camera mode. Maybe need to stop and start camera to activate new mode. 
     * 
     * @param mode desired new mode.
     */
    public void setCameraMode(CameraMode mode) throws HardwareInterfaceException {
        if (getHardwareInterface() == null || !(getHardwareInterface() instanceof CLCamera)) {
            return;
        }
        CLCamera cl = (CLCamera) getHardwareInterface();
        if (cl.getCameraMode() != mode) {
            setChanged();
        }
        cl.setCameraMode(mode);
        notifyObservers(EVENT_CAMERA_MODE);
    }

    public class ByteValueValidator extends Validator {

        private int max;

        /** Constructs a new instance with specified max value allowed.
         * 
         * @param max max allowed value.
         */
        public ByteValueValidator(int max) {
            this.max = max;
        }

        /** Returns null if input is OK, otherwise the error message.
         * 
         * @param arg a String 
         * @return null if OK (1 to max), otherwise a message
         */
        public Validator.Result validate(Object arg) {
            if (!(arg instanceof String)) {
                return null;
            }
            try {
                int i = Integer.parseInt((String) arg);
                if (i > 1 && i <= max) {
                    return null;
                } else {
                    return new Result(null, "Value range is 1-" + max);
                }
            } catch (Exception e) {
                return new Result(null, "bad value: " + arg + "; allowed range is 1-" + max);
            }
        }
    }
}
