/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.eye;

import ch.unizh.ini.jaer.chip.dvs320.cDVSEvent;
import ch.unizh.ini.jaer.projects.thresholdlearner.TemporalContrastEvent;
import cl.eye.CLCamera.CameraMode;
import java.io.File;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.ArrayList;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.JPanel;
import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import org.jdesktop.beansbinding.Validator;

/**
 * A behavioral model of several AE retina models using the code laboratories interface to a PS3-Eye camera.
 * 
 * @author Tobi Delbruck and Mat Lubelski
 */
@Description("AE retina using the PS-Eye Playstation camera")
public class PSEyeCLModelRetina extends AEChip implements PreferenceChangeListener {

    /**
     * @return the retinaModel
     */
    public RetinaModel getRetinaModel() {
        return retinaModel;
    }

    /**
     * @param retinaModel the retinaModel to set
     */
    public void setRetinaModel(RetinaModel retinaModel) {
        if (this.retinaModel != retinaModel) {
            setChanged();
            log.info("setting retinaModel=" + retinaModel);
            this.retinaModel = retinaModel;
        }
        notifyObservers(EVENT_RETINA_MODEL);
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent evt) {
        getBiasgen().loadPreferences();
    }

    /** Possible models of retina computation.
     * 
     */
    public enum RetinaModel {

        RatioBtoRG("Uses change of ratio of R/(B+G)"),
        DifferenceRB("Uses normalized absolute difference (B-R)^2/(R^2+B^2)"),
        MeanWaveLength("Uses mean wavelength (lr * pixR + lg * pixG + lb * pixB) / sum, where lx is the mean filter wavelength");
        String description;

        RetinaModel(String description) {
            this.description = description;
        }
    };
    private RetinaModel retinaModel = null;
    /** Number of gray levels in camera samples. */
    public static final int NLEVELS = 256;
    /** Number of samples levels to leave linear when using log mapping. */
    public static final int NLINEAR = 10;
    // table lookup for log from int 8 bit pixel value
    private int[] logMapping = new int[NLEVELS];
    // array of memorized brightness and hue values
    private int[] lastBrightnessValues = null, lastHueValues = null;
    // array of per-pixel threshold mismatch values. These are ints because pixel values are ints, even the linlog ones which are floored to ints
    private int[] sigmaThresholds = null;
    // array of last event times for each pixel, for use in emitting background events. We initialize this to a random number 
    // in the frame interval to avoid synchrounous bursts of background events
    private int[] lastEventTimes = null;
    // camera values
    private int gain;
    private int exposure;
    private boolean autoGainEnabled;
    private boolean autoExposureEnabled;
    // statistical parameters
    private float sigmaThreshold;
    private float backgroundEventRatePerPixelHz;
    private Random bgRandom = new Random();
    // for brightness change
    private int brightnessChangeThreshold ;
    // whether we use log intensity change or linear change
    private boolean logIntensityMode ;
    // for log mode, where the lin-log transisition sample value is
    private int linLogTransitionValue;
    // for hue change
    private int hueChangeThreshold ;
    /** Observable events; This event is fired when the parameter is changed. */
    public static final String EVENT_HUE_CHANGE_THRESHOLD = "hueChangeThreshold",
            EVENT_BRIGHTNESS_CHANGE_THRESHOLD = "brightnessChangeThreshold",
            EVENT_LOG_INTENSITY_MODE = "logIntensityMode",
            EVENT_LINEAR_INTERPOLATE_TIMESTAMP = "linearInterpolateTimeStamp",
            EVENT_GAIN = CLCamera.EVENT_GAIN,
            EVENT_EXPOSURE = CLCamera.EVENT_EXPOSURE,
            EVENT_AUTO_GAIN = CLCamera.EVENT_AUTOGAIN,
            EVENT_AUTOEXPOSURE = CLCamera.EVENT_AUTOEXPOSURE,
            EVENT_CAMERA_MODE = CLCamera.EVENT_CAMERA_MODE,
            EVENT_RETINA_MODEL = "retinaModel",
            EVENT_LINLOG_TRANSITION_VALUE = "linLogTransitionValue",
            EVENT_SIGMA_THRESHOLD = "sigmaThreshold",
            EVENT_BACKGROUND_EVENT_RATE = "backgroundEventRatePerPixelHz";
    //      
    //        
    private boolean initialized = false; // used to avoid writing events for all pixels of first frame of data
    private boolean linearInterpolateTimeStamp;
    private int lastFrameTimestamp;
//    private PolarityEvent tempEvent = new PolarityEvent();
    private BasicEvent tempEvent = new BasicEvent();
    private ArrayList<Integer> discreteEventCount = new ArrayList<Integer>();
    private boolean colorMode = false;
    //
    // validators for int values, meant to be used in bindings to GUI but not used yet in GUI.
    private ByteValueValidator gainValidator = new ByteValueValidator(CLCamera.CLEYE_MAX_GAIN);
    private ByteValueValidator exposureValidator = new ByteValueValidator(CLCamera.CLEYE_MAX_EXPOSURE);
    PSEyeModelRetinaRenderer renderer = null;
    private Observer cameraObserver; // observes updates from the camera

