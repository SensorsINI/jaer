/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.frames;

import ch.unizh.ini.jaer.chip.retina.DVSTweaks;
import eu.seebetter.ini.chips.DavisChip;
import java.util.Arrays;
import java.util.Iterator;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.SpatioTemporalCorrelationFilter;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Demonstrates the DAVIS reconstruction using complementary filter from
 * Cedric's algorithm in
 * <p>
 * Continuous-time Intensity Estimation Using Event Cameras. Cedric Scheerlinck
 * (2018). at
 * <https://cedric-scheerlinck.github.io/continuous-time-intensity-estimation>
 * </p>
 * From the paper
 * <p>
 * Scheerlinck, C., Barnes, N. & Mahony, R. Continuous-Time Intensity Estimation
 * Using Event Cameras. in Computer Vision – ACCV 2018 308–324 (Springer
 * International Publishing, 2019). doi:10.1007/978-3-030-20873-8_20
 * </p>
 *
 * Algorithm is from
 * https://www.cedricscheerlinck.com/files/2018_scheerlinck_continuous-time_intensity_estimation.pdf,
 * Algorithm 1
 *
 * @author Tobi Delbruck
 */
@Description("DAVIS reconstruction using complementary filter from Cedric's algorithm")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DavisComplementaryFilter extends ApsFrameExtractor {

 

    /**
     * A boolean to set whether the OpenGL acceleration should be used
     */
//    private boolean useOpenCV = getPrefs().getBoolean("ApsFrameExtrapolation.useOpenCV", false);
//    {setPropertyTooltip("useOpenCV", "A boolean to set whether the OpenGL acceleration should be used");}
    private boolean revertLearning = getBoolean("revertLearning", false);
    private boolean displayError = getBoolean("displayError", false);
    private boolean freezeGains = getBoolean("freezeGains", false);
    private boolean clipping = getBoolean("clipping", true);
    private boolean fixThresholdsToAverage = getBoolean("fixThresholdsToAverage", true);
    
    private float onThreshold = getFloat("onThresshold", 0.001f);
    private float offThreshold = getFloat("offThreshold", -0.001f);
    
    
    protected float crossoverFrequencyHz = getFloat("crossoverFrequencyHz", 1f);
    protected float lambda = getFloat("lambda", .1f);
    protected float kappa = getFloat("lambda", 0.05f);

    private boolean thresholdsFromBiases = getBoolean("thresholdsFromBiases", true);

    private float[] logFFrame, logEFrame; // the log of the last APS frame and the final log CF frame

    private FilterChain filterChain;
    private SpatioTemporalCorrelationFilter baFilter;
    private ApsFrameExtractor frameExtractor;

    private float[] lastDisplayBuffer, displayBuffer;

    private ApsDvsEventPacket eoeBufferPacket;
    private OutputEventIterator eoeIt;
    private boolean bufferEOE = false;

    public int eventsSinceFrame;

    private int[] lastStamp;

    private EngineeringFormat engFmt = new EngineeringFormat();

    public DavisComplementaryFilter(AEChip chip) {
        super(chip);

        setPropertyTooltip("onThresshold", "Gain for the rendering of the ON DVS event (if manual extrapolation method chosen by selecting freezeGains and revertLearning button)");
        setPropertyTooltip("offThreshold", "Gain for the rendering of the ON DVS event (if manual extrapolation method chosen by selecting freezeGains and revertLearning button)");
        
        setPropertyTooltip("crossoverFrequencyHz", "The alpha crossover frequency in Hz, increase to update more with APS frames more");
        setPropertyTooltip("lambda", "Factor by which to descrease crossoverFrequencyHz for low and high APS exposure values that are near toe or shoulder of APS response");
        setPropertyTooltip("kappa", "fraction of entire Lmax-Lmin range to apply the decreased crossoverFrequencyHz");
        
        
        setPropertyTooltip("revertLearning", "Revert gains back to manual settings");
        setPropertyTooltip("clipping", "Clip the image to 0-1 range; use to reset reconstructed frame if it has diverged. Sometimes turning off clipping helps learn better gains, faster and more stably.");
        setPropertyTooltip("displayError", "Show the pixel errors compared to last frame TODO");
        setPropertyTooltip("freezeGains", "Freeze the gains");
        setPropertyTooltip("perPixelAndTimeBinErrorUpdateFactor", "Sets the rate that error affects gains of individual pixels always (independent of fixThresholdsToAverage) and per time bin since last event. Ideally 1 should correct the gains from a single frame. Range 0-1, typical value 0.4.");
        setPropertyTooltip("globalAverageGainMixingFactor", "mixing factor for average of time bins, range 0-1, increase to speed up learning.");
        setPropertyTooltip("revertLearning", "If set, continually reverts gains to the manual settings.");

        filterChain = new FilterChain(chip);
        baFilter = new SpatioTemporalCorrelationFilter(chip);
        filterChain.add(baFilter);
        frameExtractor = new ApsFrameExtractor(chip);
        frameExtractor.setExtRender(false); // event though this method renders the events, the initial frames are not touched
        frameExtractor.setLogCompress(true); // use frame extractot to show reconstruction, using log compression display since rendering is on DVS log scale
        frameExtractor.setLogDecompress(true); // decompress log so that values fill full range of computer display
        filterChain.add(frameExtractor);
        setEnclosedFilterChain(filterChain);
        filterChain.reset();

        initFilter();
    }

    synchronized public void doRevertLearning() {
        revertLearning();
    }

    private void initMaps() {
        eventsSinceFrame = 0;
        final int nPixels = frameExtractor.getNewFrame().length;
        displayBuffer = new float[nPixels];
        logFFrame = new float[nPixels];
        logEFrame = new float[nPixels];
        lastDisplayBuffer = new float[nPixels];
        displayBuffer = frameExtractor.getRawFrame();
        lastDisplayBuffer = displayBuffer.clone();
        lastStamp = new int[nPixels];
        Arrays.fill(lastStamp, 0);
    }

    @Override
    synchronized public void resetFilter() {
        initMaps();
        filterChain.reset();
    }

    @Override
    public void initFilter() {
        resetFilter();
        setThresholdsFromBiases();
    }

    public void setThresholdsFromBiases() {
        if (chip.getBiasgen() != null && chip.getBiasgen() instanceof DVSTweaks) {
            DVSTweaks tweaks = (DVSTweaks) chip.getBiasgen();
            setOnThreshold(tweaks.getOnThresholdLogE());
            setOffThreshold(tweaks.getOffThresholdLogE());
        }
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (!filterEnabled || in.getClass() != ApsDvsEventPacket.class) {
            return in;
        }
        checkOutputPacketEventType(in);
        in = getEnclosedFilterChain().filterPacket(in);

        if (revertLearning) {
            revertLearning();
        }
        if(frameExtractor.hasNewFrameAvailable()){
            
        }
        ApsDvsEventPacket inPack = (ApsDvsEventPacket) in;
        Iterator inIt = inPack.fullIterator();
        while (inIt.hasNext()) {
            ApsDvsEvent inEv = (ApsDvsEvent) inIt.next();
            if (inEv.isEndOfExposure()) {
                bufferEOE = true;
                if (eoeBufferPacket == null) {
                    eoeBufferPacket = new ApsDvsEventPacket(ApsDvsEvent.class);
                } else {
                    eoeBufferPacket.clear();
                }
                eoeIt = eoeBufferPacket.outputIterator();
                Arrays.fill(lastStamp, inEv.timestamp);
            } else if (inEv.isEndOfFrame()) {
                bufferEOE = false;
                addEOEbufferEvents();
            }
            if (inEv.isDVSEvent()) {
                if (bufferEOE) {
                    ApsDvsEvent nextBufEvt = (ApsDvsEvent) eoeIt.nextOutput();
                    nextBufEvt.copyFrom(inEv);
                } else {
                    addEvent(inEv);
                }
            }
        }
        frameExtractor.setLegend("Events since last frame: " + eventsSinceFrame);
        return in;
    }


    private void addEvent(ApsDvsEvent e) {
        eventsSinceFrame++;
        if (lastDisplayBuffer == null || lastDisplayBuffer.length != frameExtractor.getNewFrame().length || lastDisplayBuffer.length != displayBuffer.length) {
            initMaps();
            return;
        }
        int idx = getIndex(e.x, e.y);
        if (idx < 0) {
            return;
        }
        if (e.polarity == PolarityEvent.Polarity.On) {
//            displayBuffer[idx] += onPixTimeBins[idx][bin];
        } else {
//            displayBuffer[idx] += offPixTimeBins[idx][bin];
        }

        float display = displayBuffer[idx];
        frameExtractor.updateDisplayValue(e.x, e.y, (float) display);
    }

    private void addEOEbufferEvents() {
        if (eoeBufferPacket == null) {
            return;
        }
        Iterator inIt = eoeBufferPacket.iterator();
        while (inIt.hasNext()) {
            ApsDvsEvent inEv = (ApsDvsEvent) inIt.next();
            if (inEv.isDVSEvent()) {
                addEvent(inEv);
            }
        }
    }

    synchronized private void revertLearning() {
    }

    /**
     * @return the displayError
     */
    public boolean isDisplayError() {
        return displayError;
    }

    /**
     * @param displayError the displayError to set
     */
    synchronized public void setDisplayError(boolean displayError) {
        this.displayError = displayError;
        putBoolean("displayError", displayError);
    }

    public boolean isFixThresholdsToAverage() {
        return fixThresholdsToAverage;
    }

    /**
     * @param fixThresholdsToAverage the fixThresholdsToAverage to set
     */
    synchronized public void setFixThresholdsToAverage(boolean fixThresholdsToAverage) {
        this.fixThresholdsToAverage = fixThresholdsToAverage;
    }

//    /**
//     * @return the revertLearning
//     */
//    public boolean isRevertLearning() {
//        return revertLearning;
//    }
//
//    /**
//     * @param revertLearning the revertLearning to set
//     */
//    synchronized public void setRevertLearning(boolean revertLearning) {
//        this.revertLearning = revertLearning;
//    }

    /**
     * @return the onThreshold
     */
    public float getOnThreshold() {
        return onThreshold;
    }

    /**
     * @param onThreshold the onThreshold to set
     */
    synchronized public void setOnThreshold(float onThreshold) {
        float old = this.onThreshold;
        onThreshold = clip01(onThreshold);
        this.onThreshold = onThreshold;
        putFloat("onThresshold", onThreshold);
        getSupport().firePropertyChange("onThresshold", old, this.onThreshold);
    }

    /**
     * @return the offThreshold
     */
    public float getOffThreshold() {
        return offThreshold;
    }

    /**
     * @param offThreshold the offThreshold to set
     */
    public void setOffThreshold(float offThreshold) {
        float old = this.offThreshold;
        offThreshold = -clip01(-offThreshold);
        this.offThreshold = offThreshold;
        putFloat("offThreshold", offThreshold);
        getSupport().firePropertyChange("offThreshold", old, this.offThreshold);
    }

    /**
     * @return the freezeGains
     */
    public boolean isFreezeGains() {
        return freezeGains;
    }

    /**
     * @param freezeGains the freezeGains to set
     */
    public void setFreezeGains(boolean freezeGains) {
        this.freezeGains = freezeGains;
    }

    private float clip01(float val) {
        if (val > 1) {
            val = 1;
        } else if (val < 0) {
            val = 0;
        }
        return val;
    }

    /**
     * @return the crossoverFrequencyHz
     */
    public float getCrossoverFrequencyHz() {
        return crossoverFrequencyHz;
    }

    /**
     * @param crossoverFrequencyHz the crossoverFrequencyHz to set
     */
    public void setCrossoverFrequencyHz(float crossoverFrequencyHz) {
        this.crossoverFrequencyHz = crossoverFrequencyHz;
        putFloat("crossoverFrequencyHz",crossoverFrequencyHz);
    }
    
    
       /**
     * @return the lambda
     */
    public float getLambda() {
        return lambda;
    }

    /**
     * @param lambda the lambda to set
     */
    public void setLambda(float lambda) {
        this.lambda = lambda;
        putFloat("lambda",lambda);
    }

    /**
     * @return the kappa
     */
    public float getKappa() {
        return kappa;
    }

    /**
     * @param kappa the kappa to set
     */
    public void setKappa(float kappa) {
        this.kappa = kappa;
        putFloat("kappa",kappa);
    }

}
