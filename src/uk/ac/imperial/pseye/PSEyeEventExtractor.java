
package uk.ac.imperial.pseye;

import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.chip.TypedEventExtractor;
import ch.unizh.ini.jaer.projects.thresholdlearner.TemporalContrastEvent;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.EventPacket;
import java.util.prefs.Preferences;
import net.sf.jaer.event.OutputEventIterator;
import ch.unizh.ini.jaer.chip.dvs320.cDVSEvent;
import java.util.logging.Logger;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/**
 * Generic event extractor for PSEye frame type data
 * @author Mat Katz
 */
abstract class PSEyeEventExtractor extends TypedEventExtractor<TemporalContrastEvent> {
    protected static final Logger log = Logger.getLogger("Chip");
    public static final int MAX_EVENTS = 1000000;
    
    public static final long SIGMA_SEED = 20111126;
    protected double sigmaOnThreshold;
    protected double sigmaOffThreshold;
    protected double sigmaOnDeviation;
    protected double sigmaOffDeviation;
    
    protected double[] sigmaOnThresholds;
    protected double[] sigmaOffThresholds;
    
    protected boolean initialised = false;
    protected int currentFrameTimestamp;
    protected int lastFrameTimestamp;
    protected int sx;
    protected int sy;
    
    // flag telling model that camera is restarting so don't extract events
    protected boolean linearInterpolateTimeStamp = true;
    protected ArrayList<Integer> discreteEventCount = new ArrayList<Integer>();
    
    public static final int NLEVELS = 256;
    protected boolean logIntensityMode;
    protected int linLogTransitionValue;    
    protected double[] valueMapping = new double[NLEVELS];
    
    // counters
    protected int eventCount;
    protected int frameCount;
    protected int timeCount;
    
    protected ArrayList<Integer> pixelIndices = new ArrayList<Integer>();
    protected int[] pixelValues;
    protected double[] previousValues;
    protected int nPixels;
    
    protected PSEyeModelChip modelChip;
    protected AEMonitorInterface hardwareInterface;

    public PSEyeEventExtractor(AEChip aechip) {
        super(aechip);
        modelChip = (PSEyeModelChip) chip;
    }
    
    abstract protected void createEvents(int pixelIndex, OutputEventIterator itr);
    abstract protected void initValues(int pixelIndex);

    protected boolean isHardwareInterfaceEnabled() {

        hardwareInterface = null;
        if (modelChip != null && modelChip.checkHardware()) {
            hardwareInterface = (AEMonitorInterface) modelChip.getHardwareInterface();
            return hardwareInterface.isEventAcquisitionEnabled();
        }
        return false;
    }
    
    protected void storePreferences(Preferences prefs) {
        prefs.putDouble("sigmaOnThreshold", sigmaOnThreshold);
        prefs.putDouble("sigmaOffThreshold", sigmaOffThreshold);
        prefs.putDouble("sigmaOnDeviation", sigmaOnDeviation);
        prefs.putDouble("sigmaOffDeviation", sigmaOffDeviation);
        
        prefs.putBoolean("linearInterpolateTimeStamp", linearInterpolateTimeStamp);
        prefs.putBoolean("logIntensityMode", logIntensityMode);
        prefs.putInt("linLogTransitionValue", linLogTransitionValue);
    }
    
    protected void loadPreferences(Preferences prefs) {
        sigmaOnThreshold = prefs.getDouble("sigmaOnThreshold", 10);
        sigmaOffThreshold = prefs.getDouble("sigmaOffThreshold", -10);
        sigmaOnDeviation = prefs.getDouble("sigmaOnDeviation", 0.02);
        sigmaOffDeviation = prefs.getDouble("sigmaOffDeviation", 0.02);
        
        linearInterpolateTimeStamp = prefs.getBoolean("linearInterpolateTimeStamp", false);
        logIntensityMode = prefs.getBoolean("logIntensityMode", true);
        linLogTransitionValue = prefs.getInt("linLogTransitionValue", 15);
        
        init();
    }
    
