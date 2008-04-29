/*
 * RetinaCochleaFilter.java
 *
 * Created on 29. Januar 2008, 10:26
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.robothead.retinacochlea;


import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.*;
import ch.unizh.ini.caviar.eventprocessing.filter.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import java.util.*;
import java.io.*;


/**
 * 
 * Extracts only cochlea Events from CochleaRetina Events and converts them to usual Cochlea Events (y value -64)
 *
 * @author jaeckeld
 */

public class CochleaExtractorFilter extends EventFilter2D{
   
    
    /**
     * Creates a new instance of RetinaCochleaFilter
     */
    public CochleaExtractorFilter(AEChip chip) {
        
        super(chip);
        initFilter();
        
    }
    
    public EventPacket retinaEvents;
        
    public EventPacket<?> filterPacket(EventPacket<?> in) {
       
        if(in==null) return null;
        if(!filterEnabled) return in;
        
        OutputEventIterator outItr=out.outputIterator();
        
        retinaEvents=new EventPacket();                 // new eventPacket to store retinaEvents
        OutputEventIterator retinaItr=retinaEvents.outputIterator();
        
        for(Object e:in){
        
             BasicEvent i =(BasicEvent)e;
             //System.out.println(i.timestamp+" "+i.x+" "+i.y);
            
             if (i.y>63){       // in that case it is a CochleaEvent !!
                
                 BasicEvent o=(BasicEvent)outItr.nextOutput();   // put all CochleaEvents into output
                 
                 o.copyFrom(i);
                 o.setY((short)(i.getY()-64));      // now they are lixe normal cochleaEvents
             }
//             else{
//                 BasicEvent o=(BasicEvent)retinaItr.nextOutput();   // Put Retina Events into EventPacket retinaEvents
//                 o.copyFrom(i);
//             }
        }
        
        return out;
        
    }
  
    
    public void resetFilter(){
//        System.out.println("reset!");
        
        
    }
    public void initFilter(){
        System.out.println("init!");
        
    }
    
    public void update(Observable o, Object arg){
        initFilter();
    }
    public Object getFilterState() {
        return null;
    }
    

}
    
