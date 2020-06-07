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

import net.sf.jaer.event.EventPacket;

/**
 * Measures cost of event processing.
 
 * @author tobi
 */
public class EventProcessingPerformanceMeter {
    private static final float SPNS = 1e-9f;
    public static final float NSPS = 1e9f;
    
    private EventPacket packet;
    private long startTimeNs, endTimeNs;
    private int size=1;
    private long durationSum, sumSquared;
    private long durationNs=1;
    private int nSamples=0;
    private float thisNspe=0, nspeSum=0,nspeSq=0; // summary stats for ns per event
    private EventFilter filter;
    private String filterClassName;
    private EventProcessingPerformanceView view=null;
 
    /** Creates a new instance of EventProcessingPerformanceMeter */
    public EventProcessingPerformanceMeter(EventFilter f, EventProcessingPerformanceView view) {
        this.filter=f;
        this.view=view;
        filterClassName=f.getClass().getSimpleName();
    }
    
    public void start(EventPacket packet){
        this.packet=packet;
        size=packet.getSize();
        startTimeNs=System.nanoTime();
    }
    
    public void start(int nEvents){
        size=nEvents;
        startTimeNs=System.nanoTime();
    }
    
    public void stop(){
        endTimeNs=System.nanoTime();
        durationNs=endTimeNs-startTimeNs;
        thisNspe=size==0? 0: durationNs/size;
        nspeSum+=thisNspe;
        nspeSq+=thisNspe*thisNspe;
        nSamples++;
    }
    
    public void stop(int nEvents){
        this.size=nEvents;
        stop();
    }
    
    public float eps(){
        return size/(SPNS*durationNs);
    }
    
    public float sPerEvent(){
        float eps=eps();
        if(eps==0) return 0; else return 1/eps;
    }
    
    public float avgSPerEvent(){
        return (float)nspeSum*SPNS/nSamples;
    }
    
    public float stdErrSecPerEvent(){
        if(nSamples<2) return 0;
        float avg=avgSPerEvent();
//        float avg=(float)durationSum/nSamples;
        float std=(float)Math.sqrt( ((float)nspeSq-((float)nspeSum*nspeSum)/nSamples)/(nSamples-1));
//        float std=(float)Math.sqrt( ((float)sumSquared-nSamples*avg*avg)/nSamples);
        return std*SPNS;
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
        String s=String.format("%s: %9d events, duration %8.3f ms, %8.2g eps, %8.1f ns/event (Average %8.1f +/- %-6.1f ns/event, N=%d samples), ", getFilterClassName(),
                size,
                durationNs*1e-6f,
                eps(),
                NSPS*sPerEvent(),
                NSPS*avgSPerEvent(),
                NSPS*stdErrSecPerEvent(),
                nSamples
                );
        return s;
    }

    void updateView() {
        getView().updateView(this.toString());
    }

    /**
     * @return the view
     */
    public EventProcessingPerformanceView getView() {
        return view;
    }

    /**
     * @param view the view to set
     */
    public void setView(EventProcessingPerformanceView view) {
        this.view = view;
    }

    /**
     * @return the filter
     */
    public EventFilter getFilter() {
        return filter;
    }

    /**
     * @return the filterClassName
     */
    public String getFilterClassName() {
        return filterClassName;
    }
    
}
