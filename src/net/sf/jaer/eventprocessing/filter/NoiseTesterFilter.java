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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
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

    private int sx;
    private int sy;

    private int startEventTime = -1; // ts of the first event in this packet
    private int endEventTime = -1; // ts of the last event in this packet
    private int lastEventTime = -1; // ts of the last event in last packet
    private BasicEvent lastpacketE = null;
    private BasicEvent firstE = null;
    private BasicEvent lastE = null;
    private float TPR = 0;
    private float precision = 0;
    private float TNR = 0;
    private float accuracy = 0;
    float balanceRelation = 0;
//    float balanceRelation = 2 * TPR * precision / (TPR + precision); // wish to norm to 1. if both TPR and precision is 1. the value is 1

    public NoiseTesterFilter(AEChip chip) {
        super(chip);
        chain = new FilterChain(chip);
        chain.add(new BackgroundActivityFilter(chip));
        chain.add(new SequenceBasedFilter(chip));
        setEnclosedFilterChain(chain);
        setPropertyTooltip("shotNoiseRateHz", "rate per pixel of shot noise events");
        setPropertyTooltip("leakNoiseRateHz", "rate per pixel of leak noise events");
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
        String s = String.format("TPR=%%%6.1f, TNR=%%%6.1f, BR=%%%6.1f", 100 * TPR, 100 * TNR, 100 * balanceRelation);
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
        EventPacket<BasicEvent> newIn = addNoise(in, shotNoiseRateHz, leakNoiseRateHz);
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
        TPR = TP + FN == 0 ? 0 : (float) (TP * 1.0 / (TP + FN));
        precision = TP + FP == 0 ? 0 : (float) (TP * 1.0 / (TP + FP));

        TNR = TN + FP == 0 ? 0 : (float) (TN * 1.0 / (TN + FP));
        accuracy = (float) ((TP + TN) * 1.0 / (TP + TN + FP + FN));

        balanceRelation = TPR + precision == 0 ? 0 : (float) (2 * TPR * precision / (TPR + precision)); // wish to norm to 1. if both TPR and precision is 1. the value is 1

        System.out.printf("every packet is: inList: %d after add noise: %d filter's out: %d TP: %d TN: %d FP: %d FN: %d %%%3.1f %%%3.1f %%%3.1f\n", inList.size(), newInList.size(), outList.size(), TP, TN, FP, FN, 100 * TPR, 100 * TNR, 100 * balanceRelation);

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

    private EventPacket addNoise(EventPacket<? extends BasicEvent> in, float shotNoiseRateHz, float leakNoiseRateHz) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.

        EventPacket<BasicEvent> newIn = new EventPacket<BasicEvent>();
        OutputEventIterator outItr = newIn.outputIterator();

        int count = 0;
        int lastPacketTs = 0; // timestamp of the last event in the last packet
        if (lastpacketE != null) {
            lastPacketTs = lastpacketE.timestamp;
        }

        int firstts = firstE.timestamp; // timestamp of the first event in the current packet
        int lastts = lastE.timestamp; // timestamp of the last event in the current packet
        int Min = 0;
        Random random = new Random();

//        here is a bug
//        float tmp = (float) (1.0 / (shotNoiseRateHz * (sx + 1) * (sy + 1))); // this equation yields dt too small
//        float tmp = (float) ((shotNoiseRateHz * (sx + 1) * (sy + 1))); // this equation yields dt too large
//        int dt = (int) (tmp / 10);
        int dt = 10;
//        float downbound = dt * shotNoiseRateHz / 10;
        float downbound = shotNoiseRateHz;
        float upbound = 1 - downbound;

//        java.lang.ClassCastException: net.sf.jaer.event.BasicEvent cannot be cast to net.sf.jaer.event.PolarityEvent, so I change the PolarityEvent to BasicEvent
//        insert noise between last event of last packet and first event of current packet
        for (int ts = lastPacketTs; ts < firstts; ts += dt) {
            float randomnum;
            randomnum = random.nextFloat();
            if (randomnum < downbound) {
                BasicEvent e = (BasicEvent) outItr.nextOutput();
//                e.setSpecial(false);
//                e.polarity = PolarityEvent.Polarity.Off;
                int x = (short) random.nextInt(sx);
                int y = (short) random.nextInt(sy);
                e.x = (short) (x);
                e.y = (short) (y);
                e.timestamp = ts;
            } else if (randomnum > upbound) {
                BasicEvent e = (BasicEvent) outItr.nextOutput();
//                e.setSpecial(false);
//                e.polarity = PolarityEvent.Polarity.On;
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
                BasicEvent ce = (BasicEvent) outItr.nextOutput();
                ce.copyFrom(ie);
                continue;
            }
            preEts = curEts;
            curEts = ie.timestamp;

            for (int ts = preEts; ts <= curEts; ts += dt) {
                float randomnum;
                randomnum = random.nextFloat();
                if (randomnum < downbound) {
                    BasicEvent e = (BasicEvent) outItr.nextOutput();
                    int x = (short) random.nextInt(sx);
                    int y = (short) random.nextInt(sy);
                    e.x = (short) (x);
                    e.y = (short) (y);
                    e.timestamp = ts;
                } else if (randomnum > upbound) {
                    BasicEvent e = (BasicEvent) outItr.nextOutput();
                    int x = (short) random.nextInt(sx);
                    int y = (short) random.nextInt(sy);
                    e.x = (short) (x);
                    e.y = (short) (y);
                    e.timestamp = ts;
                } else {

                }

            }
            BasicEvent ce = (BasicEvent) outItr.nextOutput();
            ce.copyFrom(ie);

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
