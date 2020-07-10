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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.FilterChain;

/**
 * Filter for testing noise filters
 *
 * @author tobid/shasah
 */
@Description("Tests noise filters by injecting known noise and measuring how much signal and noise is filtered")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class NoiseTesterFilter extends AbstractNoiseFilter {

    FilterChain chain;
    private float shotNoiseRateHz = getFloat("shotNoiseRateHz", .1f);
    private float leakNoiseRateHz = getFloat("leakNoiseRateHz", .1f);

    private int startEventTime = -1; // ts of the first event in this packet
    private int endEventTime = -1; // ts of the last event in this packet
    private int lastEventTime = -1; // ts of the last event in last packet

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
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        totalEventCount = 0;
        filteredOutEventCount = 0;
        
        startEventTime = in.getFirstTimestamp();
        endEventTime = in.getLastTimestamp();

        // record the first timestamp and last timestamp of the packet
        // add noise into the packet in and get a new packet?
        EventPacket<BasicEvent> newIn = addNoise(in, shotNoiseRateHz, leakNoiseRateHz);
        EventPacket<BasicEvent> out = getEnclosedFilterChain().filterPacket(newIn);

        int TP = 0; // filter take real events as real events. the number of events
        int TN = 0; // filter take noise events as noise events
        int FP = 0; // filter take noise events as real events
        int FN = 0; // filter take real events as noise events

        ArrayList inList = new ArrayList<BasicEvent>(in.getSize());
        ArrayList newInList = new ArrayList<BasicEvent>(newIn.getSize());
        ArrayList outList = new ArrayList<BasicEvent>(out.getSize());
        for (BasicEvent e : in) {
            inList.add(e);
        }
        for (BasicEvent e : newIn) {
            newInList.add(e);
        }
        for (BasicEvent e : out) {
            outList.add(e);
        }
        // compare out with newIn and in to get TP, TN, FP, FN. consider using set intersecion and union
        Set<BasicEvent>result = new HashSet<BasicEvent>((Collection<? extends BasicEvent>) outList);

//        java.lang.ClassCastException: net.sf.jaer.event.ApsDvsEventPacket cannot be cast to java.util.Collection
//	at net.sf.jaer.eventprocessing.filter.NoiseTesterFilter.filterPacket(NoiseTesterFilter.java:83)
        result.retainAll((Collection<?>) inList); // Intersection, 
        // in is the clean real events, so the intersection will result the collection of TP 
        TP = result.size();

        Set<BasicEvent> result2 = new HashSet<BasicEvent>((Collection<? extends BasicEvent>) inList);
        result2.removeAll(result);
        // subtraction, the intersection result is the TP, in is TP + FN
        // so in - result = #FN

        FN = result2.size();

        Set<BasicEvent> noise;
        noise = new HashSet<BasicEvent>((Collection<? extends BasicEvent>) newInList);
        noise.removeAll((Collection<?>) inList);
        // noise is TN + FP

        Set<BasicEvent> noise1 = new HashSet<BasicEvent>(noise);

        noise1.retainAll((Collection<?>) outList); // intersection
        // noise but occur in the filters output, this is False Positive FP
        FP = noise1.size();

        Set<BasicEvent> noise2 = new HashSet<BasicEvent>(noise);
        noise2.removeAll(noise1); // subtraction 
        // TN + FP - FP = TN.

        TN = noise2.size();

        float TPR = TP / (TP + FN);
        float precision = TP+FP==0? 0: TP / (TP + FP);

        float TNR = TN + FP==0? 0: TN / (TN + FP);
        float accuracy = (TP + TN) / (TP + TN + FP + FN);

        float balanceRelation = 2 * TPR * precision / (TPR + precision); // wish to norm to 1. if both TPR and precision is 1. the value is 1

//        in=getEnclosedFilterChain().filterPacket(in);
        return out;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
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
        newIn.appendCopy((EventPacket<BasicEvent>) in);
        
        ArrayList newInList = new ArrayList<BasicEvent>(newIn.getSize());
        
        int count = 0;
        for (BasicEvent e : in) {
            newInList.add(e);
            count += 1;
        }
        BasicEvent noiseE = (BasicEvent) newInList.get(count - 1);
        
        for(int i = 2; i < 10; i ++){
            noiseE.x = (short) (noiseE.x / i);
            newIn.appendCopy(noiseE);
        
            noiseE.y = (short) (noiseE.y / i);
            newIn.appendCopy(noiseE);
        }
        
        
        
        
        
        return newIn;
    }

}
