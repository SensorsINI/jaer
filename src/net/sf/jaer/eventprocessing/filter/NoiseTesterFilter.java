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
import java.util.Comparator;
import java.util.Random;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.DavisRenderer;

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
    private int poissonDtUs = 1;
    float shotOffThresholdProb; // bounds for samppling Poisson noise, factor 0.5 so total rate is shotNoiseRateHz
    float shotOnThresholdProb; // for shot noise sample both sides, for leak events just generate ON events
    float leakOnThresholdProb; // bounds for samppling Poisson noise

    private static String DEFAULT_CSV_FILENAME_BASE = "NoiseTesterFilter";
    private String csvFileName = getString("csvFileName", DEFAULT_CSV_FILENAME_BASE);
    private File csvFile = null;
    private BufferedWriter csvWriter = null;

    // chip size values, set in initFilter()
    private int sx = 0;
    private int sy = 0;

    private Integer lastTimestampPreviousPacket = null; // use Integer Object so it can be null to signify no value yet
    private float TPR = 0;
    private float TPO = 0;
    private float TNR = 0;
    private float accuracy = 0;
    private float BR = 0;
    float inSignalRateHz = 0, inNoiseRateHz = 0, outSignalRateHz = 0, outNoiseRateHz = 0;

//    private EventPacket<ApsDvsEvent> signalAndNoisePacket = null;
    private EventPacket<BasicEvent> signalAndNoisePacket = null;
//    private EventList<BasicEvent> noiseList = new EventList();
    private Random random = new Random();
    private AbstractNoiseFilter[] noiseFilters = null;
    private AbstractNoiseFilter selectedFilter = null;
    protected boolean resetCalled = true; // flag to reset on next event
    public static final float RATE_LIMIT_HZ = 10;
    private float annotateAlpha = getFloat("annotateAlpha", 0.5f);
    private AEChipRenderer renderer;

    public enum NoiseFilterEnum {
        BackgroundActivityFilter, SpatioTemporalCorrelationFilter, SequenceBasedFilter, OrderNBackgroundActivityFilter
    }
    private NoiseFilterEnum selectedNoiseFilterEnum = NoiseFilterEnum.valueOf(getString("selectedNoiseFilter", NoiseFilterEnum.BackgroundActivityFilter.toString())); //default is BAF
    private float correlationTimeS = getFloat("correlationTimeS", 20e-3f);

//    float BR = 2 * TPR * TPO / (TPR + TPO); // wish to norm to 1. if both TPR and TPO is 1. the value is 1
    public NoiseTesterFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("shotNoiseRateHz", "rate per pixel of shot noise events");
        setPropertyTooltip("leakNoiseRateHz", "rate per pixel of leak noise events");
        setPropertyTooltip("csvFileName", "Enter a filename base here to open CSV output file (appending to it if it already exists)");
        setPropertyTooltip("selectedNoiseFilterEnum", "Choose a noise filter to test");
        setPropertyTooltip("annotation", "annotateAlpha", "Sets the transparency for the annotated pixels. Only works for Davis renderer.");
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
        String s = String.format("TPR=%%%6.1f, TNR=%%%6.1f, TPO=%%%6.1f, BR=%%%6.1f, poissonDt=%d us", 100 * TPR, 100 * TNR, 100 * TPO, 100 * BR, poissonDtUs);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, s);
        gl.glRasterPos3f(0, statisticsDrawingPosition+10, 0);
        String s2 = String.format("Input sigRate=%s noiseRate=%s, Output sigRate=%s noiseRate=%s", eng.format(inSignalRateHz), eng.format(inNoiseRateHz), eng.format(outSignalRateHz), eng.format(outNoiseRateHz));
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, s2);
        gl.glPopMatrix();
    }

    private void annotateNoiseFilteringEvents(ArrayList<BasicEvent> outSig, ArrayList<BasicEvent> outNoise) {
        final float[] noiseColor = {1, 0, 0}, sigColor = {0, 1, 0};
        for (BasicEvent e : outSig) {
            renderer.setAnnotateColorRGB(e.x, e.y, sigColor);
        }
        for (BasicEvent e : outNoise) {
            renderer.setAnnotateColorRGB(e.x, e.y, noiseColor);
        }
    }