    protected synchronized void init() {
        sx = chip.getSizeX();
        sy = chip.getSizeY();
        nPixels = sx * sy;

        previousValues = new double[nPixels];
        
        // create index list and add all indices
        pixelIndices.clear();
        for (int i = 0; i < nPixels; i++) {
            pixelIndices.add(i);
        }
        
        initialised = false;
        fillValueMapping();
        fillSigmaThresholds();
    }
    
    protected synchronized void reset() {
        if (sx != chip.getSizeX() || sy != chip.getSizeY()) {
            init();
        }
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
     * <li>If the mode is colour, then {@link cDVSEvent} are output, reporting both intensity and color change.
     * </ul>
     * @param in in raw input packet from the CLCamera holding intensity/colour pixel RGB values
     * @param out the output event packet holding cooked events
     */
    @Override
    public synchronized void extractPacket(AEPacketRaw in, EventPacket out) {
        if (!isHardwareInterfaceEnabled())
            return;
        
        // check to see that framepacket has correct number of pixels
        if (!(in instanceof PSEyeFramePacketRaw))
            return;
        
        PSEyeFramePacketRaw framePacket = (PSEyeFramePacketRaw) in;
        if (framePacket.frameSize != nPixels)
            return;
        
        int nFrames = framePacket.nFrames;
        
        pixelValues = in.getAddresses(); // pixel RGB values stored here by hardware interface
        out.allocate(MAX_EVENTS);
        OutputEventIterator itr = out.outputIterator();
        
        //OutputEventIterator itr = out.outputIterator();
        if (linearInterpolateTimeStamp) {
            discreteEventCount.clear();
        }

        if (out.getEventClass() != cDVSEvent.class) {
            out.setEventClass(cDVSEvent.class); // set the proper output event class to include color change events
        }

        currentFrameTimestamp = 0;
        int pixelIndex;
        eventCount = 0;
        for (int fr = 0; fr < nFrames; fr++) {
            // get timestamp for events in this frame
            if (initialised) {
                currentFrameTimestamp = in.getTimestamp(fr * nPixels); // timestamps stored here, currently only first timestamp meaningful TODO multiple frames stored here
             
                Iterator<Integer> pixelIterator = pixelIndices.iterator();
                for (int i = 0; i < nPixels; i++) {
                    pixelIndex = pixelIterator.next();
                    createEvents(pixelIndex, itr);
                    if (eventCount >= MAX_EVENTS) {
                        log.warning("Maximum events (" + MAX_EVENTS +") exceeded ignoring further output");
                        break;
                    }
                }
            }
            else {
                currentFrameTimestamp = 0;
                Iterator<Integer> pixelIterator = pixelIndices.iterator();
                for (int i = 0; i < nPixels; i++) {
                    pixelIndex = pixelIterator.next();
                    initValues(pixelIndex);
                } 
                initialised = true;
            }

            timeCount += currentFrameTimestamp - lastFrameTimestamp;
            lastFrameTimestamp = currentFrameTimestamp;
            frameCount++;    
            
            if (frameCount >= (3 * modelChip.getFrameRate())) {
                log.warning("Frame Rate: " + frameCount * 1000000.0f / timeCount);
                frameCount = 0;
                timeCount = 0;
            }
        }
    }
    
    protected void outputEvents(cDVSEvent.EventType type, int number, OutputEventIterator itr, int x, int y) {
        for (int j = 0; j < number; j++) { // use down iterator as ensures latest timestamp as last event
            if (eventCount >= MAX_EVENTS) break;
            cDVSEvent e = (cDVSEvent) itr.nextOutput();
            e.x = (short) x;
            e.y = (short) (sy - y - 1); // flip y according to jAER with 0,0 at LL
            e.eventType = type;
            if (linearInterpolateTimeStamp) {
                e.timestamp = currentFrameTimestamp - j * (currentFrameTimestamp - lastFrameTimestamp) / number;
            } else {
                e.timestamp = currentFrameTimestamp;
            }
            eventCount++;
        }
        
    }
    
    protected void fillValueMapping() {
        // fill up lookup table to compute log from 8 bit sample value
        // the first linpart values are linear, then the rest are log, so that it ends up mapping 0:255 to 0:255
        int transistionValue = NLEVELS;
        double a = 0;
        double b = 0;
        
        if (logIntensityMode) {
            transistionValue = linLogTransitionValue;
            a = ((double) NLEVELS - linLogTransitionValue) / (Math.log(NLEVELS) - Math.log(linLogTransitionValue));
            b = ((double) NLEVELS) - a * Math.log(NLEVELS);
        }
        
        for (int i = 0; i < transistionValue; i++) {
            valueMapping[i] = i;
        }

        for (int i = transistionValue; i < NLEVELS; i++) {
            valueMapping[i] = a * Math.log(i) + b;
        }
    }
    
    protected void fillSigmaThresholds() {
        Random r = new Random(SIGMA_SEED);
        
        sigmaOnThresholds = new double[nPixels];
        sigmaOffThresholds = new double[nPixels];
        
        for (int i = 0; i < nPixels; i++) {
            sigmaOnThresholds[i] = sigmaOnThreshold * (1 + sigmaOnDeviation * r.nextGaussian());
            if (sigmaOnThresholds[i] <= 0)
                sigmaOnThresholds[i] = NLEVELS;
            
            sigmaOffThresholds[i] = sigmaOffThreshold * (1 + sigmaOffDeviation * r.nextGaussian());
            if (sigmaOffThresholds[i] >= 0)
                sigmaOffThresholds[i] = -NLEVELS;
        }
    }
}
            
