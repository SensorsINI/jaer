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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2DVS128HardwareInterface;

/**
 * Filter for testing noise filters
 *
 * @author tobid/shasah
 */
@Description("Tests noise filters by injecting known noise and measuring how much signal and noise is filtered")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class NoiseTesterFilter extends AbstractNoiseFilter implements FrameAnnotater {

    FilterChain chain;
    private float shotNoiseRateHz = getFloat("shotNoiseRateHz", .1f);
    private float leakNoiseRateHz = getFloat("leakNoiseRateHz", .1f);

    protected static String DEFAULT_FILENAME = "BGFDot.csv";
    protected String csvFileName = getString("csvFileName", DEFAULT_FILENAME);

    // chip size values, set in initFilter()
    private int sx = 0;
    private int sy = 0;
    private int npix = 0;

    private int startEventTime = -1; // ts of the first event in this packet
    private int endEventTime = -1; // ts of the last event in this packet
    private int lastEventTime = -1; // ts of the last event in last packet
    private BasicEvent lastpacketE = null;
    private BasicEvent firstE = null;
    private BasicEvent lastE = null;
    private float TPR = 0;
    private float TPO = 0;
    private float TNR = 0;
    private float accuracy = 0;
    float BR = 0;
    private EventPacket<ApsDvsEvent> newIn;
    private Random random = new Random();

//    float BR = 2 * TPR * TPO / (TPR + TPO); // wish to norm to 1. if both TPR and TPO is 1. the value is 1
    public NoiseTesterFilter(AEChip chip) {
        super(chip);
        chain = new FilterChain(chip);
        chain.add(new BackgroundActivityFilter(chip));
        chain.add(new SpatioTemporalCorrelationFilter(chip));
        chain.add(new SequenceBasedFilter(chip));
        setEnclosedFilterChain(chain);
        setPropertyTooltip("shotNoiseRateHz", "rate per pixel of shot noise events");
        setPropertyTooltip("leakNoiseRateHz", "rate per pixel of leak noise events");
        setPropertyTooltip("csvFileName", "");
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
        getSupport().addPropertyChangeListener(AEViewer.EVENT_CHIP, this);

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

//        final float filteredOutPercent = 100 * (float) filteredOutEventCount / totalEventCount;
//        String s = null;
        String s = String.format("TPR=%%%6.1f, TNR=%%%6.1f, BR=%%%6.1f", 100 * TPR, 100 * TNR, 100 * BR);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, s);
        gl.glPopMatrix();
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        totalEventCount = 0;
        filteredOutEventCount = 0;

        startEventTime = in.getFirstTimestamp();
        endEventTime = in.getLastTimestamp();

        int TP = 0; // filter take real events as real events. the number of events
        int TN = 0; // filter take noise events as noise events
        int FP = 0; // filter take noise events as real events
        int FN = 0; // filter take real events as noise events

        ArrayList inList = new ArrayList<BasicEvent>(in.getSize());
        for (BasicEvent e : in) {
            if (totalEventCount == 0) {
                firstE = e;
            }
            totalEventCount += 1;
            inList.add(e);
            lastE = e;

        }

        // record the first timestamp and last timestamp of the packet
        // add noise into the packet in and get a new packet?
//        EventPacket<ApsDvsEvent> newIn = new EventPacket<ApsDvsEvent>();
        addNoise(in, newIn, shotNoiseRateHz, leakNoiseRateHz);
        ArrayList newInList = new ArrayList<BasicEvent>(newIn.getSize());
        for (BasicEvent e : newIn) {
            newInList.add(e);
        }

        EventPacket<BasicEvent> out = getEnclosedFilterChain().filterPacket(newIn);

        ArrayList outList = new ArrayList<BasicEvent>(out.getSize());
        for (BasicEvent e : out) {
            outList.add(e);
        }

        Collection realEvents = new ArrayList<BasicEvent>(inList);
        realEvents.removeAll(outList);
        FN = realEvents.size();
//        System.out.println("realEvents not in outList, i.e., filtered out: FN " + FN);

        Collection RealEvents = new ArrayList<BasicEvent>(inList);
        RealEvents.removeAll(realEvents); // real events subtraction FN, gets TP
        TP = RealEvents.size();
//        System.out.println("inList in outList: TP " + TP);

        Collection addedNoise = new ArrayList<BasicEvent>(newInList);
        addedNoise.removeAll(inList);
//        System.out.println("added noise：" + addedNoise.size());
        Collection outEvents = new ArrayList<BasicEvent>(outList);
        addedNoise.retainAll(outEvents); // noise intersection with out 
        FP = addedNoise.size();
//        System.out.println("noise in outList: FP " + FP);

        Collection addedNoise2 = new ArrayList<BasicEvent>(newInList);
        addedNoise2.removeAll(inList);
        // noise is FP + TN
//        System.out.println("added noise：" + addedNoise2.size());
        addedNoise2.removeAll(addedNoise); // noise subtraction FP, get TN
        TN = addedNoise2.size();
//        System.out.println("TN " + TN);

//        System.out.printf("every packet is: %d %d %d %d %d, %d %d %d: %d %d %d %d\n", inList.size(), newInList.size(), outList.size(), outRealList.size(), outNoiseList.size(), outInitList.size(), outInitRealList.size(), outInitNoiseList.size(), TP, TN, FP, FN);
        TPR = TP + FN == 0 ? 0 : (float) (TP * 1.0 / (TP + FN)); // percentage of true positive events. that's output real events out of all real events
        TPO = TP + FP == 0 ? 0 : (float) (TP * 1.0 / (TP + FP)); // percentage of real events in the filter's output

        TNR = TN + FP == 0 ? 0 : (float) (TN * 1.0 / (TN + FP));
        accuracy = (float) ((TP + TN) * 1.0 / (TP + TN + FP + FN));

        BR = TPR + TPO == 0 ? 0 : (float) (2 * TPR * TPO / (TPR + TPO)); // wish to norm to 1. if both TPR and TPO is 1. the value is 1

        File csv = new File("D:/jaerrecord/" + csvFileName); // CSV数据文件

        try {
            BufferedWriter bw;
            bw = new BufferedWriter(new FileWriter(csv, true));
            // 添加新的数据行
            bw.write(String.valueOf(TP) + ", " + String.valueOf(TN) + ", " + String.valueOf(FP) + ", " + String.valueOf(FN) + ", " + String.valueOf(TPR) + ", " + String.valueOf(TNR) + ", " + String.valueOf(BR));
            bw.newLine();
            bw.close();
        } catch (IOException ex) {
            Logger.getLogger(NoiseTesterFilter.class.getName()).log(Level.SEVERE, null, ex);
        }
// 附加

        System.out.printf("every packet is: inList: %d after add noise: %d filter's out: %d TP: %d TN: %d FP: %d FN: %d %%%3.1f %%%3.1f %%%3.1f\n", inList.size(), newInList.size(), outList.size(), TP, TN, FP, FN, 100 * TPR, 100 * TNR, 100 * BR);

        lastpacketE = lastE;

        return out;
    }

    @Override
    public void resetFilter() {
        getEnclosedFilterChain().reset();
    }

    @Override
    public void initFilter() {
        lastE = new BasicEvent();

        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
        npix = (chip.getSizeX() * chip.getSizeY());
        newIn = new EventPacket<>(ApsDvsEvent.class);

//        EventPacket<BasicEvent> newIn = new EventPacket<BasicEvent>();
    }

    /**
     * @return the shotNoiseRateHz
     */
    public float getShotNoiseRateHz() {
        return shotNoiseRateHz;
    }

    /**
     * @param shotNoiseRateHz the shotNoiseRateHz to set
     */
    public void setShotNoiseRateHz(float shotNoiseRateHz) {
        if (shotNoiseRateHz < 0) {
            shotNoiseRateHz = 0;
        }
        if (shotNoiseRateHz > 1) {
            log.warning("high leak rates will hang the filter and consume all memory");
            shotNoiseRateHz = 1;
        }
        this.shotNoiseRateHz = shotNoiseRateHz;
        putFloat("shotNoiseRateHz", shotNoiseRateHz);
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
        if (leakNoiseRateHz > 1) {
            log.warning("high leak rates will hang the filter and consume all memory");
            leakNoiseRateHz = 1;
        }
        this.leakNoiseRateHz = leakNoiseRateHz;
        putFloat("leakNoiseRateHz", leakNoiseRateHz);
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
        this.csvFileName = csvFileName;
        putString("csvFileName", csvFileName);
    }

    private EventPacket addNoise(EventPacket<? extends BasicEvent> in, EventPacket<? extends ApsDvsEvent> newIn, float shotNoiseRateHz, float leakNoiseRateHz) {

        if (shotNoiseRateHz == 0.0 && leakNoiseRateHz == 0) {
            return in;
        }

        newIn.clear();
        OutputEventIterator<ApsDvsEvent> outItr;
        outItr = (OutputEventIterator<ApsDvsEvent>) newIn.outputIterator();

        int count = 0;
        int lastPacketTs = 0; // timestamp of the last event in the last packet
        if (lastpacketE != null) {
            lastPacketTs = lastpacketE.timestamp;
        }

        int firstts = firstE.timestamp; // timestamp of the first event in the current packet
        int lastts = lastE.timestamp; // timestamp of the last event in the current packet
        int Min = 0;

        // the rate per pixel resuults in overall noise rate for entire sensor that is product of pixel rate and number of pixels.
        // we compute this overall noise rate to determine the Poisson sample interval that is much smaller than this to enable simple Poisson noise sampling.
        // Compute time step that is 10X less than the overall mean interval for noise
        // dt is the time interval such that if we sample a random value 0-1 every dt us, the the overall noise rate will be correct.
        float tmp = (float) (1.0 / ((leakNoiseRateHz + shotNoiseRateHz) * npix)); // this value is very small
        int dtUs = (int) ((tmp / 10) * 1000000); // 1s = 1000000 us
        log.info(String.format("time step dt for sampling noise is %d us", dtUs));

        float shotOffThresholdProb = (dtUs * 1e-6f * npix) * shotNoiseRateHz; // bounds for samppling Poisson noise
        float shotOnThresholdProb = 1 - shotOffThresholdProb; // for shot noise sample both sides, for leak events just generate ON events
        float leakOnThresholdProb = (dtUs * 1e-6f * npix) * leakNoiseRateHz; // bounds for samppling Poisson noise

        // insert noise between last event of last packet and first event of current packet
        for (int ts = lastPacketTs; ts < firstts; ts += dtUs) {
            sampleNoiseEvent(outItr, ts, shotOffThresholdProb, shotOnThresholdProb, leakOnThresholdProb);
        }

        // insert noise between two real events, record their timestamp
        count = 0;

        int preEts = 0;
        int curEts = 0;

        for (BasicEvent ie : in) {
            if (count == 0) {  // TODO what is this code doing?
                curEts = ie.timestamp;
                count += 1;
                outItr.nextOutput().copyFrom(ie);
                continue;
            }
            preEts = curEts;
            curEts = ie.timestamp;
            for (int ts = preEts; ts <= curEts; ts += dtUs) {  // TODO might be truncation error here with leftover time
                sampleNoiseEvent(outItr, ts, shotOffThresholdProb, shotOnThresholdProb, leakOnThresholdProb);
            }
            outItr.nextOutput().copyFrom(ie);
        }
        return newIn;
    }

    private void sampleNoiseEvent(OutputEventIterator<ApsDvsEvent> outItr, int ts, float shotOffThresholdProb, float shotOnThresholdProb, float leakOnThresholdProb) {
        // dt is in us
        float randomnum = random.nextFloat();
        if (randomnum < shotOffThresholdProb) {
            injectOffEvent(outItr, ts);
        } else if (randomnum > shotOnThresholdProb) {
            injectOnEvent(outItr, ts);
        }
        if (random.nextFloat() < leakOnThresholdProb) {
            injectOnEvent(outItr, ts);
        }
    }

    private void injectOnEvent(OutputEventIterator<ApsDvsEvent> outItr, int ts) {
        ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
        e.setSpecial(false);
        e.polarity = PolarityEvent.Polarity.On;
        int x = (short) random.nextInt(sx);
        int y = (short) random.nextInt(sy);
        e.x = (short) (x);
        e.y = (short) (y);
        e.timestamp = ts;
    }

    private void injectOffEvent(OutputEventIterator<ApsDvsEvent> outItr, int ts) {
        ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
        e.setSpecial(false);
        e.polarity = PolarityEvent.Polarity.Off;

        int x = (short) random.nextInt(sx);
        int y = (short) random.nextInt(sy);
        e.x = (short) (x);
        e.y = (short) (y);
        e.timestamp = ts;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt); //To change body of generated methods, choose Tools | Templates.
        if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            log.info(String.format("got rewound event %s, resetting filter", evt));
            resetFilter();
        } else if (evt.getPropertyName() == AEViewer.EVENT_CHIP) {
            log.info(String.format("AEChip changed event %s, resetting filter", evt));
            resetFilter();
        }

    }

}
