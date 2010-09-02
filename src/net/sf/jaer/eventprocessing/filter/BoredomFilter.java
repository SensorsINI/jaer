/*
 * RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.eventprocessing.filter;

import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.util.*;

/**
 * An AE filter that filters out high firing-rate repetitive events.
 *It does this by maintaining an internal map of boring cells (x,y,type). These are boring because they occur very often.
 *This filter is superceded by {@link RepetitiousFilter}, which works much better for streetlamps, etc.
 *@see RepetitiousFilter
 *
 * @author tobi
 */
public class BoredomFilter extends EventFilter2D implements Observer {
   public static String getDescription(){
       return "Filters out events with low relative variance in activity";
   }
    
    public int threshold=4;
    public int forgetInterval=30;
    int[][][] boringMap;
    short[] tmpx, tmpy;
    byte[] tmptype;
    int[] tmptimestamps;
    
    public BoredomFilter(AEChip chip){
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();
        setFilterEnabled(false);
    }
    
    public BoredomFilter(AEChip chip, EventFilter2D in){
        super(chip);
        allocateBoringMap(chip);
        setEnclosedFilter(in);
        setFilterEnabled(false);
        resetFilter();
    }
    
    
    private void allocateBoringMap(final AEChip chip) {
        boringMap=new int[chip.getSizeX()+2][chip.getSizeY()+2][chip.getNumCellTypes()];
    }
    
    
    int maxEvents=0;
    int index=0;
    private short x,y;
    private byte type;
    private int ts,repMeasure,i;
    private int forgetCounter=0;
    
    
    void allocateNewBuffers(int n){
        tmpx=new short[n];
        tmpy=new short[n];
        tmptype=new byte[n];
        tmptimestamps=new int[n];
    }
    
    
    synchronized public void resetFilter() {
        allocateBoringMap(chip);
    }
    
    
    
    public int getForgetInterval() {
        return this.forgetInterval;
    }
    
    public void setForgetInterval(final int forgetInterval) {
        this.forgetInterval = forgetInterval;
    }
    
    public int getThreshold() {
        return this.threshold;
    }
    
    public void setThreshold(final int threshold) {
        this.threshold = threshold;
    }
    
    public void initFilter() {
        allocateBoringMap(chip);
    }
    
    public void update(Observable o, Object arg) {
        if(!isFilterEnabled()) return;
        initFilter();
    }
    
    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *@param in input events can be null or empty.
     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
     */
    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        // forget
        
        forgetCounter++;
        if(forgetCounter%forgetInterval==0){
            // every forgetInterval packets, we divide the counts in boringMap by 2
            //boringMap=new int[chip.getSizeX()+2][chip.getSizeY()+2][chip.getNumCellTypes()];
            int nt=getChip().getNumCellTypes(),nx=getChip().getSizeX(),ny=getChip().getSizeY();
            
            for(byte t=0;t<nt;t++)
                for(short x=0;x<nx;x++)
                    for(short y=0;y<ny;y++)
                        boringMap[x][y][t]>>>=1;
        }
        
        // filter
        
        int n=in.getSize();
        if(n==0) return in;
        
        if(n>maxEvents){
            allocateNewBuffers(n);
        }
        
        // for each event only write it to the tmp buffers if it isn't boring
        OutputEventIterator outItr=out.outputIterator();
        for(Object ein:in){
            TypedEvent e=(TypedEvent)ein;
            repMeasure=boringMap[e.x][e.y][e.type];
            if(repMeasure<threshold){
                TypedEvent eout=(TypedEvent)outItr.nextOutput();
                eout.copyFrom(e);
            }
            // for each event stuff inc the corresponding boringMap array
            boringMap[x][y][type]++;
        }
        return out;
    }
    
}