            /*
            
            if (true) {    
                int brightnessval = map2linlog(pixVals[addrCtr] & 0xff); // get gray value 0-255

                    if (!initialized) {
                        lastBrightnessValues[pixCtr] = brightnessval; // update stored gray level for first frame
                    }

                    lastBrightness = lastBrightnessValues[pixCtr];
                    int brightnessdiff = brightnessval - lastBrightness;

                    // Output synthetic events here.
                    //  At the moment, the threshold variation is the same for each pixel for all event types. I.e., if a
                    // pixel has a high threshold, it will be high for all event types, e.g. if it is hard to make ON events
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
                    if (brightnessdiff > brightnessChangeThreshold) { // if our gray level is sufficiently higher than the stored gray level
                        n = brightnessdiff / brightnessChangeThreshold;
                        outputEvents(cDVSEvent.EventType.Brighter, pixCtr, n, itr, x, sy, y, ts, eventTimeDelta, out);
                        lastBrightnessValues[pixCtr] += brightnessChangeThreshold * n; // update stored gray level by events // TODO include mismatch

                    } else if (brightnessdiff < -brightnessChangeThreshold) { // note negative on sigmaThresholds here
                        n = -brightnessdiff / brightnessChangeThreshold;
                        outputEvents(cDVSEvent.EventType.Darker, pixCtr, n, itr, x, sy, y, ts, eventTimeDelta, out);
                        lastBrightnessValues[pixCtr] -= brightnessChangeThreshold * n;
                    }

                    addrCtr++; // pixel counter
                    pixCtr++;
                } // inner loop of pixel addresses
            } // loop over rows
            initialized = true;
            lastFrameTimestamp = ts;
            pixCtr = 0;
                
            frameCount++;    
            timeCount += eventTimeDelta;
                
            if (frameCount >= (3 * frameRate)) {
                log.warning("Frame Rate: " + frameCount * 1000000.0f / timeCount);
                frameCount = 0;
                timeCount = 0;
            }
        }// frame
    } // extractor
        


}
*/