/*
 * ChannelFilter.java
 *
 * Created on 29. Januar 2008, 10:26
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.robothead;


import java.util.Observable;


import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
/**
 *
 * @author jaeckeld
 */

public class ChannelFilter extends EventFilter2D {
   
    private int chMin=getPrefs().getInt("ChannelFilter.chMin",1);
    private int chMax=getPrefs().getInt("ChannelFilter.chMax",32);
    
    
    /** Creates a new instance of ChannelFilter */
    public ChannelFilter(AEChip chip) {
        super(chip);
        //initFilter();
        //resetFilter();
        setPropertyTooltip("chMin", "highest Channel");
        setPropertyTooltip("chMax", "lowest Channel");
        
    
    }
        
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()){
            //System.out.print("TEST 2");
            return in;       // only use if filter enabled
        }
       checkOutputPacketEventType(in);
           
       OutputEventIterator outItr=out.outputIterator();
             
       for(Object e:in){
        
            BasicEvent i =(BasicEvent)e;
       
            //System.out.println(i.timestamp+" "+i.x+" "+i.y);
            
            if (i.x>=this.chMin-1 && i.x<=this.chMax-1){
                
                BasicEvent o=(BasicEvent)outItr.nextOutput();   //provide filtered Output!!
                o.copyFrom(i);
            }
        
       }     
        return out;
        
    }
   
    public Object getFilterState() {
        return null;
    }
    public void resetFilter(){
        
        log.info("channel max="+this.getChMax()+" channel min="+this.getChMin());
        
    }
    public void initFilter(){
        System.out.println("init!");
        
        
    }
    
    public int getChMin(){
        return this.chMin;   
    }
    public void setChMin(int chMin){
        getPrefs().putInt("ChannelFilter.chMin",chMin);
        getSupport().firePropertyChange("chMin",this.chMin,chMin);
        this.chMin=chMin;
        
    }

    public int getChMax(){
        return this.chMax;   
    }
    public void setChMax(int chMax){
        getPrefs().putInt("ChannelFilter.chMax",chMax);
        getSupport().firePropertyChange("chMax",this.chMax,chMax);
        this.chMax=chMax;
        
    }
}
    