    public PSEyeCLModelRetina() {
        setSizeX(320);
        setSizeY(240);
        setNumCellTypes(4);
        setName("PSEyeCLModelRetina");
        lastBrightnessValues = new int[sizeX * sizeY];
        lastHueValues = new int[sizeX * sizeY];
        setEventExtractor(new EventExtractor(this));
        setEventClass(cDVSEvent.class);
        setBiasgen(new Controls(this));

        setRenderer((renderer = new PSEyeModelRetinaRenderer(this)));
        fillLogMapping();
        fillSigmaThresholds();
        lastEventTimes = new int[getNumPixels()];
        Random r = new Random();
        for (int i = 0; i < lastEventTimes.length; i++) {
            lastEventTimes[i] = r.nextInt(16000); // initialize to random time in 16ms
        }
        r = null;
        getPrefs().addPreferenceChangeListener(this);
        loadPreferences();
        cameraObserver = new Observer() {

            @Override
            public void update(Observable o, Object arg) {
                log.info(o + " sent " + arg);
                if (o != null && getHardwareInterface() != null && (o instanceof CLCamera) && arg != null) {
                    CLRetinaHardwareInterface hw = (CLRetinaHardwareInterface) getHardwareInterface();
                    if (arg == CLCamera.EVENT_AUTOEXPOSURE) {
                        setAutoExposureEnabled(hw.isAutoExposure());
                    } else if (arg == CLCamera.EVENT_AUTOGAIN) {
                        setAutoGainEnabled(hw.isAutoGain());
                    } else if (arg == CLCamera.EVENT_CAMERA_MODE) {
                        try {
                            setCameraMode(hw.getCameraMode());
                        } catch (HardwareInterfaceException ex) {
                            log.warning(ex.toString());
                        }
                    } else if (arg == CLCamera.EVENT_EXPOSURE) {
                        setExposure(hw.getExposure());
                    } else if (arg == CLCamera.EVENT_GAIN) {
                        setGain(hw.getGain());
                    } else {
                        log.warning(o + " sent unknown event " + arg);
                    }
                }
            }
        };
    }

    public void loadPreferences() {
        setGain(getPrefs().getInt("PSEyeModelRetina.gain", 30));
        setExposure(getPrefs().getInt("PSEyeModelRetina.exposure", 511));
        setAutoGainEnabled(getPrefs().getBoolean("PSEyeModelRetina.autoGainEnabled", true));
        setAutoExposureEnabled(getPrefs().getBoolean("PSEyeModelRetina.autoExposureEnabled", true));
        setSigmaThreshold(getPrefs().getFloat("PSEyeModelRetina.sigmaThreshold", 0));
        setBackgroundEventRatePerPixelHz(getPrefs().getFloat("PSEyeModelRetina.backgroundEventRatePerPixelHz", 0));
        setBrightnessChangeThreshold(getPrefs().getInt("PSEyeModelRetina.brightnessChangeThreshold", 10));
        setLogIntensityMode(getPrefs().getBoolean("PSEyeModelRetina.logIntensityMode", false));
        setLinLogTransitionValue(getPrefs().getInt("PSEyeModelRetina.linLogTransitionValue", 15));
        setHueChangeThreshold(getPrefs().getInt("PSEyeModelRetina.hueChangeThreshold", 20));
        setLinearInterpolateTimeStamp(getPrefs().getBoolean("PSEyeModelRetina.linearInterpolateTimeStamp", false));
        String defModel = RetinaModel.RatioBtoRG.toString();
        try {
            setRetinaModel(RetinaModel.valueOf(getPrefs().get("PSEyeModelRetina.retinaModel", defModel)));
        } catch (Exception e) {
            setRetinaModel(RetinaModel.RatioBtoRG);
        }
    }

