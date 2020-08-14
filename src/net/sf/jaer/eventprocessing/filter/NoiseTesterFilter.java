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
import net.sf.jaer.eventprocessing.FilterChain;
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

    private int sx;
    private int sy;

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
    }

    @Override
    public void initFilter() {
        lastE = new BasicEvent();

        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;

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
        Random random = new Random();

        float tmp = (float) (1.0 / (shotNoiseRateHz * (sx + 1) * (sy + 1))); // this value is very small
        int dt = (int) ((float) (tmp / 10) * 1000000); // 1s = 1000000 us
        System.out.printf("dt %d\n", dt);

        float downbound = shotNoiseRateHz;
        float upbound = 1 - downbound;
        
        if (shotNoiseRateHz == 0.0){
            for (BasicEvent ie : in){
                outItr.nextOutput().copyFrom(ie);
            }
            return newIn;
            
        }

//        insert noise between last event of last packet and first event of current packet
        for (int ts = lastPacketTs; ts < firstts; ts += dt) {
            float randomnum;
            randomnum = random.nextFloat();
            if (randomnum < downbound) {
                ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
                e.setSpecial(false);
                e.polarity = PolarityEvent.Polarity.Off;

                int x = (short) random.nextInt(sx);
                int y = (short) random.nextInt(sy);
                e.x = (short) (x);
                e.y = (short) (y);
                e.timestamp = ts;
            } else if (randomnum > upbound) {
                ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
                e.setSpecial(false);
                e.polarity = PolarityEvent.Polarity.On;
                int x = (short) random.nextInt(sx);
                int y = (short) random.nextInt(sy);
                e.x = (short) (x);
                e.y = (short) (y);
                e.timestamp = ts;
            } else {

            }
        }

        // insert noise between two real events, record their timestamp
        count = 0;

        int preEts = 0;
        int curEts = 0;

        for (BasicEvent ie : in) {

            if (count == 0) {

                curEts = ie.timestamp;
                count += 1;
                outItr.nextOutput().copyFrom(ie);
//                BasicEvent ce = (BasicEvent) outItr.nextOutput();
//                ce.copyFrom(ie);
                continue;
            }
            preEts = curEts;
            curEts = ie.timestamp;

            for (int ts = preEts; ts <= curEts; ts += dt) {
                float randomnum;
                randomnum = random.nextFloat();
                if (randomnum < downbound) {
                    ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
                    e.copyFrom(ie);
                    int x = (short) random.nextInt(sx);
                    int y = (short) random.nextInt(sy);
                    e.x = (short) (x);
                    e.y = (short) (y);
                    e.timestamp = ts;
                } else if (randomnum > upbound) {
                    ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
                    e.copyFrom(ie);
                    int x = (short) random.nextInt(sx);
                    int y = (short) random.nextInt(sy);
                    e.x = (short) (x);
                    e.y = (short) (y);
                    e.timestamp = ts;
                } else {

                }

            }
//            BasicEvent ce = (BasicEvent) outItr.nextOutput();
//            ce.copyFrom(ie);
            outItr.nextOutput().copyFrom(ie);

        }

//        for (int ts = lastPacketTs; ts <= lastts; ts += 100) {
//
//            int x = (short) random.nextInt(sx);
//            int y = (short) random.nextInt(sy);
//
//            BasicEvent noiseE = new PolarityEvent();
//
//            noiseE.timestamp = ts;
//            newIn.appendCopy(noiseE);
//
//        }
        return newIn;
    }

}