//    final private class TimeStampComparator<E extends BasicEvent> implements Comparator<E> {
//
//        // NOTE this hack so that sorted event EventSet does not lose elements with identical timestamps
//        @Override
//        public int compare(final E e1, final E e2) {
//            int diff = e1.timestamp - e2.timestamp;
//
//            return diff;
//        }
//    }
//    private TimeStampComparator timestampComparator = new TimeStampComparator<BasicEvent>();
    private ArrayList<BasicEvent> createEventList() {
        return new ArrayList<BasicEvent>();
    }

    private class BackwardsTimestampException extends Exception {

        public BackwardsTimestampException(String string) {
            super(string);
        }

    }

    private ArrayList<BasicEvent> createEventList(EventPacket<BasicEvent> p) throws BackwardsTimestampException {
        ArrayList<BasicEvent> l = new ArrayList(p.getSize());
        BasicEvent pe = null;
        for (BasicEvent e : p) {
            if (pe != null && (e.timestamp < pe.timestamp || pe.equals(e))) {
                throw new BackwardsTimestampException(String.format("timestamp %d is earlier than previous %d", e.timestamp, pe.timestamp));
            }
            l.add(e);
            pe = e;
        }
        return l;
    }

    private ArrayList<BasicEvent> createEventList(List<BasicEvent> p) throws BackwardsTimestampException {
        ArrayList<BasicEvent> l = new ArrayList(p.size());
        BasicEvent pe = null;
        for (BasicEvent e : p) {
            if (pe != null && (e.timestamp < pe.timestamp || pe.equals(e))) {
                throw new BackwardsTimestampException(String.format("timestamp %d is earlier than previous %d", e.timestamp, pe.timestamp));
            }
            l.add(e);
            pe = e;
        }
        return l;
    }

    /**
     * Counts the number of events in a that are in b. Assumes packets are
     * non-monotonic in timestamp ordering. But after a rewind, the packet can
     * contain events from end and start of a recording. To prevent this, the
     * EventList constructor throws BackwardsTimestampException.
     *
     *
     * @param a
     * @param b
     */
    private int countIntersect(ArrayList<BasicEvent> a, ArrayList<BasicEvent> b) {
//        ArrayList<BasicEvent> intersect = new ArrayList();
        int count = 0;
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }

        // TODO test case
//        a = new ArrayList();
//        b = new ArrayList();
//        a.add(new BasicEvent(4, (short) 0, (short) 0));
//        a.add(new BasicEvent(4, (short) 0, (short) 0));
//        a.add(new BasicEvent(4, (short) 1, (short) 0));
//        a.add(new BasicEvent(4, (short) 2, (short) 0));
////        a.add(new BasicEvent(2, (short) 0, (short) 0));
////        a.add(new BasicEvent(10, (short) 0, (short) 0));
//
//        b.add(new BasicEvent(2, (short) 0, (short) 0));
//        b.add(new BasicEvent(2, (short) 0, (short) 0));
//        b.add(new BasicEvent(4, (short) 0, (short) 0));
//        b.add(new BasicEvent(4, (short) 0, (short) 0));
//        b.add(new BasicEvent(4, (short) 1, (short) 0));
//        b.add(new BasicEvent(10, (short) 0, (short) 0));
        int i = 0, j = 0;
        int na = a.size(), nb = b.size();
        while (i < na && j < nb) {
            if (a.get(i).timestamp < b.get(j).timestamp) {
                i++;
            } else if (b.get(j).timestamp < a.get(i).timestamp) {
                j++;
            } else {
                // If timestamps equal, it mmight be idential events or maybe not
                // and there might be several events with idential timestamps.
                // We MUST match all a with all b.
                // We don't want to increment both pointers or we can miss matches.
                // We do an inner double loop for exhaustive matching as long as the timestamps
                // are identical. 
                int i1 = i, j1 = j;
                while (i1 < na && j1 < nb && a.get(i1).timestamp == b.get(j1).timestamp) {
                    boolean match = false;
                    while (j1 < nb && i1 < na && a.get(i1).timestamp == b.get(j1).timestamp) {
                        if (a.get(i1).equals(b.get(j1))) {
                            count++;
//                            intersect.add(b.get(j1)); // TODO debug
                            // we have a match, so use up the a element
                            i1++;
                            match = true;
                        }
                        j1++;
                    }
                    if (!match) {
                        i1++; // 
                    }
                    j1 = j; // reset j to start of matching ts region
                }
                i = i1; // when done, timestamps are different or we reached end of either or both arrays
                j = j1;
            }
        }
