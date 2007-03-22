/*
 * EventProcessingPerformanceMeter.java
 *
 * Created on August 20, 2006, 10:14 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright August 20, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.eventprocessing;

import ch.unizh.ini.caviar.event.*;

/**
 * Measures cost of event processing.
 
 * @author tobi
 */
public class EventProcessingPerformanceMeter {
    
    EventPacket packet;
    long startTime, endTime;
    int size=1;
    long durationSum, sumSquared;
    long duration=1;
    int nSamples=0;
    EventFilter filter;
    String filterClassName;
    
    /** Creates a new instance of EventProcessingPerformanceMeter */
    public EventProcessingPerformanceMeter(EventFilter f) {
        this.filter=f;
        filterClassName=f.getClass().getSimpleName();
    }
    
    public void start(EventPacket packet){
        this.packet=packet;
        size=packet.getSize();
        startTime=System.nanoTime();
    }
    
    public void start(int nEvents){
        size=nEvents;
        startTime=System.nanoTime();
    }
    
    public void stop(){
        endTime=System.nanoTime();
        duration=endTime-startTime;
        durationSum+=duration;
        nSamples++;
        sumSquared+= duration*duration;
    }
    
    public void stop(int nEvents){
        this.size=nEvents;
        stop();
    }
    
    public float eps(){
        return size/(1e-9f*duration);
    }
    
    public float sPerEvent(){
        float eps=eps();
        if(eps==0) return 0; else return 1/eps;
    }
    
//    public float meanEps(){
//        float m=durationSum/n
//    }
    
    public String toString(){
        String s=String.format("%s: %8d events, %12d ns, %8.2g eps, %8.2g  +- %8.2g ns/event, ", 
                filterClassName,
                size, 
                duration,
                eps(),
                1e9f*sPerEvent(),
                0f
                );
        return s;
    }
    
}
