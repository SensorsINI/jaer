package ch.unizh.ini.robothead;
//package ch.unizh.ini.caviar.robothead;
/*
 * CorrelatorFilter.java
 *
 * Created on 28. November 2007, 14:13
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */


import ch.unizh.ini.robothead.Bins;
import ch.unizh.ini.robothead.Angle;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import com.sun.org.apache.xpath.internal.operations.Mod;
import java.util.*;


/**
 *
 * @author jaeckeld
 */
public class CalibrationFilter extends EventFilter2D implements Observer{
   
    private int shiftSize=getPrefs().getInt("CorrelatorFilter.shiftSize",800);
    private int binSize=getPrefs().getInt("CorrelatorFilter.binSize",40);
    private int numberOfPairs=getPrefs().getInt("CorrelatorFilter.numberOfPairs",1000);
    private int usedChannel=getPrefs().getInt("CorrelatorFilter.usedChannel",16);
    
    
    /** Creates a new instance of CorrelatorFilter */
    public CalibrationFilter(AEChip chip) {
        super(chip);
        //initFilter();
        //resetFilter();
        setPropertyTooltip("shiftSize", "maximum shift size for autocorrelation");
        setPropertyTooltip("binSize", "size for one Bin");
        setPropertyTooltip("numberOfPairs", "how many left/right pairs used");
        setPropertyTooltip("usedChannel", "choose channel");
        
    
    }
    //Angle myAngle;    
    
    Bins myBins = new Bins();
    int[][] lastTs = new int[2][32]; 
    double ITD;
    
        
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        //if(!isFilterEnabled()){
            //System.out.print("TEST 2");
            return in;       // only use if filter enabled
        //}
       /**checkOutputPacketEventType(in);
            
       for(Object e:in){
        
            BasicEvent i =(BasicEvent)e;
       
            //System.out.println(i.timestamp+" "+i.x+" "+i.y);
            //  try{
            
            OutputEventIterator outItr=out.outputIterator();
            
            
            if (i.x==this.usedChannel){
                
                int diff=i.timestamp-lastTs[1-i.y][i.x];    // compare actual ts with last complementary ts of that channel
                if (i.y==0)  diff=-diff;                    // to distingiuish plus- and minus-delay
            
                //System.out.println(diff);
            
                if (java.lang.Math.abs(diff)<shiftSize){
                    myBins.addToBin(diff);
                }
                lastTs[i.y][i.x]=i.timestamp;
                //}catch(ArrayIndexOutOfBoundsException eeob){
                    //allocateMaps(chip);
                //}
                BasicEvent o=(BasicEvent)outItr.nextOutput();   //provide filtered Output!!
                o.copyFrom(i);
            }*/
        //}
        
        //myBins.dispBins();
        
        //ITD=myBins.getITD();
        
        //System.out.println(ITD);
        
        
        
        //return out;
        
    }
    public Object getFilterState() {
        return null;
    }
    public void resetFilter(){
        System.out.println("reset!");
        
        myBins.genBins(shiftSize,binSize,numberOfPairs);  
        System.out.println(this.getBinSize());
        System.out.println(this.getShiftSize());
        int[][] lastTs = new int[2][32]; 
        
    }
    public void initFilter(){
        System.out.println("init!");
        myBins.genBins(shiftSize,binSize,numberOfPairs);  
        
    }
    
    public void update(Observable o, Object arg){
        initFilter();
    }
    
    
    public int getShiftSize(){
        return this.shiftSize;   
    }
    public void setShiftSize(int shiftSize){
        getPrefs().putInt("CorrelatorFilter.shiftSize",shiftSize);
        support.firePropertyChange("shiftSize",this.shiftSize,shiftSize);
        this.shiftSize=shiftSize;
        myBins.genBins(shiftSize,binSize,numberOfPairs);  
        
    }
    public int getUsedChannel(){
        return this.usedChannel;   
    }
    public void setUsedChannel(int usedChannel){
        getPrefs().putInt("CorrelatorFilter.usedChannel",usedChannel);
        support.firePropertyChange("usedChannel",this.usedChannel,usedChannel);
        this.usedChannel=usedChannel;
        myBins.genBins(shiftSize,binSize,numberOfPairs);  
        
    }
    public int getBinSize(){
        return this.binSize;   
    }
    public void setBinSize(int binSize){
        getPrefs().putInt("CorrelatorFilter.binSize",binSize);
        support.firePropertyChange("binSize",this.binSize,binSize);
        this.binSize=binSize;
        myBins.genBins(shiftSize,binSize,numberOfPairs);  
        
    }
    public int getNumberOfPairs(){
        return this.numberOfPairs;
    }
    public void setNumberOfPairs(int numberOfPairs){
        getPrefs().putInt("CorrelatorFilter.numberOfPairs",numberOfPairs);
        support.firePropertyChange("numberOfPairs",this.numberOfPairs,numberOfPairs);
        this.numberOfPairs=numberOfPairs;
        myBins.genBins(shiftSize,binSize,numberOfPairs);  
        
    }
    
}
    