    public void storePreferences() {
        getPrefs().putInt("PSEyeModelRetina.gain", gain);
        getPrefs().putInt("PSEyeModelRetina.exposure", exposure);
        getPrefs().putBoolean("PSEyeModelRetina.autoGainEnabled", autoGainEnabled);
        getPrefs().putBoolean("PSEyeModelRetina.autoExposureEnabled", autoExposureEnabled);
        getPrefs().putFloat("PSEyeModelRetina.sigmaThreshold", sigmaThreshold);
        getPrefs().putFloat("PSEyeModelRetina.backgroundEventRatePerPixelHz", backgroundEventRatePerPixelHz);
        getPrefs().putInt("PSEyeModelRetina.brightnessChangeThreshold", brightnessChangeThreshold);
        getPrefs().putInt("PSEyeModelRetina.hueChangeThreshold", hueChangeThreshold);
        getPrefs().putBoolean("PSEyeModelRetina.logIntensityMode", logIntensityMode);
        getPrefs().putInt("PSEyeModelRetina.linLogTransitionValue", linLogTransitionValue);
        getPrefs().putBoolean("PSEyeModelRetina.linearInterpolateTimeStamp", linearInterpolateTimeStamp);
        getPrefs().put("PSEyeModelRetina.retinaModel", retinaModel.toString());
    }

