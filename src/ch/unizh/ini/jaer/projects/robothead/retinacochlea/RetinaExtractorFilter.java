/*
 * RetinaCochleaFilter.java
 *
 * Created on 29. Januar 2008, 10:26
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.robothead.retinacochlea;


import java.util.Observable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;


/**
 * 
 * Filters only Retina Events from CochleaRetina Events
 *
 * @author jaeckeld
 */

public class RetinaExtractorFilter extends EventFilter2D{
   
    
    /**
     * Creates a new instance of RetinaCochleaFilter
     */
    public RetinaExtractorFilter(AEChip chip) {
        
        super(chip);
        initFilter();
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
       
        if(in==null) return null;
        if(!filterEnabled) return in;     // not necessary in FilterChain..
        
        OutputEventIterator outItr=out.outputIterator();
        
        
        for(Object e:in){
        
             BasicEvent i =(BasicEvent)e;
             //System.out.println(i.timestamp+" "+i.x+" "+i.y);
            
             if (i.y<=63){       // in that case it is a RetinaEvents !!
                
                 BasicEvent o=(BasicEvent)outItr.nextOutput();   // put all RetinaEvents into output
                 o.copyFrom(i);
             }
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
    
