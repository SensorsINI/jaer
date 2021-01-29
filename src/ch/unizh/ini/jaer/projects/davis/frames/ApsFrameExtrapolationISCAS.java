/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.frames;

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
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Demonstrates the DAVIS real time interframe reconstruction in the paper 
 * C. Brandli, L. Muller, and T. Delbruck, “Real-Time, High-Speed Video Decompression Using a Frame- and Event-Based DAVIS Sensor,” in Proc. 2014 Intl. Symp. Circuits and Systems (ISCAS 2014), Melbourne, Australia, 2014. 

 * @author Christian & Lorenz
 */
@Description("Extrapolates between two frames using the events ISCAS paper version")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ApsFrameExtrapolationISCAS extends EventFilter2D {

    /**
     * A boolean to set whether the OpenGL acceleration should be used
     */
//    private boolean useOpenCV = getPrefs().getBoolean("ApsFrameExtrapolation.useOpenCV", false);
//    {setPropertyTooltip("useOpenCV", "A boolean to set whether the OpenGL acceleration should be used");}
    private boolean revertLearning = getBoolean("revertLearning", false);
    private boolean displayError = getBoolean("displayError", false);
    private boolean freezeGains = getBoolean("freezeGains", false);
    private boolean clipping = getBoolean("clipping", true);
    private boolean expansion=getBoolean("expansion",false);
    private boolean tieGainsToAverage = getBoolean("tieGainsToAverage", true);
    private float manualOnGain = getFloat("manualOnGain", 0.001f);
    private float manualOffGain = getFloat("manualOffGain", -0.001f);

    private FilterChain filterChain;
    private BackgroundActivityFilter baFilter;
    private ApsFrameExtractor frameExtractor;

    private float[] lastDisplayBuffer, displayBuffer;
    private float[] pixelErrors;
    private int[] pixelOnEventCounter, pixelOffEventCounter;
    private float globalOnGain, globalOffGain;
    private boolean bufferEOE = false;
    private ApsDvsEventPacket eoeBufferPacket;
    private OutputEventIterator eoeIt;

    public int eventsSinceFrame;
    public float errorSum, errPP, errPE;

    private final int nonSpikingPixels = 50 * 180;
    private float minDisplayBuffer, maxDisplayBuffer;

    //TIMEbins method parameters
//    private float binErrorMixingFactor = 0.4f; // not used currently
    private float globalAverageGainMixingFactor = getFloat("globalAverageGainMixingFactor", 0.1f);//0.4f; // how quickly the bin average adapts
    private float perPixelAndTimeBinErrorUpdateFactor = getFloat("perPixelAndTimeBinMixingFactor", 0.25f);//0.004f; //how quickly the per pixel bins adapts
    private int[] lastStamp;
    private final float timeBinSize = 1.5f;
    private final int numTimeBins = getInt("numTimeBins",3);  // per pixel this many bins are used
    private short[][] onTimeBlame, offTimeBlame;
    private float[] onTimeBins, offTimeBins;
    private float[][] onPixTimeBins, offPixTimeBins;
    
    private EngineeringFormat engFmt=new EngineeringFormat();

    public ApsFrameExtrapolationISCAS(AEChip chip) {
        super(chip);

        setPropertyTooltip("manualOnGain", "Gain for the rendering of the ON DVS event (if manual extrapolation method chosen by selecting freezeGains and revertLearning button)");
        setPropertyTooltip("manualOffGain", "Gain for the rendering of the ON DVS event (if manual extrapolation method chosen by selecting freezeGains and revertLearning button)");
        setPropertyTooltip("revertLearning", "Revert gains back to manual settings");
        setPropertyTooltip("clipping", "Clip the image to 0-1 range; use to reset reconstructed frame if it has diverged. Sometimes turning off clipping helps learn better gains, faster and more stably.");
        setPropertyTooltip("displayError", "Show the pixel errors compared to last frame TODO");
        setPropertyTooltip("freezeGains", "Freeze the gains");
        setPropertyTooltip("perPixelAndTimeBinErrorUpdateFactor", "Sets the rate that error affects gains of individual pixels always (independent of tieGainsToAverage) and per time bin since last event. Ideally 1 should correct the gains from a single frame. Range 0-1, typical value 0.4.");
        setPropertyTooltip("globalAverageGainMixingFactor", "mixing factor for average of time bins, range 0-1, increase to speed up learning.");
        setPropertyTooltip("revertLearning", "If set, continually reverts gains to the manual settings.");
        setPropertyTooltip("tieGainsToAverage", "All the gains are clamped to be identical to the average. Use this option to greatly speed up initial gain setting.");
        setPropertyTooltip("expansion", "Whether expansion of the extrapolation to neighboring pixels is used, to fill in missing pixels with missing events (not in published results)");

        filterChain = new FilterChain(chip);
        baFilter = new BackgroundActivityFilter(chip);
        filterChain.add(baFilter);
        frameExtractor = new ApsFrameExtractor(chip);
        frameExtractor.setExtRender(false); // event though this method renders the events, the initial frames are not touched
        frameExtractor.setLogCompress(true);
        frameExtractor.setLogDecompress(true);
        filterChain.add(frameExtractor);
        setEnclosedFilterChain(filterChain);
        filterChain.reset();

        initFilter();
    }
    
    synchronized public void doRevertLearning(){
        revertLearning();
    }

    private void initMaps() {
        eventsSinceFrame = 0;
        globalOnGain = getFloat("globalOnGain", manualOnGain);
        globalOffGain = getFloat("globalOffGain", manualOffGain);
        minDisplayBuffer = frameExtractor.getMinBufferValue();
        maxDisplayBuffer = frameExtractor.getMaxBufferValue();
        displayBuffer = new float[frameExtractor.getNewFrame().length];
        pixelOnEventCounter = new int[frameExtractor.getNewFrame().length];
        pixelOffEventCounter = new int[frameExtractor.getNewFrame().length];
        lastDisplayBuffer = new float[frameExtractor.getNewFrame().length];
        onTimeBlame = new short[frameExtractor.getNewFrame().length][numTimeBins];
        offTimeBlame = new short[frameExtractor.getNewFrame().length][numTimeBins];
        onPixTimeBins = new float[frameExtractor.getNewFrame().length][numTimeBins];
        offPixTimeBins = new float[frameExtractor.getNewFrame().length][numTimeBins];
        pixelErrors = new float[frameExtractor.getNewFrame().length];
        displayBuffer = frameExtractor.getDisplayBuffer();
        lastDisplayBuffer = displayBuffer.clone();
        onTimeBins = new float[numTimeBins];
        offTimeBins = new float[numTimeBins];
        lastStamp = new int[frameExtractor.getNewFrame().length];
        Arrays.fill(onTimeBins, manualOnGain);
        Arrays.fill(offTimeBins, manualOffGain);
        Arrays.fill(lastStamp, 0);
        Arrays.fill(pixelErrors, 0.0f);
        Arrays.fill(pixelOnEventCounter, 0);
        Arrays.fill(pixelOffEventCounter, 0);

        for (int i = 0; i < onPixTimeBins.length; i++) {
            Arrays.fill(onPixTimeBins[i], manualOnGain);
            Arrays.fill(offPixTimeBins[i], manualOffGain);
        }
        for (int i = 0; i < onTimeBlame.length; i++) {
            Arrays.fill(onTimeBlame[i], (short) 0);
        }
        for (int i = 0; i < offTimeBlame.length; i++) {
            Arrays.fill(offTimeBlame[i], (short) 0);
        }
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (!filterEnabled || in.getClass() != ApsDvsEventPacket.class) {
            return in;
        }
        checkOutputPacketEventType(in);
        if (enclosedFilterChain != null) {
            in = enclosedFilterChain.filterPacket(in);
        }

        if (revertLearning) {
            revertLearning();
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
                newFrame();
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
        frameExtractor.setLegend("Events since last frame: " + eventsSinceFrame + ", err/pixel: " + engFmt.format(errPP) + ", err/event: " + engFmt.format(errPE));
        return in;
    }

    @Override
   synchronized public void resetFilter() {
        initMaps();
        filterChain.reset();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    private void newFrame() {
        updateWeights();
        errPP = errorSum / (displayBuffer.length - nonSpikingPixels);
        errPE = errorSum / eventsSinceFrame;
        if (displayError) {
            displayErrors();
        } else {
            frameExtractor.displayPreBuffer();
        }
        eventsSinceFrame = 0;
        errorSum = 0.0f;
    }

    private void addEvent(ApsDvsEvent inEv) {
        addEventToFrameBinBased(inEv);
        if (!freezeGains) {
            updateTimeBlame(inEv);
        }
        eventsSinceFrame++;
    }

    private void addEOEbufferEvents() {
        if(eoeBufferPacket==null) return;
        Iterator inIt = eoeBufferPacket.iterator();
        while (inIt.hasNext()) {
            ApsDvsEvent inEv = (ApsDvsEvent) inIt.next();
            if (inEv.isDVSEvent()) {
                addEvent(inEv);
            }
        }
    }

    synchronized private void revertLearning() {
        globalOnGain = manualOnGain;
        globalOffGain = manualOffGain;
        for (int i = 0; i < onPixTimeBins.length; i++) {
            Arrays.fill(onPixTimeBins[i], globalOnGain);
            Arrays.fill(offPixTimeBins[i], globalOffGain);
        }
        Arrays.fill(onTimeBins, globalOnGain);
        Arrays.fill(offTimeBins, globalOffGain);
    }

    private void addEventToFrameBinBased(PolarityEvent e) {
        if (lastDisplayBuffer == null || lastDisplayBuffer.length != frameExtractor.getNewFrame().length || lastDisplayBuffer.length != displayBuffer.length) {
            initMaps();
            return;
        }
        int idx = getIndex(e.x, e.y);
        if (idx < 0) {
            return;
        }
        int bin = 0;
        bin = getTimeBin(e);
        if (bin < 0) {
            return;
        }
        if (e.polarity == PolarityEvent.Polarity.On) {
            pixelOnEventCounter[idx] += 1;
            displayBuffer[idx] += onPixTimeBins[idx][bin];
        } else {
            pixelOffEventCounter[idx] += 1;
            displayBuffer[idx] += offPixTimeBins[idx][bin];
        }
        if (expansion) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    if (dy == 0) {
                        continue;
                    }
                    int x = e.x + dx, y = e.y + dy;
                    if (x < 0 || x >= chip.getSizeX() || y < 0 || y >= chip.getSizeY()) {
                        continue;
                    }
                    int idx2 = getIndex(x, y);
                    displayBuffer[idx2] += (e.polarity == PolarityEvent.Polarity.On) ? onPixTimeBins[idx][bin] : offPixTimeBins[idx][bin];
                }
            }
        }
        //clipping of the displayBuffer values
//        if (isClipping()) {
//            if (displayBuffer[idx] > maxDisplayBuffer) {
//                displayBuffer[idx] = maxDisplayBuffer;
//            }
//            if (displayBuffer[idx] < minDisplayBuffer) {
//                displayBuffer[idx] = minDisplayBuffer;
//            }
//        }
        float display = displayBuffer[idx];
        if (display > maxDisplayBuffer) {
            display = maxDisplayBuffer;
        }
        if (display < minDisplayBuffer) {
            display = minDisplayBuffer;
        }
        if (!displayError) {
            frameExtractor.updateDisplayValue(e.x, e.y, (float) display);
        }
    }

    private void updateTimeBlame(PolarityEvent e) {
        int idx = getIndex(e.x, e.y);
        if (idx < 0) {
            return;
        }
        int bin = 0;
        bin = getTimeBin(e);
        if (bin >= 0 && bin < numTimeBins) {
            if (e.polarity == PolarityEvent.Polarity.On) {
                onTimeBlame[idx][bin]++;
            } else {
                offTimeBlame[idx][bin]++;
            }
        }
        lastStamp[idx] = e.timestamp;
    }

    private void updateWeights() {
        if (lastDisplayBuffer == null || lastDisplayBuffer.length != frameExtractor.getDisplayBuffer().length || lastDisplayBuffer.length != displayBuffer.length) {
            initMaps();
            return;
        }

        lastDisplayBuffer = displayBuffer.clone();
        displayBuffer = frameExtractor.getDisplayBuffer();
        if (!freezeGains) {
            updateWeightsTIMEbin();
        }
    }

    private void updateWeightsTIMEbin() {
        int[] newOnTimeBinCount = new int[numTimeBins];
        Arrays.fill(newOnTimeBinCount, 0);
        float[] newOnTimeBinSum = new float[numTimeBins];
        Arrays.fill(newOnTimeBinSum, 0.0f);
        int[] newOffTimeBinCount = new int[numTimeBins];
        Arrays.fill(newOffTimeBinCount, 0);
        float[] newOffTimeBinSum = new float[numTimeBins];
        Arrays.fill(newOffTimeBinSum, 0.0f);
        boolean tied = isTieGainsToAverage();

        for (int i = 0; i < lastDisplayBuffer.length; i++) {

            //calculate the error
            pixelErrors[i] = lastDisplayBuffer[i] - displayBuffer[i];
            errorSum += Math.abs(pixelErrors[i]);
            //update the ON and OFF gains
            for (int j = 0; j < numTimeBins; j++) {

                if (onTimeBlame[i][j] > 0) {
                    if (Math.abs((pixelOffEventCounter[i] + pixelOnEventCounter[i])) > 0.01f) {
                        onPixTimeBins[i][j] = onPixTimeBins[i][j] - perPixelAndTimeBinErrorUpdateFactor * pixelErrors[i] * onTimeBlame[i][j] / ((pixelOffEventCounter[i] + pixelOnEventCounter[i]));
                    }
                    newOnTimeBinSum[j] += onPixTimeBins[i][j];//onTimeBins[j] - binErrorMixingFactor*pixelErrors[i]*onTimeBlame[i][j]/((pixelOffEventCounter[i]+pixelOnEventCounter[i]));
                    newOnTimeBinCount[j]++; //the commented part above is an alternative to the RHS
                    onTimeBlame[i][j] = 0;
                }
                if (offTimeBlame[i][j] > 0) {
                    if (Math.abs((pixelOffEventCounter[i] + pixelOnEventCounter[i])) > 0.01f) {
                        offPixTimeBins[i][j] = offPixTimeBins[i][j] - perPixelAndTimeBinErrorUpdateFactor * pixelErrors[i] * offTimeBlame[i][j] / ((pixelOffEventCounter[i] + pixelOnEventCounter[i]));
                    }
                    newOffTimeBinSum[j] += offPixTimeBins[i][j];//offTimeBins[j] - binErrorMixingFactor*pixelErrors[i]*offTimeBlame[i][j]/((pixelOffEventCounter[i]+pixelOnEventCounter[i]));
                    newOffTimeBinCount[j]++; //the commented part above is an alternative to the RHS
                    offTimeBlame[i][j] = 0;
                }

            }
            pixelOnEventCounter[i] = 0;
            pixelOffEventCounter[i] = 0;
        }

        //calculate the mixed average
        for (int j = 0; j < numTimeBins; j++) {
            float newOnTimeBin = newOnTimeBinSum[j] / newOnTimeBinCount[j];
            if (newOnTimeBinCount[j] > 0 && newOnTimeBin > 0.0f && newOnTimeBin != Float.POSITIVE_INFINITY) {
                onTimeBins[j] = onTimeBins[j] * (1 - globalAverageGainMixingFactor) + globalAverageGainMixingFactor * newOnTimeBin;
                if (tied) {
                    for (int i = 0; i < lastDisplayBuffer.length; i++) {
                        onPixTimeBins[i][j] = onTimeBins[j];
                    }
                }
            }
            float newOffTimeBin = newOffTimeBinSum[j] / newOffTimeBinCount[j];
            if (newOffTimeBinCount[j] > 0 && newOffTimeBin < 0.0f && newOffTimeBin != Float.NEGATIVE_INFINITY) {
                offTimeBins[j] = offTimeBins[j] * (1 - globalAverageGainMixingFactor) + globalAverageGainMixingFactor * newOffTimeBin;
                if (tied) {
                    for (int i = 0; i < lastDisplayBuffer.length; i++) {
                        offPixTimeBins[i][j] = offTimeBins[j];
                    }
                }
            }
            if (offTimeBins[j] == Float.NEGATIVE_INFINITY || onTimeBins[j] == Float.POSITIVE_INFINITY) {
                System.out.println("weight update diverged");
            }
        }
        System.out.print("ON bins:\t");
        for (int j = 0; j < numTimeBins; j++) {
            System.out.print(String.format(" %+1.4f\t",onTimeBins[j]));
        }
        System.out.println();
        System.out.print("OFF bins:\t");
        for (int j = 0; j < numTimeBins; j++) {
            System.out.print(String.format(" %+1.4f\t",offTimeBins[j]));
         }
        System.out.println("");
        System.out.println("--------------------------");
        putFloatArray("onTimeBins", onTimeBins);
        putFloatArray("offTimeBins", offTimeBins);
    }

    private int getIndex(int x, int y) {
        if (x < 0 || x > chip.getSizeX() || y < 0 || y > chip.getSizeY()) {
            return -1;
        }
        return chip.getSizeX() * y + x;
    }

    private int getTimeBin(PolarityEvent e) {
        float dt = e.timestamp - lastStamp[getIndex(e.x, e.y)];
        int ind = 0;
        ind = (int) (Math.log(dt + 1) / Math.log(timeBinSize));
        if (ind >= numTimeBins) {
            ind = numTimeBins - 1;
        }
        if (ind < 0) {
            ind = 0;
        }
        return ind;
    }

    private void displayErrors() {
        float minValue = frameExtractor.getMinBufferValue();
        for (int i = 0; i < pixelErrors.length; i++) {
            int x = i % frameExtractor.width;
            int y = i / frameExtractor.width;
            frameExtractor.updateDisplayValue(x, y, (float) minValue + Math.abs(pixelErrors[i]));
        }
    }