    @Override
    public void setHardwareInterface(HardwareInterface hardwareInterface) {
        if (hardwareInterface != null && (hardwareInterface instanceof CLRetinaHardwareInterface)) {
//        super.setHardwareInterface(hardwareInterface);

            try {
                if (this.hardwareInterface != null && (this.hardwareInterface instanceof CLRetinaHardwareInterface)) {
                    ((CLRetinaHardwareInterface) hardwareInterface).deleteObserver(cameraObserver);
                }
                super.setHardwareInterface(hardwareInterface);
                CLRetinaHardwareInterface hw = (CLRetinaHardwareInterface) hardwareInterface;
                hw.addObserver(cameraObserver); // we update our state depending on how camera is setup.
                hw.setCameraMode(getCameraMode());
                colorMode = (hw.getCameraMode().color == CLCamera.CLEYE_COLOR_PROCESSED); // sets whether input is color or not
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
        } else {
            log.warning("tried to set HardwareInterface to not a CLRetinaHardwareInterface: " + hardwareInterface);
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
    synchronized public void setHueChangeThreshold(int hueChangeThreshold) {
        if (hueChangeThreshold < 1) {
            hueChangeThreshold = 1;
        } else if (hueChangeThreshold > NLEVELS) {
            hueChangeThreshold = NLEVELS;
        }
        if (this.hueChangeThreshold != hueChangeThreshold) {
            setChanged();
            this.hueChangeThreshold = hueChangeThreshold;
        }
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
            this.logIntensityMode = logIntensityMode;
        }
        notifyObservers(EVENT_LOG_INTENSITY_MODE);
    }

    /**
     * @return the linLogTransitionValue
     */
    public int getLinLogTransitionValue() {
        return linLogTransitionValue;
    }

    /**
     * @param linLogTransitionValue the linLogTransitionValue to set
     */
    public void setLinLogTransitionValue(int linLogTransitionValue) {
        if (linLogTransitionValue < 1) {
            linLogTransitionValue = 1;
        } else if (linLogTransitionValue > NLEVELS) {
            linLogTransitionValue = NLEVELS;
        }
        if (this.linLogTransitionValue != linLogTransitionValue) {
            setChanged();
            this.linLogTransitionValue = linLogTransitionValue;
        }
        fillLogMapping();
        notifyObservers(EVENT_LINLOG_TRANSITION_VALUE);
    }

    /** Returns color mode
     * 
     * @return true if running color mode on PS-Eye 
     */
    public boolean isColorMode() {
        return colorMode;
    }

    /** If logIntensityMode is active, returns value according to
     * <p>
     * <img src="doc-files/logmapping.png"/>
     * @param v
     * @return mapped value, or identity if not log mapping
     */
    public int map2linlog(int v) {
        if (!logIntensityMode) {
            return v;
        }

        if (v < 0) {
            v = 0;
        } else if (v > NLEVELS - 1) {
            v = NLEVELS - 1;
        }
        return logMapping[v];
    }

    private void fillLogMapping() {
        // fill up lookup table to compute log from 8 bit sample value
        // the first linpart values are linear, then the rest are log, so that it ends up mapping 0:255 to 0:255
        for (int i = 0; i < linLogTransitionValue; i++) {
            logMapping[i] = i;
        }
        double a = ((double) NLEVELS - linLogTransitionValue) / (Math.log(NLEVELS) - Math.log(linLogTransitionValue));
        double b = NLEVELS - a * Math.log(NLEVELS);

        for (int i = linLogTransitionValue; i < NLEVELS; i++) {
            logMapping[i] = (int) Math.floor(a * Math.log(i) + b);
        }
    }

    private void fillSigmaThresholds() {
        Random r = new Random();
        if (sigmaThresholds == null) {
            sigmaThresholds = new int[getNumPixels()];
        }
        for (int i = 0; i < sigmaThresholds.length; i++) {
            int s = (int) Math.round(sigmaThreshold * r.nextGaussian());
            sigmaThresholds[i] = s;
        }
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

        @Override
        public void loadPreferences() {
            PSEyeCLModelRetina.this.loadPreferences(); // delegate to Chip object
        }

        public void storePreferences() {
           PSEyeCLModelRetina.this.storePreferences();
        }
    }

    public class EventExtractor extends TypedEventExtractor<TemporalContrastEvent> {

        public EventExtractor(AEChip aechip) {
            super(aechip);
        }

        /** Extracts events from the raw camera frame data that is supplied in the input packet.
         * Input packets are assumed to contain multiple frames of data (at least one but possibly several).
         * The timestamp for the events from each frame are either 
         * <ul>
         * <li> A single common timestamp for all events from each frame
         * <li> An interpolated timestamp for the events from each frame that assigns a continuous timestamp during each frame (TODO how does this work Mat?)
         * </ul>
         * Events are extracted according to the camera operating mode. For recorded data, it is assumed that the camera is operating in color mode.
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
            OutputEventIterator itr = out.outputIterator();
            if (linearInterpolateTimeStamp) {
                discreteEventCount.clear();
            }
            int sx = getSizeX(), sy = getSizeY(), addrCtr = 0, pixCtr = 0;
            int n = 0, lastBrightness, lastHue;
            float pixR = 0, pixB = 0, pixG = 0;
            if (out.getEventClass() != cDVSEvent.class) {
                out.setEventClass(cDVSEvent.class); // set the proper output event class to include color change events
            }
            colorMode = getCameraMode() == null ? true : getCameraMode().color == CLCamera.CLEYE_COLOR_PROCESSED; // TODO what do we do with recorded data? assumes color raw data now
            int bgIntervalUs = Integer.MAX_VALUE;
            if (backgroundEventRatePerPixelHz > 0) {
                bgIntervalUs = (int) (1e6f / backgroundEventRatePerPixelHz);
            }

            int npix = chip.getNumPixels();
            int nsamples = in.getNumEvents();
            int nframes = nsamples / npix;
            if (nsamples % npix != 0) {
                log.warning("input packet does not appear to contain an integral number of frames of raw pixel data: there are " + nsamples + " pixel samples which is not a multiple of " + npix + " pixels");
            }
            int ts = 0;
            for (int fr = 0; fr < nframes; fr++) {
                // get timestamp for events in this frame
                ts = in.getTimestamp(addrCtr); // timestamps stored here, currently only first timestamp meaningful TODO multiple frames stored here
                int eventTimeDelta = ts - lastFrameTimestamp;
//                System.out.println("nframes="+nframes+" fr="+fr+" ts="+ts+" addrCtr="+addrCtr);

                for (int y = 0; y < sy; y++) {
                    for (int x = 0; x < sx; x++) {
                        int hueval = 127;
                        int brightnessval = map2linlog(pixVals[addrCtr] & 0xff); // get gray value 0-255

                        if (colorMode) { // compute mean wavelength value from RGB
                            // Here we define the "mean color" as the magnitude of the difference between red and blue, scaled to 0-1.
                            // Then if the color difference changes, we get events. If the difference becomes larger, we get "bigger color difference"
                            // events, if the difference becomes smaller, we get "smaller color difference" events.
                            // Not exactly mean wavelength.
                            //  1-2(rb)/(r^2+b^2) =(b-r)^2/(r^2+b^2) which is somehow a measure of mean wavelength.
                            // This is the magnitude of the difference between the red and blue value scaled to a value between 0 and 1
                            final int RMASK = 0xff0000;
                            final int GMASK = 0x00ff00;
                            final int BMASK = 0x0000ff; // colors packed like this into each int when in color mode
                            final float lr = 650, lg = 500, lb = 430; // guesstimated mean wavelengh of color filters

                            pixR = (float) ((pixVals[addrCtr] >> 16) & 0xff);
                            pixG = (float) ((pixVals[addrCtr] >> 8) & 0xff);
                            pixB = (float) (pixVals[addrCtr] & 0xff);
                            pixR = map2linlog((int) pixR);
                            pixG = map2linlog((int) pixG);
                            pixB = map2linlog((int) pixB);
                            float sum = 0;
                            switch (retinaModel) {
                                case DifferenceRB:
                                    if (pixR > 0 || pixB > 0) {
                                        hueval = (int) (255 - 510 * (pixR * pixB) / (pixR * pixR + pixB * pixB));
                                    }
                                    break;
                                case MeanWaveLength:
                                    sum = (pixR + pixG + pixB);
                                    if (sum > 0) {
                                        // new method computes hue directly
                                        hueval = (int) ((lr * pixR + lg * pixG + lb * pixB) / sum);
                                        hueval = (int) (255 * (hueval - lb) / ((float) (lr - lb)));
                                        hueval = 256 - hueval; // flip hue value to get in same sense os RatioBtoRG, higher value is more blue
                                    }
                                    break;
                                case RatioBtoRG:
                                    sum = pixG + pixR;
                                    if (sum > 0) {
                                        hueval = (int) (255 * ((pixB) / sum));
                                    }
                            }
                        }
                        if (!initialized) {
                            lastBrightnessValues[pixCtr] = brightnessval; // update stored gray level for first frame
                            lastHueValues[pixCtr] = hueval; // update stored gray level for first frame
                        }

                        lastBrightness = lastBrightnessValues[pixCtr];
                        lastHue = lastHueValues[pixCtr];
                        int brightnessdiff = brightnessval - lastBrightness;

                        // Output synthetic events here.
                        //  At the moment, the threshold variation is the same for each pixel for all event types. I.e., if a
                        // pixel has a high threshold, it will be high for all event types, e.g. if it is hard to make ON events,
                        // it will also be hard to make off events. This is only one possible variation.

                        // events are output according to the size of the change from the stored value.
                        // after events are output, the stored value is updated not to the new sample, but rather
                        // by the number of emitted events times the threshold. The idea here is that
                        // the leftover change is not discarded by emitting the events. E.g. if the threhsold is 10
                        // and the change is +35, we emit 3 ON events but only increase the stored value by 30, not by 35.
                        // Then on the next sample if the change is an additional +5, we emit 1 ON event rather than none.
                        // This is closer to what the DVS pixel actually does than storing the new sample.


                        // Background events are emitted according to the backgroundEventRatePerPixelHz of a particular event
                        // type. For the DVS, ON events are emitted, while for the cDVS, Redder events are emitted (TODO check this).
                        // Background events are emitted at a fixed time after the pixel last emitted an event regardless of the 
                        // sample value to mimic the constant leakage of the stored pixel voltage on the differencing amplifier
                        // towards Vdd. In reality this background rate varies considerably between pixels owing to large 
                        // differences in dark current in the switches but we do not model this. Also, the cDVS emits background
                        // events at a higher rate than the DVS because the differencing amplifier has higher gain, but we also
                        // do not model this.


                        // brightness change 
                        if (brightnessdiff > brightnessChangeThreshold + sigmaThresholds[pixCtr]) { // if our gray level is sufficiently higher than the stored gray level
                            n = brightnessdiff / brightnessChangeThreshold;
                            outputEvents(cDVSEvent.EventType.Brighter, pixCtr, n, itr, x, sy, y, ts, eventTimeDelta, out);
                            lastBrightnessValues[pixCtr] += brightnessChangeThreshold * n; // update stored gray level by events // TODO include mismatch

                        } else if (brightnessdiff < -brightnessChangeThreshold - sigmaThresholds[pixCtr]) { // note negative on sigmaThresholds here
                            n = -brightnessdiff / brightnessChangeThreshold;
                            outputEvents(cDVSEvent.EventType.Darker, pixCtr, n, itr, x, sy, y, ts, eventTimeDelta, out);
                            lastBrightnessValues[pixCtr] -= brightnessChangeThreshold * n;
                        }
                        // hue change
                        int huediff = hueval - lastHue;
                        if (huediff > hueChangeThreshold + sigmaThresholds[pixCtr]) { // if our gray level is sufficiently higher than the stored gray level
                            n = huediff / hueChangeThreshold;
                            outputEvents(cDVSEvent.EventType.Bluer, pixCtr, n, itr, x, sy, y, ts, eventTimeDelta, out);
                            lastHueValues[pixCtr] += hueChangeThreshold * n; // update stored gray level by events
                        } else if (huediff < -hueChangeThreshold - sigmaThresholds[pixCtr]) {
                            n = -huediff / hueChangeThreshold;
                            outputEvents(cDVSEvent.EventType.Redder, pixCtr, n, itr, x, sy, y, ts, eventTimeDelta, out);
                            lastHueValues[pixCtr] -= hueChangeThreshold * n;
                        }
                        // emit background event possibly
                        if (backgroundEventRatePerPixelHz > 0 && (ts - lastEventTimes[pixCtr]) > bgIntervalUs) {
                            // randomly emit either Brighter or Redder event TODO check Redder/Bluer
                            cDVSEvent.EventType bgType = bgRandom.nextBoolean() ? cDVSEvent.EventType.Brighter : cDVSEvent.EventType.Redder;
                            outputEvents(bgType, pixCtr, 1, itr, x, sy, y, ts, eventTimeDelta, out);
                            // randomize time stored to avoid synchronizing background activity
                            // TODO doesn't really work well, since it leaves behind a trail of activity that mirrors input after delay
                            lastEventTimes[pixCtr] += bgRandom.nextInt(bgIntervalUs);
                        }

                        addrCtr++; // pixel counter
                        pixCtr++;
                    } // inner loop of pixel addresses
                } // loop over rows
                initialized = true;
                lastFrameTimestamp = ts;
                pixCtr = 0;
            }// frame
        } // extractor

        /** Outputs n events of a given type for pixel i using the output iterator itr, for pixel address, x, y, with stride sy, and timestamp ts, using
         * eventTimeDelta for timestamp interpolation and output packet out.
         * @param type type of output event
         * @param i pixel index in 1d pixel arrays, e.g. lastEventTimes
         * @param n emit this many copies of event
         * @param itr
         * @param x
         * @param sy
         * @param y
         * @param ts
         * @param eventTimeDelta
         * @param out 
         */
        private void outputEvents(cDVSEvent.EventType type, int i, int n, OutputEventIterator itr, int x, int sy, int y, int ts, int eventTimeDelta, EventPacket out) {
            for (int j = 0; j < n; j++) { // use down iterator as ensures latest timestamp as last event
                cDVSEvent e = (cDVSEvent) itr.nextOutput();
                e.x = (short) x;
                e.y = (short) (sy - y - 1); // flip y according to jAER with 0,0 at LL
                e.eventType = type;
                if (linearInterpolateTimeStamp) {
                    e.timestamp = ts - j * eventTimeDelta / n;
                    orderedLastSwap(out, j);
                } else {
                    e.timestamp = ts;
                }
            }
            lastEventTimes[i] = ts; // store event time for this pixel
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
    synchronized public void setGain(int gain) {
        if (gain < 1) {
            gain = 1;
        } else if (gain > CLCamera.CLEYE_MAX_GAIN) {
            gain = CLCamera.CLEYE_MAX_GAIN;
        }
        if (this.gain != gain) {
            setChanged();
            this.gain = gain;
        }
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
    synchronized public void setExposure(int exposure) {
        if (exposure < 1) {
            exposure = 1;
        } else if (exposure > CLCamera.CLEYE_MAX_EXPOSURE) {
            exposure = CLCamera.CLEYE_MAX_EXPOSURE;
        }
        if (this.exposure != exposure) {
            setChanged();
            this.exposure = exposure;
        }
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
            this.autoGainEnabled = autoGainEnabled;
        }
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
            this.autoExposureEnabled = autoExposureEnabled;
        }
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
    synchronized public void setBrightnessChangeThreshold(int brightnessChangeThreshold) {
        if (brightnessChangeThreshold < 1) {
            brightnessChangeThreshold = 1;
        } else if (brightnessChangeThreshold > NLEVELS) {
            brightnessChangeThreshold = NLEVELS;
        }
        if (this.brightnessChangeThreshold != brightnessChangeThreshold) {
            setChanged();
            this.brightnessChangeThreshold = brightnessChangeThreshold;
        }
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
    synchronized public void setLinearInterpolateTimeStamp(boolean linearInterpolateTimeStamp) {
        if (this.linearInterpolateTimeStamp != linearInterpolateTimeStamp) {
            setChanged();
            this.linearInterpolateTimeStamp = linearInterpolateTimeStamp;
        }
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
    synchronized public void setCameraMode(CameraMode mode) throws HardwareInterfaceException {
        if (getHardwareInterface() == null || !(getHardwareInterface() instanceof CLCamera)) {
            return;
        }
        CLCamera cl = (CLCamera) getHardwareInterface();
        if (cl.getCameraMode() != mode) {
            setChanged();
        }
        cl.setCameraMode(mode);
        sendConfiguration();
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

    @Override
    public AEFileInputStream constuctFileInputStream(File file) throws IOException {
        return new CLCameraFileInputStream(file);
    }

    /**
     * @return the sigmaThreshold
     */
    public float getSigmaThreshold() {
        return sigmaThreshold;
    }

    /**
     * @param sigmaThreshold the sigmaThreshold to set
     */
    public void setSigmaThreshold(float sigmaThreshold) {
        if (sigmaThreshold < 0) {
            sigmaThreshold = 0;
        } else if (sigmaThreshold > NLEVELS) {
            sigmaThreshold = NLEVELS;
        }
        if (this.sigmaThreshold != sigmaThreshold) {
            setChanged();
            this.sigmaThreshold = sigmaThreshold;
        }
        fillSigmaThresholds();
        notifyObservers(EVENT_SIGMA_THRESHOLD);
    }

    /**
     * @return the backgroundEventRatePerPixelHz
     */
    public float getBackgroundEventRatePerPixelHz() {
        return backgroundEventRatePerPixelHz;
    }

    /**
     * @param backgroundEventRatePerPixelHz the backgroundEventRatePerPixelHz to set
     */
    public void setBackgroundEventRatePerPixelHz(float backgroundEventRatePerPixelHz) {
        if (backgroundEventRatePerPixelHz < 0) {
            backgroundEventRatePerPixelHz = 0;
        } else if (backgroundEventRatePerPixelHz > NLEVELS) {
            backgroundEventRatePerPixelHz = NLEVELS;
        }
        if (this.backgroundEventRatePerPixelHz != backgroundEventRatePerPixelHz) {
            setChanged();
            this.backgroundEventRatePerPixelHz = backgroundEventRatePerPixelHz;
        }
        notifyObservers(EVENT_BACKGROUND_EVENT_RATE);
    }
}
