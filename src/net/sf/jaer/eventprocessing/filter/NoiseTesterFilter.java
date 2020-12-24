/*
 * Copyright (C) 2020 tobid.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package net.sf.jaer.eventprocessing.filter;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;
import java.beans.PropertyChangeEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;

/**
 * Filter for testing noise filters
 *
 * @author tobid/shasah
 */
@Description("Tests noise filters by injecting known noise and measuring how much signal and noise is filtered")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class NoiseTesterFilter extends AbstractNoiseFilter implements FrameAnnotater, RemoteControlled {

    FilterChain chain;
    private float shotNoiseRateHz = getFloat("shotNoiseRateHz", .1f);
    private float leakNoiseRateHz = getFloat("leakNoiseRateHz", .1f);

    private static String DEFAULT_CSV_FILENAME_BASE = "NoiseTesterFilter";
    private String csvFileName = getString("csvFileName", DEFAULT_CSV_FILENAME_BASE);
    private File csvFile = null;
    private BufferedWriter csvWriter = null;

    // chip size values, set in initFilter()
    private int sx = 0;
    private int sy = 0;
    private int npix = 0;

    private Integer lastTimestampPreviousPacket = null; // use Integer Object so it can be null to signify no value yet
    private float TPR = 0;
    private float TPO = 0;
    private float TNR = 0;
    private float accuracy = 0;
    private float BR = 0;
    private EventPacket<ApsDvsEvent> signalAndNoisePacket = null;
    private EventSet<BasicEvent> noiseList = new EventSet();
    private Random random = new Random();
    private int poissonDtUs = 1;
    private AbstractNoiseFilter[] noiseFilters = null;
    private AbstractNoiseFilter selectedFilter = null;
    protected boolean resetCalled = true; // flag to reset on next event
    public static final float RATE_LIMIT_HZ = 10;

    public enum NoiseFilter {
        BackgroundActivityFilter, SpatioTemporalCorrelationFilter, SequenceBasedFilter, OrderNBackgroundActivityFilter
    }
    private NoiseFilter selectedNoiseFilterEnum = NoiseFilter.valueOf(getString("selectedNoiseFilter", NoiseFilter.BackgroundActivityFilter.toString())); //default is BAF
    private float correlationTimeS = getFloat("correlationTimeS", 20e-3f);

//    float BR = 2 * TPR * TPO / (TPR + TPO); // wish to norm to 1. if both TPR and TPO is 1. the value is 1
    public NoiseTesterFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("shotNoiseRateHz", "rate per pixel of shot noise events");
        setPropertyTooltip("leakNoiseRateHz", "rate per pixel of leak noise events");
        setPropertyTooltip("csvFileName", "Enter a filename base here to open CSV output file (appending to it if it already exists)");
        setPropertyTooltip("selectedNoiseFilter", "Choose a noise filter to test");
        if (chip.getRemoteControl() != null) {
            log.info("adding RemoteControlCommand listener to AEChip\n");
            chip.getRemoteControl().addCommandListener(this, "setNoiseFilterParameters", "set correlation time or distance.");
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        filterEnabled = yes;
        if (yes) {
            for (EventFilter2D f : chain) {
                if (selectedFilter != null && selectedFilter == f) {
                    f.setFilterEnabled(yes);
                }

            }
        } else {
            for (EventFilter2D f : chain) {
                f.setFilterEnabled(false);

            }
        }
    }

    public void doCloseCsvFile() {
        if (csvFile != null) {
            try {
                log.info("closing statistics output file" + csvFile);
                csvWriter.close();
            } catch (IOException e) {
                log.warning("could not close " + csvFile + ": caught " + e.toString());
            } finally {
                csvFile = null;
                csvWriter = null;
            }
        }
    }

    private void openCvsFiile() {
        String fn = csvFileName + ".csv";
        csvFile = new File(fn);
        log.info(String.format("opening %s for output", fn));
        try {
            csvWriter = new BufferedWriter(new FileWriter(csvFile, true));
            if (!csvFile.exists()) { // write header
                log.info("file did not exist, so writing header");
                csvWriter.write(String.format("TP,TN,FP,FN,TPR,TNR,BR,firstE.timestamp,"
                        + "inSignalRateHz,inNoiseRateHz,outSignalRateHz,outNoiseRateHz\n"));
            }
        } catch (IOException ex) {
            log.warning(String.format("could not open %s for output; caught %s", fn, ex.toString()));
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!showFilteringStatistics) {
            return;
        }
        findUnusedDawingY();
        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        final GLUT glut = new GLUT();
        gl.glColor3f(.2f, .2f, .8f); // must set color before raster position (raster position is like glVertex)
        gl.glRasterPos3f(0, statisticsDrawingPosition, 0);
        String s = String.format("TPR=%%%6.1f, TNR=%%%6.1f, BR=%%%6.1f, poissonDt=%d us", 100 * TPR, 100 * TNR, 100 * BR, poissonDtUs);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, s);
        gl.glPopMatrix();
    }

    final private class TimeStampComparator<E extends BasicEvent> implements Comparator<E> {

        @Override
        public int compare(final E e1, final E e2) {
            return e1.timestamp - e2.timestamp;
        }
    }
    
    private TimeStampComparator timestampComparator=new TimeStampComparator<BasicEvent>();
    
    private final class EventSet<BasicEvent> extends TreeSet{

        public EventSet() {
            super(timestampComparator);
        }
        
        public EventSet(Collection c){
            this();
            addAll(c);
        }
    }
    
    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {

        totalEventCount = 0; // from super, to measure filtering
        filteredOutEventCount = 0;

        int TP = 0; // filter take real events as real events. the number of events
        int TN = 0; // filter take noise events as noise events
        int FP = 0; // filter take noise events as real events
        int FN = 0; // filter take real events as noise events

        if (in == null || in.isEmpty()) {
            log.warning("empty packet, cannot inject noise");
            return in;
        }
        BasicEvent firstE = in.getFirstEvent();
        if (resetCalled) {
            resetCalled = false;
            int ts = in.getLastTimestamp(); // we use getLastTimestamp because getFirstTimestamp contains event from BEFORE the rewind :-(
            // initialize filters with lastTimesMap to Poisson waiting times
            log.info("initializing timestamp maps with Poisson process waiting times");
            for (AbstractNoiseFilter f : noiseFilters) {
                int[][] map = f.getLastTimesMap();
                if (map != null) {
                    initializeLastTimesMapForNoiseRate(map, shotNoiseRateHz + leakNoiseRateHz, ts);
                }
            }

        }

        // copy input events to inList
        EventSet signalList=new EventSet();
//        HashSet signalList = new HashSet(in.getSize());
        for (BasicEvent e : in) {
            totalEventCount += 1;
            signalList.add(e);
        }
        
        System.out.printf("in size and signal size are %d and %d\n", in.getSize(), signalList.size());

        // add noise into signalList to get the outputPacketWithNoiseAdded, track noise in noiseList
        addNoise(in, signalAndNoisePacket, noiseList, shotNoiseRateHz, leakNoiseRateHz);

        // we need to copy the augmented event packet to a HashSet for use with Collections
//        HashSet signalAndNoiseList = new HashSet(signalAndNoisePacket.getSize());
        EventSet signalAndNoiseList = new EventSet();
        for (BasicEvent e : signalAndNoisePacket) {
            signalAndNoiseList.add(e);
        }

        // filter the augmented packet
        EventPacket<BasicEvent> passedSignalAndNoisePacket = getEnclosedFilterChain().filterPacket(signalAndNoisePacket);

        // make a copy of the output packet, which has noise filtered out by selected filter
//        HashSet passedSignalAndNoiseList = new HashSet(passedSignalAndNoisePacket.getSize());
        EventSet passedSignalAndNoiseList = new EventSet();
        for (BasicEvent e : passedSignalAndNoisePacket) {
            passedSignalAndNoiseList.add(e);
        }

        // now we sort out the mess
        // make a list of everything that was removed
//        Collection removedList = new HashSet(signalAndNoiseList); // start with S+N
        Collection removedList = new EventSet(signalAndNoiseList); // start with S+N
        removedList.removeAll(passedSignalAndNoiseList); // remove the filtered S+N, leaves everything that was filtered out

        // False negatives: Signal that was incorrectly removed by filter.
//        Collection fnList = new HashSet(signalList); // start with signal
        Collection fnList = new EventSet(signalList); // start with signal
        fnList.removeAll(passedSignalAndNoiseList);
        // Signal - (passed Signal (TP)) = FN
        // remove from signal the filtered output which removes all signal left 
        //over plus removes all noise (which is not there to start with).
        // What is left is signal that was removed by filtering, which are the false negatives
        FN = fnList.size();

        // True positives: Signal that was correctly retained by filtering
//        Collection tpList = new HashSet(signalList); // start with signal
        Collection tpList = new EventSet(signalList); // start with passed signal
        tpList.removeAll(fnList); // S = TP + FN, TP = S - FN
        TP = tpList.size();

        // False positives: Noise that is incorrectly passed by filter
//        Collection fpList = new HashSet(noiseList); // start with added noise
        Collection fpList = new EventSet(noiseList); // start with added noise
        fpList.retainAll(passedSignalAndNoiseList); // noise intersect with (passed S+N) which means noise are regarded as signal
        FP = fpList.size();

        // True negatives: Noise that was correctly removed by filter
//        Collection tnList = new HashSet(removedList); // start with all N
        Collection tnList = new EventSet(noiseList); // start with noiseList
        tnList.removeAll(passedSignalAndNoiseList); // N - (passed S + N) is N filtered out
        TN = tnList.size();

//        System.out.printf("every packet is: %d %d %d %d %d, %d %d %d: %d %d %d %d\n", inList.size(), newInList.size(), outList.size(), outRealList.size(), outNoiseList.size(), outInitList.size(), outInitRealList.size(), outInitNoiseList.size(), TP, TN, FP, FN);
        TPR = TP + FN == 0 ? 0f : (float) (TP * 1.0 / (TP + FN)); // percentage of true positive events. that's output real events out of all real events
        TPO = TP + FP == 0 ? 0f : (float) (TP * 1.0 / (TP + FP)); // percentage of real events in the filter's output

        TNR = TN + FP == 0 ? 0f : (float) (TN * 1.0 / (TN + FP));
        accuracy = (float) ((TP + TN) * 1.0 / (TP + TN + FP + FN));

        BR = TPR + TPO == 0 ? 0f : (float) (2 * TPR * TPO / (TPR + TPO)); // wish to norm to 1. if both TPR and TPO is 1. the value is 1
//        System.out.printf("shotNoiseRateHz and leakNoiseRateHz is %.2f and %.2f\n", shotNoiseRateHz, leakNoiseRateHz);

        float inSignalRateHz = 0, inNoiseRateHz = 0, outSignalRateHz = 0, outNoiseRateHz = 0;

        if (lastTimestampPreviousPacket != null) {
            int deltaTime = in.getLastTimestamp() - lastTimestampPreviousPacket;
            lastTimestampPreviousPacket = in.getLastTimestamp();
            inSignalRateHz = (1e-6f * in.getSize()) / deltaTime;
            inNoiseRateHz = (1e-6f * noiseList.size()) / deltaTime;
            outSignalRateHz = (1e-6f * tpList.size()) / deltaTime;
            outNoiseRateHz = (1e-6f * fpList.size()) / deltaTime;
        }
        if (csvWriter != null) {
            try {
                csvWriter.write(String.format("%d,%d,%d,%d,%f,%f,%f,%d,%f,%f,%f,%f\n",
                        TP, TN, FP, FN, TPR, TNR, BR, firstE.timestamp,
                        inSignalRateHz, inNoiseRateHz, outSignalRateHz, outNoiseRateHz));
            } catch (IOException e) {
                doCloseCsvFile();
            }
        }
        int outputEventCount = passedSignalAndNoiseList.size();
        filteredOutEventCount = totalEventCount - outputEventCount;

        return passedSignalAndNoisePacket;
    }

    @Override
    synchronized public void resetFilter() {
        lastTimestampPreviousPacket = null;
        resetCalled = true;
        getEnclosedFilterChain().reset();
    }

    @Override
    public void initFilter() {
        chain = new FilterChain(chip);

        noiseFilters = new AbstractNoiseFilter[]{new BackgroundActivityFilter(chip), new SpatioTemporalCorrelationFilter(chip), new SequenceBasedFilter(chip), new OrderNBackgroundActivityFilter((chip))};
        for (AbstractNoiseFilter n : noiseFilters) {
            n.initFilter();
            chain.add(n);
        }
        setEnclosedFilterChain(chain);
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
        npix = (chip.getSizeX() * chip.getSizeY());
        signalAndNoisePacket = new EventPacket<>(ApsDvsEvent.class);
        if (chip.getAeViewer() != null) {
            chip.getAeViewer().getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
            chip.getAeViewer().getSupport().addPropertyChangeListener(AEViewer.EVENT_CHIP, this);
        }
        setSelectedNoiseFilter(selectedNoiseFilterEnum);
    }

    /**
     * @return the shotNoiseRateHz
     */
    public float getShotNoiseRateHz() {
        return shotNoiseRateHz;
    }

    @Override
    public int[][] getLastTimesMap() {
        return null;
    }

    /**
     * @param shotNoiseRateHz the shotNoiseRateHz to set
     */
    public void setShotNoiseRateHz(float shotNoiseRateHz) {
        if (shotNoiseRateHz < 0) {
            shotNoiseRateHz = 0;
        }
        if (shotNoiseRateHz > RATE_LIMIT_HZ) {
            log.warning("high leak rates will hang the filter and consume all memory");
            shotNoiseRateHz = RATE_LIMIT_HZ;
        }

        putFloat("shotNoiseRateHz", shotNoiseRateHz);
        getSupport().firePropertyChange("shotNoiseRateHz", this.shotNoiseRateHz, shotNoiseRateHz);
        this.shotNoiseRateHz = shotNoiseRateHz;
    }

    /**
     * @return the leakNoiseRateHz
     */
    public float getLeakNoiseRateHz() {
        return leakNoiseRateHz;
    }

    /**
     * @param leakNoiseRateHz the leakNoiseRateHz to set
     */
    public void setLeakNoiseRateHz(float leakNoiseRateHz) {
        if (leakNoiseRateHz < 0) {
            leakNoiseRateHz = 0;
        }
        if (leakNoiseRateHz > RATE_LIMIT_HZ) {
            log.warning("high leak rates will hang the filter and consume all memory");
            leakNoiseRateHz = RATE_LIMIT_HZ;
        }

        putFloat("leakNoiseRateHz", leakNoiseRateHz);
        getSupport().firePropertyChange("leakNoiseRateHz", this.leakNoiseRateHz, leakNoiseRateHz);
        this.leakNoiseRateHz = leakNoiseRateHz;
    }

    /**
     * @return the csvFileName
     */
    public String getCsvFilename() {
        return csvFileName;
    }

    /**
     * @param csvFileName the csvFileName to set
     */
    public void setCsvFilename(String csvFileName) {
        if (csvFileName.toLowerCase().endsWith(".csv")) {
            csvFileName = csvFileName.substring(0, csvFileName.length() - 4);
        }
        
        putString("csvFileName", csvFileName);
        getSupport().firePropertyChange("csvFileName", this.csvFileName, csvFileName);
        this.csvFileName = csvFileName;
        openCvsFiile();
    }

    private void addNoise(EventPacket<? extends BasicEvent> in, EventPacket<? extends ApsDvsEvent> augmentedPacket, EventSet<BasicEvent> generatedNoise, float shotNoiseRateHz, float leakNoiseRateHz) {

        // we need at least 1 event to be able to inject noise before it
        if ((in.isEmpty())) {
            log.warning("no input events in this packet, cannot inject noise because there is no end event");
            return;
        }

        // save input packet
        augmentedPacket.clear();
        generatedNoise.clear();
        // make the itertor to save events with added noise events
        OutputEventIterator<ApsDvsEvent> outItr = (OutputEventIterator<ApsDvsEvent>) augmentedPacket.outputIterator();
        if (leakNoiseRateHz == 0 && shotNoiseRateHz == 0) {
            for (BasicEvent ie : in) {
                outItr.nextOutput().copyFrom(ie);
            }
            return; // no noise, just return which returns the copy from filterPacket
        }

        // the rate per pixel results in overall noise rate for entire sensor that is product of pixel rate and number of pixels.
        // we compute this overall noise rate to determine the Poisson sample interval that is much smaller than this to enable simple Poisson noise sampling.
        // Compute time step that is 10X less than the overall mean interval for noise
        // dt is the time interval such that if we sample a random value 0-1 every dt us, the the overall noise rate will be correct.
        float tmp = (float) (1.0 / ((leakNoiseRateHz + shotNoiseRateHz) * npix)); // this value is very small
        poissonDtUs = (int) ((tmp / 10) * 1000000); // 1s = 1000000 us
        if (poissonDtUs < 1) {
            poissonDtUs = 1;
        }
        float shotOffThresholdProb = 0.5f * (poissonDtUs * 1e-6f * npix) * shotNoiseRateHz; // bounds for samppling Poisson noise, factor 0.5 so total rate is shotNoiseRateHz
        float shotOnThresholdProb = 1 - shotOffThresholdProb; // for shot noise sample both sides, for leak events just generate ON events
        float leakOnThresholdProb = (poissonDtUs * 1e-6f * npix) * leakNoiseRateHz; // bounds for samppling Poisson noise

        int firstTsThisPacket = in.getFirstTimestamp();
        // insert noise between last event of last packet and first event of current packet
        // but only if there waa a previous packet and we are monotonic
        if (lastTimestampPreviousPacket != null) {
            if (firstTsThisPacket < lastTimestampPreviousPacket) {
                log.warning(String.format("non-monotonic timestamp: Resetting filter. (first event %d is smaller than previous event %d by %d)",
                        firstTsThisPacket, lastTimestampPreviousPacket, firstTsThisPacket - lastTimestampPreviousPacket));
                resetFilter();
                return;
            }
            // we had some previous event
            int lastPacketTs = lastTimestampPreviousPacket; // timestamp of the last event in the last packet
            for (int ts = lastPacketTs; ts < firstTsThisPacket; ts += poissonDtUs) {
                sampleNoiseEvent(ts, outItr, noiseList, shotOffThresholdProb, shotOnThresholdProb, leakOnThresholdProb);
            }
        }

        // insert noise between events of this packet after the first event, record their timestamp
        int preEts = 0;

        int lastEventTs = in.getFirstTimestamp();
        for (BasicEvent ie : in) {
            // if it is the first event or any with first event timestamp then just copy them
            if (ie.timestamp == firstTsThisPacket) {
                outItr.nextOutput().copyFrom(ie);
                continue;
            }
            // save the previous timestamp and get the next one, and then inject noise between them
            preEts = lastEventTs;
            lastEventTs = ie.timestamp;
            for (int ts = preEts; ts <= lastEventTs; ts += poissonDtUs) {  // TODO might be truncation error here with leftover time
                sampleNoiseEvent(ts, outItr, noiseList, shotOffThresholdProb, shotOnThresholdProb, leakOnThresholdProb);
            }
            outItr.nextOutput().copyFrom(ie);
        }
    }

    private void sampleNoiseEvent(int ts, OutputEventIterator<ApsDvsEvent> outItr, EventSet<BasicEvent> noiseList, float shotOffThresholdProb, float shotOnThresholdProb, float leakOnThresholdProb) {
        float randomnum = random.nextFloat();
        if (randomnum < shotOffThresholdProb) {
            injectOffEvent(ts, outItr, noiseList);
        } else if (randomnum > shotOnThresholdProb) {
            injectOnEvent(ts, outItr, noiseList);
        }
        if (random.nextFloat() < leakOnThresholdProb) {
            injectOnEvent(ts, outItr, noiseList);
        }
    }

    private void injectOnEvent(int ts, OutputEventIterator<ApsDvsEvent> outItr, EventSet<BasicEvent> noiseList) {
        ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
        e.setSpecial(false);
        e.polarity = PolarityEvent.Polarity.On;
        int x = (short) random.nextInt(sx);
        int y = (short) random.nextInt(sy);
        e.x = (short) (x);
        e.y = (short) (y);
        e.timestamp = ts;
        noiseList.add(e);
    }

    private void injectOffEvent(int ts, OutputEventIterator<ApsDvsEvent> outItr, EventSet<BasicEvent> noiseList) {
        ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
        e.setSpecial(false);
        e.polarity = PolarityEvent.Polarity.Off;

        int x = (short) random.nextInt(sx);
        int y = (short) random.nextInt(sy);
        e.x = (short) (x);
        e.y = (short) (y);
        e.timestamp = ts;
        noiseList.add(e);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt); //To change body of generated methods, choose Tools | Templates.
        if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            log.info(String.format("got rewound event %s, setting reset on next packet flat", evt));
            resetCalled = true;
        }
    }

    /**
     * @return the selectedNoiseFilter
     */
    public NoiseFilter getSelectedNoiseFilter() {
        return selectedNoiseFilterEnum;
    }

    /**
     * @param selectedNoiseFilter the selectedNoiseFilter to set
     */
    synchronized public void setSelectedNoiseFilter(NoiseFilter selectedNoiseFilter) {
        this.selectedNoiseFilterEnum = selectedNoiseFilter;
        putString("selectedNoiseFilter", selectedNoiseFilter.toString());
        for (AbstractNoiseFilter n : noiseFilters) {
            if (n.getClass().getSimpleName().equals(selectedNoiseFilter.toString())) {
                n.initFilter();
                n.setFilterEnabled(true);
                selectedFilter = n;
            } else {
                n.setFilterEnabled(false);
            }
        }
        resetCalled = true; // make sure we iniitialize the timestamp maps on next packet for new filter
    }

    private String USAGE = "Need at least 2 arguments: noisefilter <command> <args>\nCommands are: setNoiseFilterParameters <csvFilename> xx <shotNoiseRateHz> xx <leakNoiseRateHz> xx and specific to the filter\n";

    @Override
    public String processRemoteControlCommand(RemoteControlCommand command, String input) {
        // parse command and set parameters of NoiseTesterFilter, and pass command to specific filter for further processing
        // e.g. 
        // setNoiseFilterParameters csvFilename 10msBAFdot_500m_0m_300num0 shotNoiseRateHz 0.5 leakNoiseRateHz 0 dt 300 num 0
        String[] tok = input.split("\\s");
        if (tok.length < 2) {
            return USAGE;
        } else {
            for (int i = 1; i < tok.length; i++) {
                if (tok[i].equals("csvFileName")) {
                    setCsvFilename(tok[i + 1]);
                } else if (tok[i].equals("shotNoiseRateHz")) {
                    setShotNoiseRateHz(Float.parseFloat(tok[i + 1]));
                    log.info(String.format("setShotNoiseRateHz %f", shotNoiseRateHz));
                } else if (tok[i].equals("leakNoiseRateHz")) {
                    setLeakNoiseRateHz(Float.parseFloat(tok[i + 1]));
                    log.info(String.format("setLeakNoiseRateHz %f", leakNoiseRateHz));
                }
            }
            log.info("Received Command:" + input);
            String out = selectedFilter.setParameters(command, input);
            log.info("Execute Command:" + input);
            return out;
        }
    }

    /**
     * Fills lastTimesMap with waiting times drawn from Poisson process with
     * rate noiseRateHz
     *
     * @param lastTimesMap map
     * @param noiseRateHz rate in Hz
     * @param lastTimestampUs the last timestamp; waiting times are created
     * before this time
     */
    protected void initializeLastTimesMapForNoiseRate(int[][] lastTimesMap, float noiseRateHz, int lastTimestampUs) {
        for (final int[] arrayRow : lastTimesMap) {
            for (int i = 0; i < arrayRow.length; i++) {
                final float p = random.nextFloat();
                final double t = -noiseRateHz * Math.log(1 - p);
                final int tUs = (int) (1000000 * t);
                arrayRow[i] = lastTimestampUs - tUs;
            }
        }
    }

    @Override
    public float getCorrelationTimeS() {
        return this.correlationTimeS;
    }

    @Override
    public void setCorrelationTimeS(float dtS) {
        this.correlationTimeS = dtS;
        for (AbstractNoiseFilter f : noiseFilters) {
            f.setCorrelationTimeS(dtS);
        }
        putFloat("correlationTimeS", this.correlationTimeS);
    }

}