//    /**
//     * @return the useOpenCV
//     */
//    public boolean isUseOpenCV() {
//        return useOpenCV;
//    }
//
//    /**
//     * @param useOpenCV the useOpenCV to set
//     */
//    public void setUseOpenCV(boolean useOpenCV) {
//        this.useOpenCV = useOpenCV;
//        prefs().putBoolean("ApsFrameExtrapolation.useOpenCV", useOpenCV);
//    }
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

    public boolean isTieGainsToAverage() {
        return tieGainsToAverage;
    }

    /**
     * @param tieGainsToAverage the tieGainsToAverage to set
     */
    synchronized public void setTieGainsToAverage(boolean tieGainsToAverage) {
        this.tieGainsToAverage = tieGainsToAverage;
    }

    /**
     * @return the revertLearning
     */
    public boolean isRevertLearning() {
        return revertLearning;
    }

    /**
     * @param revertLearning the revertLearning to set
     */
    synchronized public void setRevertLearning(boolean revertLearning) {
        this.revertLearning = revertLearning;
    }

    /**
     * @return the manualOnGain
     */
    public float getManualOnGain() {
        return manualOnGain;
    }

    /**
     * @param manualOnGain the manualOnGain to set
     */
    synchronized public void setManualOnGain(float manualOnGain) {
        manualOnGain=clip01(manualOnGain);
        this.manualOnGain = manualOnGain;
        putFloat("manualOnGain", manualOnGain);
    }

    public float getPerPixelAndTimeBinErrorUpdateFactor() {
        return perPixelAndTimeBinErrorUpdateFactor;
    }

    synchronized public void setPerPixelAndTimeBinErrorUpdateFactor(float perPixelAndTimeBinErrorUpdateFactor) {
        perPixelAndTimeBinErrorUpdateFactor=clip01(perPixelAndTimeBinErrorUpdateFactor);
        this.perPixelAndTimeBinErrorUpdateFactor = perPixelAndTimeBinErrorUpdateFactor;
        putFloat("perPixelAndTimeBinMixingFactor", perPixelAndTimeBinErrorUpdateFactor);
    }

    public float getGlobalAverageGainMixingFactor() {
        return globalAverageGainMixingFactor;
    }

    synchronized public void setGlobalAverageGainMixingFactor(float globalAverageGainMixingFactor) {
        this.globalAverageGainMixingFactor = globalAverageGainMixingFactor;
        putFloat("globalAverageGainMixingFactor", globalAverageGainMixingFactor);
    }

    /**
     * @return the manualOffGain
     */
    public float getManualOffGain() {
        return manualOffGain;
    }

    /**
     * @param manualOffGain the manualOffGain to set
     */
    public void setManualOffGain(float manualOffGain) {
        manualOffGain=-clip01(-manualOffGain);
        this.manualOffGain = manualOffGain;
        putFloat("manualOffGain", manualOffGain);
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

//    /**
//     * @return the clipping
//     */
//    public boolean isClipping() {
//        return clipping;
//    }

    // bad idea, reduces performance
//    /**
//     * @param clipping the clipping to set
//     */
//    public void setClipping(boolean clipping) {
//        this.clipping = clipping;
//        putBoolean("clipping",clipping);
//    }
//
    private float clip01(float val) {
        if(val>1) val=1; else if(val<0) val=0;
        return val;
    }

    /**
     * @return the expansion
     */
    public boolean isExpansion() {
        return expansion;
    }

    /**
     * @param expansion the expansion to set
     */
    public void setExpansion(boolean expansion) {
        this.expansion = expansion;
        putBoolean("expansion",expansion);
    }

}