//        System.out.println("%%%%%%%%%%%%%%");
//        printarr(a, "a");
//        printarr(b, "b");
//        printarr(intersect, "intsct");
        return count;
    }

    // TODO test case
    void printarr(ArrayList<BasicEvent> a, String n) {
        final int MAX = 30;
        if (a.size() > MAX) {
            System.out.printf("--------\n%s[%d]>%d\n", n, a.size(), MAX);
            return;
        }
        System.out.printf("%s[%d] --------\n", n, a.size());
        for (int i = 0; i < a.size(); i++) {
            BasicEvent e = a.get(i);
            System.out.printf("%s[%d]=[%d %d %d %d]\n", n, i, e.timestamp, e.x, e.y, (e instanceof PolarityEvent) ? ((PolarityEvent) e).getPolaritySignum() : 0);
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
        ArrayList<BasicEvent> signalList;
        try {
            signalList = createEventList((EventPacket<BasicEvent>) in);
        } catch (BackwardsTimestampException ex) {
            log.warning(String.format("%s: skipping nonmonotonic packet [%s]", ex, in));
            return in;
        }

        assert signalList.size() == in.getSizeNotFilteredOut() : String.format("signalList size (%d) != in.getSizeNotFilteredOut() (%d)", signalList.size(), in.getSizeNotFilteredOut());

        // add noise into signalList to get the outputPacketWithNoiseAdded, track noise in noiseList
        ArrayList<BasicEvent> noiseList = createEventList(); //List.create(tim);
        addNoise(in, signalAndNoisePacket, noiseList, shotNoiseRateHz, leakNoiseRateHz);

        // we need to copy the augmented event packet to a HashSet for use with Collections
        ArrayList<BasicEvent> signalAndNoiseList;
        try {
            signalAndNoiseList = createEventList((EventPacket<BasicEvent>) signalAndNoisePacket);

            // filter the augmented packet
            EventPacket<BasicEvent> passedSignalAndNoisePacket = (EventPacket<BasicEvent>) selectedFilter.filterPacket(signalAndNoisePacket);

            ArrayList<BasicEvent> filteredOutList = selectedFilter.getFilteredOutEvents();

            // make a list of the output packet, which has noise filtered out by selected filter
            ArrayList<BasicEvent> passedSignalAndNoiseList = createEventList(passedSignalAndNoisePacket);

            assert (signalList.size() + noiseList.size() == signalAndNoiseList.size());

            // now we sort out the mess
            TP = countIntersect(signalList, passedSignalAndNoiseList);   // True positives: Signal that was correctly retained by filtering
            FP = countIntersect(noiseList, passedSignalAndNoiseList);    // False positives: Noise that is incorrectly passed by filter
            TN = countIntersect(noiseList, filteredOutList);             // True negatives: Noise that was correctly removed by filter
            FN = countIntersect(signalList, filteredOutList);            // False negatives: Signal that was incorrectly removed by filter.

//            if (TN + FP != noiseList.size()) {
//                System.err.println(String.format("TN (%d) + FP (%d) = %d != noiseList (%d)", TN, FP, TN + FP, noiseList.size()));
//                printarr(signalList, "signalList");
//                printarr(noiseList, "noiseList");
//                printarr(passedSignalAndNoiseList, "passedSignalAndNoiseList");
//                printarr(signalAndNoiseList, "signalAndNoiseList");
//            }
            assert (TN + FP == noiseList.size()) : String.format("TN (%d) + FP (%d) = %d != noiseList (%d)", TN, FP, TN + FP, noiseList.size());
            totalEventCount = signalAndNoiseList.size();
            int outputEventCount = passedSignalAndNoiseList.size();
            filteredOutEventCount = totalEventCount - outputEventCount;

//            if (TP + FP != outputEventCount) {
//                System.err.printf("@@@@@@@@@ TP (%d) + FP (%d) = %d != outputEventCount (%d)", TP, FP, TP + FP, outputEventCount);
//                printarr(signalList, "signalList");
//                printarr(noiseList, "noiseList");
//                printarr(passedSignalAndNoiseList, "passedSignalAndNoiseList");
//                printarr(signalAndNoiseList, "signalAndNoiseList");
//            }
            assert TP + FP == outputEventCount : String.format("TP (%d) + FP (%d) = %d != outputEventCount (%d)", TP, FP, TP + FP, outputEventCount);
//            if (TP + TN + FP + FN != totalEventCount) {
//                System.err.printf("***************** TP (%d) + TN (%d) + FP (%d) + FN (%d) = %d != totalEventCount (%d)", TP, TN, FP, FN, TP + TN + FP + FN, totalEventCount);
//                printarr(signalList, "signalList");
//                printarr(noiseList, "noiseList");
//                printarr(signalAndNoiseList, "signalAndNoiseList");
//                printarr(passedSignalAndNoiseList, "passedSignalAndNoiseList");
//            }
            assert TP + TN + FP + FN == totalEventCount : String.format("TP (%d) + TN (%d) + FP (%d) + FN (%d) = %d != totalEventCount (%d)", TP, TN, FP, FN, TP + TN + FP + FN, totalEventCount);
            assert TN + FN == filteredOutEventCount : String.format("TN (%d) + FN (%d) = %d  != filteredOutEventCount (%d)", TN, FN, TN + FN, filteredOutEventCount);

//        System.out.printf("every packet is: %d %d %d %d %d, %d %d %d: %d %d %d %d\n", inList.size(), newInList.size(), outList.size(), outRealList.size(), outNoiseList.size(), outInitList.size(), outInitRealList.size(), outInitNoiseList.size(), TP, TN, FP, FN);
            TPR = TP + FN == 0 ? 0f : (float) (TP * 1.0 / (TP + FN)); // percentage of true positive events. that's output real events out of all real events
            TPO = TP + FP == 0 ? 0f : (float) (TP * 1.0 / (TP + FP)); // percentage of real events in the filter's output

            TNR = TN + FP == 0 ? 0f : (float) (TN * 1.0 / (TN + FP));
            accuracy = (float) ((TP + TN) * 1.0 / (TP + TN + FP + FN));

            BR = TPR + TPO == 0 ? 0f : (float) (2 * TPR * TPO / (TPR + TPO)); // wish to norm to 1. if both TPR and TPO is 1. the value is 1
//        System.out.printf("shotNoiseRateHz and leakNoiseRateHz is %.2f and %.2f\n", shotNoiseRateHz, leakNoiseRateHz);

            if (lastTimestampPreviousPacket != null) {
                int deltaTime = in.getLastTimestamp() - lastTimestampPreviousPacket;
                inSignalRateHz = (1e6f * in.getSize()) / deltaTime;
                inNoiseRateHz = (1e6f * noiseList.size()) / deltaTime;
                outSignalRateHz = (1e6f * TP) / deltaTime;
                outNoiseRateHz = (1e6f * FP) / deltaTime;
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

            annotateNoiseFilteringEvents(signalList, noiseList);

            lastTimestampPreviousPacket = in.getLastTimestamp();
            return passedSignalAndNoisePacket;
        } catch (BackwardsTimestampException ex) {
            Logger.getLogger(NoiseTesterFilter.class.getName()).log(Level.SEVERE, null, ex);
            return in;
        }
    }

    @Override
    synchronized public void resetFilter() {
        lastTimestampPreviousPacket = null;
        resetCalled = true;
        getEnclosedFilterChain().reset();
    }

    private void computeProbs() {
        // the rate per pixel results in overall noise rate for entire sensor that is product of pixel rate and number of pixels.
        // we compute this overall noise rate to determine the Poisson sample interval that is much smaller than this to enable simple Poisson noise sampling.
        // Compute time step that is 10X less than the overall mean interval for noise
        // dt is the time interval such that if we sample a random value 0-1 every dt us, the the overall noise rate will be correct.
        int npix = (chip.getSizeX() * chip.getSizeY());
        float tmp = (float) (1.0 / ((leakNoiseRateHz + shotNoiseRateHz) * npix)); // this value is very small
        poissonDtUs = (int) ((tmp / 10) * 1000000); // 1s = 1000000 us
        if (poissonDtUs < 1) {
            log.warning(String.format("Poisson sampling rate is less than 1us which is timestep resolution, generated noise rate will too low"));
            poissonDtUs = 1;
        }
        shotOffThresholdProb = 0.5f * (poissonDtUs * 1e-6f * npix) * shotNoiseRateHz; // bounds for samppling Poisson noise, factor 0.5 so total rate is shotNoiseRateHz
        shotOnThresholdProb = 1 - shotOffThresholdProb; // for shot noise sample both sides, for leak events just generate ON events
        leakOnThresholdProb = (poissonDtUs * 1e-6f * npix) * leakNoiseRateHz; // bounds for samppling Poisson noise
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
        signalAndNoisePacket = new EventPacket<>(ApsDvsEvent.class);
        if (chip.getAeViewer() != null) {
            chip.getAeViewer().getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
            chip.getAeViewer().getSupport().addPropertyChangeListener(AEViewer.EVENT_CHIP, this);
        }
        setSelectedNoiseFilterEnum(selectedNoiseFilterEnum);
        computeProbs();
        renderer = (AEChipRenderer) chip.getRenderer();
        if (renderer != null) { // might be null on startup, if initFilter is called from AEChip constructor
            renderer.setExternalRenderer(false);
        }
        setAnnotateAlpha(annotateAlpha);
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
    synchronized public void setShotNoiseRateHz(float shotNoiseRateHz) {
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
        computeProbs();
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
    synchronized public void setLeakNoiseRateHz(float leakNoiseRateHz) {
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
        computeProbs();
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

    private void addNoise(EventPacket<? extends BasicEvent> in, EventPacket<? extends BasicEvent> augmentedPacket, List<BasicEvent> generatedNoise, float shotNoiseRateHz, float leakNoiseRateHz) {

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
            int lastPacketTs = lastTimestampPreviousPacket + 1; // 1us more than timestamp of the last event in the last packet
            for (int ts = lastPacketTs; ts < firstTsThisPacket; ts += poissonDtUs) {
                ts = sampleNoiseEvent(ts, outItr, generatedNoise, shotOffThresholdProb, shotOnThresholdProb, leakOnThresholdProb); // note noise injection updates ts to make sure monotonic
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
            preEts = lastEventTs + 1;
            lastEventTs = ie.timestamp;
            for (int ts = preEts; ts < lastEventTs; ts += poissonDtUs) {  // TODO might be truncation error here with leftover time
                ts = sampleNoiseEvent(ts, outItr, generatedNoise, shotOffThresholdProb, shotOnThresholdProb, leakOnThresholdProb);
            }
            outItr.nextOutput().copyFrom(ie);
        }
    }

    private int sampleNoiseEvent(int ts, OutputEventIterator<ApsDvsEvent> outItr, List<BasicEvent> noiseList, float shotOffThresholdProb, float shotOnThresholdProb, float leakOnThresholdProb) {
        float randomnum = random.nextFloat();
        if (randomnum < shotOffThresholdProb) {
            injectOffEvent(ts++, outItr, noiseList);
        } else if (randomnum > shotOnThresholdProb) {
            injectOnEvent(ts++, outItr, noiseList);
        }
        if (random.nextFloat() < leakOnThresholdProb) {
            injectOnEvent(ts++, outItr, noiseList);
        }
        return ts;  // we make sure ts increment and return the new one, which updates loop param
    }

    private void injectOnEvent(int ts, OutputEventIterator<ApsDvsEvent> outItr, List<BasicEvent> noiseList) {
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

    private void injectOffEvent(int ts, OutputEventIterator<ApsDvsEvent> outItr, List<BasicEvent> noiseList) {
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
    public NoiseFilterEnum getSelectedNoiseFilterEnum() {
        return selectedNoiseFilterEnum;
    }

    /**
     * @param selectedNoiseFilter the selectedNoiseFilter to set
     */
    synchronized public void setSelectedNoiseFilterEnum(NoiseFilterEnum selectedNoiseFilter) {
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
        if (dtS > 1e-6f * MAX_DT) {
            dtS = 1e-6f * MAX_DT;
        } else if (dtS < 1e-6f * MIN_DT) {
            dtS = 1e-6f * MIN_DT;
        }

        this.correlationTimeS = dtS;
        for (AbstractNoiseFilter f : noiseFilters) {
            f.setCorrelationTimeS(dtS);
        }
        putFloat("correlationTimeS", this.correlationTimeS);
    }

    /**
     * @return the annotateAlpha
     */
    public float getAnnotateAlpha() {
        return annotateAlpha;
    }

    /**
     * @param annotateAlpha the annotateAlpha to set
     */
    public void setAnnotateAlpha(float annotateAlpha) {
        if (annotateAlpha > 1.0) {
            annotateAlpha = 1.0f;
        }
        if (annotateAlpha < 0.0) {
            annotateAlpha = 0.0f;
        }
        this.annotateAlpha = annotateAlpha;
        if (renderer != null && renderer instanceof DavisRenderer) {
            DavisRenderer frameRenderer = (DavisRenderer) renderer;
            frameRenderer.setAnnotateAlpha(annotateAlpha);
            renderer.setExternalRenderer(annotateAlpha > 0.1);
        }
    }
}
