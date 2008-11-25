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

package net.sf.jaer.eventprocessing;

import net.sf.jaer.event.*;

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
    float thisNspe=0, nspeSum=0,nspeSq=0; // summary stats for ns per event
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
        thisNspe=size==0? 0: duration/size;
        nspeSum+=thisNspe;
        nspeSq+=thisNspe*thisNspe;
        nSamples++;
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
    
    public float stdErrSecPerEvent(){
        float avg=(float)nspeSum/nSamples;
//        float avg=(float)durationSum/nSamples;
        float std=(float)Math.sqrt( ((float)nspeSq-nSamples*avg*avg)/nSamples);
//        float std=(float)Math.sqrt( ((float)sumSquared-nSamples*avg*avg)/nSamples);
        return std*1e-9f;
    }
    
    public void resetStatistics(){
        nSamples = 0;
        thisNspe = 0;
        nspeSum = 0;
        nspeSq = 0; // summary stats for ns per event
    }
    
//    public float meanEps(){
//        float m=durationSum/n
//    }
    
    public String toString(){
        String s=String.format("%s: %8d events, %12d ns, %10.2g eps, %10.4g+%10.2g ns/event, ", 
                filterClassName,
                size, 
                duration,
                eps(),
                1e9f*sPerEvent(),
                1e9f*stdErrSecPerEvent()
                );
        return s;
    }
    
}
